/*
 * (c) Copyright 2023 Andrej Chomutovskij. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.achomutovskij.deviceservice;

import com.achomutovskij.deviceservice.booking.api.DeviceBookingServiceEndpoints;
import com.achomutovskij.deviceservice.database.DatabaseManager;
import com.achomutovskij.deviceservice.gsm.GsmArenaDataProvider;
import com.achomutovskij.deviceservice.gsm.RapidApiClient;
import com.achomutovskij.deviceservice.info.api.DeviceInfoServiceEndpoints;
import com.achomutovskij.deviceservice.management.api.DeviceManagementServiceEndpoints;
import com.achomutovskij.deviceservice.resources.DeviceBookingResource;
import com.achomutovskij.deviceservice.resources.DeviceInfoResource;
import com.achomutovskij.deviceservice.resources.DeviceManagementResource;
import com.google.common.base.Strings;
import com.palantir.conjure.java.api.config.ssl.SslConfiguration;
import com.palantir.conjure.java.api.errors.ErrorType;
import com.palantir.conjure.java.api.errors.ServiceException;
import com.palantir.conjure.java.config.ssl.SslSocketFactories;
import com.palantir.conjure.java.undertow.runtime.ConjureHandler;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import io.undertow.Handlers;
import io.undertow.Undertow;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Optional;
import javax.net.ssl.SSLContext;
import okhttp3.OkHttpClient;

public final class DeviceServiceApplication {

    private static final SafeLogger log = SafeLoggerFactory.get(DeviceServiceApplication.class);

    private static final String KEY_STORE_PATH = "var/certs/keystore.jks";
    private static final String TRUSTSTORE_PATH = "var/certs/truststore.jks";
    private static final String KEYSTORE_PASSWORD = "changeit";

    public static final SslConfiguration SSL_CONFIG =
            SslConfiguration.of(Paths.get(TRUSTSTORE_PATH), Paths.get(KEY_STORE_PATH), KEYSTORE_PASSWORD);

    public static final String SQLITE_URL = "jdbc:sqlite:var/db/database.db";
    private static final String DEVICE_INFO_CSV = "var/gsmarena_data/gsmarena_dataset.csv";

    private DeviceServiceApplication() {}

    public static void main(String[] _args) {
        Configuration conf;
        try {
            conf = ConfigurationLoader.load();
        } catch (IOException e) {
            throw new ServiceException(ErrorType.INTERNAL, e, SafeArg.of("reason", "Failed to load the config"));
        }

        startServer(conf);
    }

    @SuppressWarnings("ShutdownHook") // cannot find another way of attaching a shutdown hook other than via runtime
    public static Undertow startServer(Configuration conf) {
        DatabaseManager databaseManager = new DatabaseManager(SQLITE_URL, conf.getFirstStartupRegisterDevices());

        GsmArenaDataProvider gsmArenaDataProvider = new GsmArenaDataProvider(DEVICE_INFO_CSV);

        SSLContext sslContext = SslSocketFactories.createSslContext(SSL_CONFIG);

        Optional<RapidApiClient> rapidApiClientOptional = conf.getApiKey()
                .filter(apiKey -> !Strings.isNullOrEmpty(apiKey))
                .map(apiKey -> new RapidApiClient(new OkHttpClient(), apiKey));

        if (rapidApiClientOptional.isEmpty()) {
            log.warn("No API key is provided, will only use the CSV lookup.");
        }

        Undertow server = Undertow.builder()
                .addHttpsListener(conf.getPort(), conf.getHost(), sslContext)
                .addHttpListener(conf.getPort() + 1, conf.getHost())
                .setHandler(Handlers.path()
                        .addPrefixPath(
                                "api/",
                                ConjureHandler.builder()
                                        .services(DeviceManagementServiceEndpoints.of(
                                                new DeviceManagementResource(databaseManager)))
                                        .services(DeviceInfoServiceEndpoints.of(new DeviceInfoResource(
                                                databaseManager, rapidApiClientOptional, gsmArenaDataProvider)))
                                        .services(DeviceBookingServiceEndpoints.of(
                                                new DeviceBookingResource(databaseManager)))
                                        .build()))
                .build();

        Runtime.getRuntime().addShutdownHook(new Thread(databaseManager::stop));

        server.start();

        return server;
    }
}

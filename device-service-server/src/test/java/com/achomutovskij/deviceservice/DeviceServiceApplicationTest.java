/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
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

import static org.assertj.core.api.Assertions.assertThat;

import com.achomutovskij.deviceservice.api.BookingErrors;
import com.achomutovskij.deviceservice.api.BookingRequest;
import com.achomutovskij.deviceservice.api.DeviceErrors;
import com.achomutovskij.deviceservice.api.DeviceInfo;
import com.achomutovskij.deviceservice.booking.api.DeviceBookingService;
import com.achomutovskij.deviceservice.info.api.DeviceInfoService;
import com.achomutovskij.deviceservice.management.api.DeviceManagementService;
import com.google.common.collect.ImmutableList;
import com.palantir.conjure.java.api.config.service.UserAgent;
import com.palantir.conjure.java.api.testing.Assertions;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.client.config.ClientConfigurations;
import com.palantir.conjure.java.client.jaxrs.JaxRsClient;
import com.palantir.conjure.java.okhttp.NoOpHostEventsSink;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.undertow.Undertow;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class DeviceServiceApplicationTest {

    private static DeviceManagementService deviceManagementService;
    private static DeviceInfoService deviceInfoService;
    private static DeviceBookingService deviceBookingService;

    private static Undertow server;

    @BeforeAll
    public static void before()
            throws CertificateException, IOException, KeyStoreException, NoSuchAlgorithmException,
                    KeyManagementException {

        server = DeviceServiceApplication.startServer(Configuration.builder()
                .port(8345)
                .host("0.0.0.0")
                .firstStartupRegisterDevices(Collections.emptyList())
                .apiKey(Optional.empty()) // don't contact the external API in these tests
                .build());

        File crtFile = new File("src/test/resources/certs/ca-cert");
        Certificate certificate =
                CertificateFactory.getInstance("X.509").generateCertificate(new FileInputStream(crtFile));
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        keyStore.setCertificateEntry("server", certificate);

        TrustManagerFactory trustManagerFactory =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(keyStore);

        TrustManager[] trustManager = trustManagerFactory.getTrustManagers();
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustManager, null);

        ClientConfiguration clientConfig = ClientConfigurations.of(
                ImmutableList.of("https://localhost:8345/api/"), sslContext.getSocketFactory(), (X509TrustManager)
                        trustManager[0]);

        deviceManagementService = JaxRsClient.create(
                DeviceManagementService.class,
                UserAgent.of(UserAgent.Agent.of("test", "0.0.0")),
                NoOpHostEventsSink.INSTANCE,
                clientConfig);

        deviceInfoService = JaxRsClient.create(
                DeviceInfoService.class,
                UserAgent.of(UserAgent.Agent.of("test", "0.0.0")),
                NoOpHostEventsSink.INSTANCE,
                clientConfig);

        deviceBookingService = JaxRsClient.create(
                DeviceBookingService.class,
                UserAgent.of(UserAgent.Agent.of("test", "0.0.0")),
                NoOpHostEventsSink.INSTANCE,
                clientConfig);

        deviceManagementService.deleteAllDevices();
    }

    @AfterAll
    public static void afterAll() throws SQLException {
        if (server != null) {
            server.stop();
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(DeviceServiceApplication.SQLITE_URL);

        try (HikariDataSource dataSource = new HikariDataSource(config);
                Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {

            statement.executeUpdate("DROP TABLE devices;");
            System.out.println("Dropped the table");
        }
    }

    @AfterEach
    void afterEach() {
        deviceManagementService.deleteAllDevices();
    }

    @Test
    public void getDeviceUsingInvalidName() {
        Assertions.assertThatRemoteExceptionThrownBy(() -> deviceInfoService.getDevicesByName("doesNotExist"))
                .isGeneratedFromErrorType(DeviceErrors.DEVICE_NAME_NOT_FOUND);
    }

    @Test
    public void createDevice() {
        deviceManagementService.createDevice("Nokia");

        List<DeviceInfo> devicesWithGivenNameAfterRegistration = deviceInfoService.getDevicesByName("Nokia");
        assertThat(devicesWithGivenNameAfterRegistration).hasSize(1);
        DeviceInfo deviceInfo = devicesWithGivenNameAfterRegistration.get(0);
        assertThat(deviceInfo.getAvailable()).isTrue();
        assertThat(deviceInfo.getName()).isEqualTo("Nokia");
        assertThat(deviceInfo.getTechnology()).contains("INFO UNAVAILABLE");
        assertThat(deviceInfo.getTwoGBands()).contains("INFO UNAVAILABLE");
        assertThat(deviceInfo.getThreeGBands()).contains("INFO UNAVAILABLE");
        assertThat(deviceInfo.getFourGBands()).contains("INFO UNAVAILABLE");

        deviceManagementService.deleteDevice(deviceInfo.getId());

        Assertions.assertThatRemoteExceptionThrownBy(() -> deviceInfoService.getDevicesByName("Nokia"))
                .isGeneratedFromErrorType(DeviceErrors.DEVICE_NAME_NOT_FOUND);
    }

    @Test
    public void createMultipleDevicesWithTheSameName() {
        deviceManagementService.createDevice("Nokia");
        deviceManagementService.createDevice("Nokia");

        assertThat(deviceInfoService.getDevicesByName("Nokia")).hasSize(2);
        assertThat(deviceInfoService.getAllDevices()).hasSize(2);
        assertThat(deviceInfoService.getAllAvailableDevices()).hasSize(2);
    }

    @Test
    public void verifyGsmArenaEnrichment() {
        String deviceName = "Samsung Galaxy S9";
        deviceManagementService.createDevice(deviceName);

        List<DeviceInfo> devicesWithGivenNameAfterRegistration = deviceInfoService.getDevicesByName(deviceName);
        assertThat(devicesWithGivenNameAfterRegistration).hasSize(1);
        DeviceInfo deviceInfo = devicesWithGivenNameAfterRegistration.get(0);

        assertThat(deviceInfo.getTechnology()).contains("GSM / CDMA / HSPA / EVDO / LTE");
        assertThat(deviceInfo.getTwoGBands())
                .contains("GSM 850 / 900 / 1800 / 1900 - SIM 1 & SIM 2 (dual-SIM model only)");
        assertThat(deviceInfo.getThreeGBands()).contains("HSDPA 850 / 900 / 1700(AWS) / 1900 / 2100 - Global, USA");
        assertThat(deviceInfo.getFourGBands())
                .contains("LTE band 1(2100), 2(1900), 3(1800), 4(1700/2100), 5(850), 7(2600), 8(900), 12(700), 13(700),"
                        + " 17(700), 18(800), 19(800), 20(800), 25(1900), 26(850), 28(700), 32(1500), 38(2600),"
                        + " 39(1900), 40(2300), 41(2500), 66(1700/2100) - Global");
    }

    @Test
    public void bookByName() {
        deviceManagementService.createDevice("iPhone 14");
        deviceBookingService.bookDevice(BookingRequest.of("Andrej", "iPhone 14", OptionalInt.empty()));
        assertThat(deviceInfoService.getAllAvailableDevices()).isEmpty();
        assertThat(deviceInfoService.getAllDevices()).hasSize(1);

        Assertions.assertThatRemoteExceptionThrownBy(() ->
                        deviceBookingService.bookDevice(BookingRequest.of("Peter", "iPhone 14", OptionalInt.empty())))
                .isGeneratedFromErrorType(BookingErrors.DEVICE_NOT_AVAILABLE);
    }

    @Test
    public void bookById() {
        deviceManagementService.createDevice("iPhone 14");

        List<DeviceInfo> iPhone14DeviceList = deviceInfoService.getDevicesByName("iPhone 14");
        assertThat(iPhone14DeviceList).hasSize(1);
        int iphone14Id = iPhone14DeviceList.get(0).getId();

        deviceBookingService.bookDevice(
                BookingRequest.builder().person("Peter").deviceId(iphone14Id).build());

        assertThat(deviceInfoService.getAllAvailableDevices()).isEmpty();
        assertThat(deviceInfoService.getAllDevices()).hasSize(1);

        Assertions.assertThatRemoteExceptionThrownBy(() -> deviceBookingService.bookDevice(BookingRequest.builder()
                        .person("Andrej")
                        .deviceId(iphone14Id)
                        .build()))
                .isGeneratedFromErrorType(BookingErrors.DEVICE_NOT_AVAILABLE);
    }

    @Test
    public void bookAndReturnDevices() {
        deviceManagementService.createDevice("iPhone 14");
        deviceManagementService.createDevice("iPhone 13");

        deviceBookingService.bookDevice(BookingRequest.of("Andrej", "iPhone 14", OptionalInt.empty()));
        deviceBookingService.bookDevice(BookingRequest.of("Andrej", "iPhone 13", OptionalInt.empty()));
        assertThat(deviceInfoService.getAllAvailableDevices()).hasSize(0);

        List<DeviceInfo> iPhone14DeviceList = deviceInfoService.getDevicesByName("iPhone 14");
        assertThat(iPhone14DeviceList).hasSize(1);
        int iphone14IdThatAndrejUsed = iPhone14DeviceList.get(0).getId();
        Optional<OffsetDateTime> iphone14IdThatAndrejUsedTimestamp =
                iPhone14DeviceList.get(0).getLastBookedTime();
        assertThat(iphone14IdThatAndrejUsedTimestamp).isPresent();

        deviceManagementService.createDevice("iPhone 14");
        assertThat(deviceInfoService.getAllDevices()).hasSize(3);
        assertThat(deviceInfoService.getAllAvailableDevices()).hasSize(1);

        deviceBookingService.bookDevice(BookingRequest.of("Peter", "iPhone 14", OptionalInt.empty()));
        assertThat(deviceInfoService.getAllAvailableDevices()).hasSize(0);

        deviceBookingService.returnDevice(BookingRequest.of("Andrej", "iPhone 14", OptionalInt.empty()));
        assertThat(deviceInfoService.getAllDevices()).hasSize(3);
        assertThat(deviceInfoService.getAllAvailableDevices()).hasSize(1);
        assertThat(deviceInfoService.getDeviceById(iphone14IdThatAndrejUsed).getAvailable())
                .isTrue();

        deviceManagementService.createDevice("iPhone X");

        deviceBookingService.bookDevice(BookingRequest.of("Peter", "iPhone 14", OptionalInt.empty()));
        assertThat(deviceInfoService.getDeviceById(iphone14IdThatAndrejUsed).getLastBookedTime())
                .isNotEqualTo(iphone14IdThatAndrejUsedTimestamp);
    }
}

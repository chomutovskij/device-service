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

import com.achomutovskij.deviceservice.booking.api.DeviceBookingServiceBlocking;
import com.achomutovskij.deviceservice.info.api.DeviceInfoServiceBlocking;
import com.achomutovskij.deviceservice.management.api.DeviceManagementServiceBlocking;
import com.google.common.base.Suppliers;
import com.palantir.conjure.java.api.config.service.HumanReadableDuration;
import com.palantir.conjure.java.api.config.service.PartialServiceConfiguration;
import com.palantir.conjure.java.api.config.service.ServicesConfigBlock;
import com.palantir.conjure.java.api.config.service.UserAgent;
import com.palantir.conjure.java.api.config.service.UserAgent.Agent;
import com.palantir.dialogue.clients.DialogueClients;
import com.palantir.dialogue.clients.DialogueClients.ReloadingFactory;
import com.palantir.refreshable.Refreshable;
import java.net.URI;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Supplier;

// TODO(achomutovskij): make DeviceServiceApplicationTest class use DeviceServiceClients
final class DeviceServiceClients {

    private static final String MANAGEMENT_SERVICE = "management-service";
    private static final String MANAGEMENT_SERVICE_PATH = "/api/v1/management";

    private static final String INFO_SERVICE = "info-service";
    private static final String INFO_SERVICE_PATH = "/api/v1/info";

    private static final String BOOKING_SERVICE = "info-service";
    private static final String BOOKING_SERVICE_PATH = "/api/v1/booking";

    private final Supplier<DeviceManagementServiceBlocking> deviceManagementService;
    private final Supplier<DeviceInfoServiceBlocking> deviceInfoService;
    private final Supplier<DeviceBookingServiceBlocking> deviceBookingService;

    DeviceServiceClients(URI baseUrl, OptionalInt maxNumRetries) {
        ReloadingFactory dialogueClients = DialogueClients.create(Refreshable.only(ServicesConfigBlock.builder()
                        .defaultSecurity(DeviceServiceApplication.SSL_CONFIG)
                        .defaultConnectTimeout(HumanReadableDuration.seconds(30))
                        .defaultReadTimeout(Optional.of(HumanReadableDuration.seconds(15)))
                        .defaultWriteTimeout(Optional.of(HumanReadableDuration.seconds(15)))
                        .putServices(
                                MANAGEMENT_SERVICE,
                                PartialServiceConfiguration.builder()
                                        .addUris(baseUrl.resolve(MANAGEMENT_SERVICE_PATH)
                                                .toString())
                                        .build())
                        .putServices(
                                INFO_SERVICE,
                                PartialServiceConfiguration.builder()
                                        .addUris(baseUrl.resolve(INFO_SERVICE_PATH)
                                                .toString())
                                        .build())
                        .putServices(
                                BOOKING_SERVICE,
                                PartialServiceConfiguration.builder()
                                        .addUris(baseUrl.resolve(BOOKING_SERVICE_PATH)
                                                .toString())
                                        .build())
                        .build()))
                .withUserAgent(UserAgent.of(Agent.of("device-service-client-test", "0.0.0")));

        this.deviceManagementService = Suppliers.memoize(() -> {
            ReloadingFactory clientFactory = dialogueClients;
            if (maxNumRetries.isPresent()) {
                clientFactory = clientFactory.withMaxNumRetries(maxNumRetries.getAsInt());
            }
            return clientFactory.get(DeviceManagementServiceBlocking.class, MANAGEMENT_SERVICE);
        });

        this.deviceInfoService = Suppliers.memoize(() -> {
            ReloadingFactory clientFactory = dialogueClients;
            if (maxNumRetries.isPresent()) {
                clientFactory = clientFactory.withMaxNumRetries(maxNumRetries.getAsInt());
            }
            return clientFactory.get(DeviceInfoServiceBlocking.class, INFO_SERVICE);
        });

        this.deviceBookingService = Suppliers.memoize(() -> {
            ReloadingFactory clientFactory = dialogueClients;
            if (maxNumRetries.isPresent()) {
                clientFactory = clientFactory.withMaxNumRetries(maxNumRetries.getAsInt());
            }
            return clientFactory.get(DeviceBookingServiceBlocking.class, BOOKING_SERVICE);
        });
    }

    public DeviceManagementServiceBlocking managementService() {
        return deviceManagementService.get();
    }

    public DeviceInfoServiceBlocking infoService() {
        return deviceInfoService.get();
    }

    public DeviceBookingServiceBlocking bookingService() {
        return deviceBookingService.get();
    }
}

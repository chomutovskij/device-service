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

package com.achomutovskij.deviceservice.resources;

import com.achomutovskij.deviceservice.api.DeviceErrors;
import com.achomutovskij.deviceservice.api.DeviceInfo;
import com.achomutovskij.deviceservice.database.DatabaseManager;
import com.achomutovskij.deviceservice.gsm.GsmArenaDataProvider;
import com.achomutovskij.deviceservice.gsm.GsmNetworkDetails;
import com.achomutovskij.deviceservice.gsm.RapidApiClient;
import com.achomutovskij.deviceservice.info.api.UndertowDeviceInfoService;
import com.palantir.logsafe.Preconditions;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public final class DeviceInfoResource implements UndertowDeviceInfoService {

    private static final String INFO_UNAVAILABLE = "INFO UNAVAILABLE";

    private final DatabaseManager databaseManager;

    private final Optional<RapidApiClient> rapidApiClientOptional;
    private final GsmArenaDataProvider gsmArenaDataProvider;

    public DeviceInfoResource(
            DatabaseManager databaseManager,
            Optional<RapidApiClient> rapidApiClientOptional,
            GsmArenaDataProvider gsmArenaDataProvider) {
        this.databaseManager = Preconditions.checkNotNull(databaseManager, "Database manager must be non-null");
        this.rapidApiClientOptional = rapidApiClientOptional;
        this.gsmArenaDataProvider =
                Preconditions.checkNotNull(gsmArenaDataProvider, "GSM Arena Data Provider must be non-null");
    }

    @Override
    public List<DeviceInfo> getAllDevices() {
        return databaseManager.getAllDevices().stream()
                .map(this::enrichWithGsmNetworkDetails)
                .collect(Collectors.toList());
    }

    @Override
    public List<DeviceInfo> getDevicesByName(String name) {
        List<DeviceInfo> devices = databaseManager.getDevicesWithName(name);

        if (devices.isEmpty()) {
            throw DeviceErrors.deviceNameNotFound(name);
        }

        return devices.stream().map(this::enrichWithGsmNetworkDetails).collect(Collectors.toList());
    }

    @Override
    public DeviceInfo getDeviceById(int id) {
        return enrichWithGsmNetworkDetails(databaseManager.queryDeviceById(id));
    }

    @Override
    public List<DeviceInfo> getAllAvailableDevices() {
        return databaseManager.getAllAvailableDevices().stream()
                .map(this::enrichWithGsmNetworkDetails)
                .collect(Collectors.toList());
    }

    private DeviceInfo enrichWithGsmNetworkDetails(DeviceInfo fromDb) {

        DeviceInfo.Builder builder = DeviceInfo.builder()
                .id(fromDb.getId())
                .name(fromDb.getName())
                .available(fromDb.getAvailable())
                .lastBookedPersonName(fromDb.getLastBookedPersonName())
                .lastBookedTime(fromDb.getLastBookedTime());

        if (rapidApiClientOptional.isPresent()) {
            Optional<GsmNetworkDetails> gsmNetworkDetailsFromRapidApiOptional =
                    rapidApiClientOptional.get().lookup(fromDb.getName());
            if (gsmNetworkDetailsFromRapidApiOptional.isPresent()) {
                return enrich(builder, gsmNetworkDetailsFromRapidApiOptional.get());
            }
        }

        Optional<GsmNetworkDetails> gsmArenaDataOptional = gsmArenaDataProvider.lookupDevice(fromDb.getName());

        return gsmArenaDataOptional
                .map(gsmNetworkDetails -> enrich(builder, gsmNetworkDetails))
                .orElseGet(() -> builder.technology(INFO_UNAVAILABLE)
                        .twoGBands(INFO_UNAVAILABLE)
                        .threeGBands(INFO_UNAVAILABLE)
                        .fourGBands(INFO_UNAVAILABLE)
                        .build());
    }

    private static DeviceInfo enrich(DeviceInfo.Builder builder, GsmNetworkDetails gsmNetworkDetails) {
        return builder.technology(gsmNetworkDetails.technology())
                .twoGBands(gsmNetworkDetails.twoGBands())
                .threeGBands(gsmNetworkDetails.threeGBands())
                .fourGBands(gsmNetworkDetails.fourGBands())
                .build();
    }
}

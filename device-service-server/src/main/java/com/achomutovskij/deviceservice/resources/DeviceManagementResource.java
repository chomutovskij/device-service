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

import com.achomutovskij.deviceservice.database.DatabaseManager;
import com.achomutovskij.deviceservice.management.api.UndertowDeviceManagementService;
import com.palantir.logsafe.Preconditions;

public final class DeviceManagementResource implements UndertowDeviceManagementService {

    private final DatabaseManager databaseManager;

    public DeviceManagementResource(DatabaseManager databaseManager) {
        this.databaseManager = Preconditions.checkNotNull(databaseManager, "Database manager must be non-null");
    }

    @Override
    public void createDevice(String name) {
        databaseManager.registerDevice(name);
    }

    @Override
    public void deleteDevice(int id) {
        databaseManager.deleteDevice(id);
    }

    @Override
    public void deleteAllDevices() {
        databaseManager.deleteAllDevices();
    }
}

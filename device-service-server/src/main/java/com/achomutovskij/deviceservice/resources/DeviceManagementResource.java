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

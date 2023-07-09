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

package com.achomutovskij.deviceservice.database;

import com.achomutovskij.deviceservice.api.BookingErrors;
import com.achomutovskij.deviceservice.api.DeviceErrors;
import com.achomutovskij.deviceservice.api.DeviceInfo;
import com.palantir.conjure.java.api.errors.ErrorType;
import com.palantir.conjure.java.api.errors.ServiceException;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.apache.commons.lang3.tuple.Pair;

public final class DatabaseManager {

    private static final SafeLogger log = SafeLoggerFactory.get(DatabaseManager.class);

    private final HikariDataSource dataSource;
    private final ReadWriteLock readWriteLock;

    public DatabaseManager(String jdbcUrl, List<String> prefillWithDevices) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(Preconditions.checkNotNull(jdbcUrl, "JDBC url must be non-null"));
        this.dataSource = new HikariDataSource(config);
        this.readWriteLock = new ReentrantReadWriteLock();

        boolean tableWasAlreadyThere = ensureDbTableExist();
        if (!tableWasAlreadyThere) {
            log.info("About to populate the table with the devices");
            for (String device : Preconditions.checkNotNull(prefillWithDevices, "Devices list must be non-null")) {
                registerDevice(device);
            }
            log.info("Populated the table with {} devices", SafeArg.of("devices-list-size", prefillWithDevices.size()));
        }
    }

    public void stop() {
        dataSource.close();
    }

    public void registerDevice(String deviceName) {
        String insertQuery = "INSERT INTO devices (name, available) VALUES (?, ?)";
        readWriteLock.writeLock().lock();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(insertQuery)) {

            statement.setString(1, deviceName);
            statement.setBoolean(2, true);

            statement.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to insert data into table", e);
            throw new ServiceException(ErrorType.INTERNAL, e);
        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    public void deleteDevice(int deviceId) {
        String sql = "DELETE FROM devices WHERE id = ?;";
        readWriteLock.writeLock().lock();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, deviceId);
            statement.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to delete data from table", e);
            throw new ServiceException(ErrorType.INTERNAL, e);
        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    public void deleteAllDevices() {
        String sql = "DELETE FROM devices;";
        readWriteLock.writeLock().lock();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to delete data from table", e);
            throw new ServiceException(ErrorType.INTERNAL, e);
        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    public List<DeviceInfo> getAllDevices() {
        return getDevices("SELECT * FROM devices;", Optional.empty());
    }

    public List<DeviceInfo> getAllAvailableDevices() {
        return getDevices("SELECT * FROM devices WHERE available = 1;", Optional.empty());
    }

    public List<DeviceInfo> getDevicesWithName(String deviceName) {
        return getDevices("SELECT * FROM devices WHERE name LIKE ?;", Optional.of(Pair.of(1, "%" + deviceName + "%")));
    }

    public DeviceInfo queryDeviceById(int id) {
        List<DeviceInfo> devices = getDevices(getSelectQueryToFetchDeviceWithId(id), Optional.empty());
        if (devices.isEmpty()) {
            throw DeviceErrors.deviceIdNotFound(id);
        }
        return devices.get(0);
    }

    private String getSelectQueryToFetchDeviceWithId(int id) {
        return String.format("SELECT * FROM devices WHERE id = %d;", id);
    }

    private List<DeviceInfo> getDevices(String sql, Optional<Pair<Integer, String>> setStringOptional) {
        List<DeviceInfo> devices = new ArrayList<>();

        readWriteLock.readLock().lock();

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            if (setStringOptional.isPresent()) {
                statement.setString(
                        setStringOptional.get().getLeft(),
                        setStringOptional.get().getRight());
            }

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    devices.add(getDeviceInfoFromResultSet(resultSet));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to get data from table", e);
            throw new ServiceException(ErrorType.INTERNAL, e);
        } finally {
            readWriteLock.readLock().unlock();
        }

        return devices;
    }

    @SuppressWarnings("NestedTryDepth")
    public void bookDevice(String person, int deviceId) throws SQLException {
        readWriteLock.writeLock().lock();
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            connection.setAutoCommit(false); // start the transaction

            String selectSql = getSelectQueryToFetchDeviceWithId(deviceId);
            try (PreparedStatement selectStatement = connection.prepareStatement(selectSql)) {
                try (ResultSet resultSet = selectStatement.executeQuery()) {
                    if (resultSet.next()) {
                        if (resultSet.getBoolean("available")) {
                            String updateSql =
                                    "UPDATE devices SET available = ?, lastBookedPersonName = ?, lastBookedTime = ? "
                                            + "WHERE id = ?";
                            try (PreparedStatement updateStatement = connection.prepareStatement(updateSql)) {
                                bookingStatement(updateStatement, person, deviceId);
                                updateStatement.executeUpdate();
                            }
                        } else { // device with the given ID not available
                            throw BookingErrors.deviceNotAvailable();
                        }
                    } else { // device with the given ID not found
                        throw DeviceErrors.deviceIdNotFound(deviceId);
                    }
                }
            }

        } catch (SQLException ex) {
            // Rollback the transaction in case of any exception
            if (connection != null) {
                connection.rollback();
            }
            throw new ServiceException(ErrorType.INTERNAL, ex);
        } finally {
            if (connection != null) {
                connection.setAutoCommit(true);
                connection.close();
            }
            readWriteLock.writeLock().unlock();
        }
    }

    @SuppressWarnings("NestedTryDepth")
    public void bookDevice(String person, String deviceName) throws SQLException {
        readWriteLock.writeLock().lock();
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            connection.setAutoCommit(false); // start the transaction

            String selectSql = "SELECT * FROM devices WHERE available = 1 AND name = ?;";

            try (PreparedStatement selectStatement = connection.prepareStatement(selectSql)) {
                selectStatement.setString(1, deviceName);

                try (ResultSet resultSet = selectStatement.executeQuery()) {

                    boolean booked = false;
                    while (resultSet.next() && !booked) {
                        if (resultSet.getBoolean("available")) {
                            String updateSql =
                                    "UPDATE devices SET available = ?, lastBookedPersonName = ?, lastBookedTime = ? "
                                            + "WHERE id = ?";
                            try (PreparedStatement updateStatement = connection.prepareStatement(updateSql)) {
                                bookingStatement(updateStatement, person, resultSet.getInt("id"));
                                updateStatement.executeUpdate();
                            }
                            booked = true;
                        }
                    }

                    if (!booked) { // no available device with the given name found
                        throw BookingErrors.deviceNotAvailable();
                    }
                }
            }

        } catch (SQLException ex) {
            // Rollback the transaction in case of any exception
            if (connection != null) {
                connection.rollback();
            }
            throw new ServiceException(ErrorType.INTERNAL, ex);
        } finally {
            // Restore auto-commit mode and close the connection
            if (connection != null) {
                connection.setAutoCommit(true);
            }
            readWriteLock.writeLock().unlock();
        }
    }

    @SuppressWarnings("NestedTryDepth")
    public void returnDevice(String person, int deviceId) throws SQLException {
        readWriteLock.writeLock().lock();
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            connection.setAutoCommit(false); // start the transaction

            String selectSql = "SELECT * FROM devices WHERE id = ? AND lastBookedPersonName = ? AND available = 0;";

            try (PreparedStatement selectStatement = connection.prepareStatement(selectSql)) {
                selectStatement.setInt(1, deviceId);
                selectStatement.setString(2, person);

                try (ResultSet resultSet = selectStatement.executeQuery()) {
                    if (resultSet.next()) {
                        String updateSql = "UPDATE devices SET available = ? WHERE id = ?";
                        try (PreparedStatement updateStatement = connection.prepareStatement(updateSql)) {
                            returnStatement(updateStatement, deviceId);
                            updateStatement.executeUpdate();
                        }
                    } else {
                        throw BookingErrors.noPersonWithGivenBookedDevice();
                    }
                }
            }

        } catch (SQLException ex) {
            // Rollback the transaction in case of any exception
            if (connection != null) {
                connection.rollback();
            }
            throw new ServiceException(ErrorType.INTERNAL, ex);
        } finally {
            if (connection != null) {
                connection.setAutoCommit(true);
                connection.close();
            }
            readWriteLock.writeLock().unlock();
        }
    }

    @SuppressWarnings("NestedTryDepth")
    public void returnDevice(String person, String deviceName) throws SQLException {
        readWriteLock.writeLock().lock();
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            connection.setAutoCommit(false); // start the transaction

            String selectSql = "SELECT * FROM devices WHERE available = 0 AND lastBookedPersonName = ? AND name = ?;";

            try (PreparedStatement selectStatement = connection.prepareStatement(selectSql)) {
                selectStatement.setString(1, person);
                selectStatement.setString(2, deviceName);

                try (ResultSet resultSet = selectStatement.executeQuery()) {
                    boolean returned = false;
                    while (resultSet.next() && !returned) {
                        String updateSql = "UPDATE devices SET available = ? WHERE id = ?";
                        try (PreparedStatement updateStatement = connection.prepareStatement(updateSql)) {
                            returnStatement(updateStatement, resultSet.getInt("id"));
                            updateStatement.executeUpdate();
                        }
                        returned = true;
                    }

                    if (!returned) { // no previously booked device with the given name found
                        throw BookingErrors.noPersonWithGivenBookedDevice();
                    }
                }
            }

        } catch (SQLException ex) {
            // Rollback the transaction in case of any exception
            if (connection != null) {
                connection.rollback();
            }
            throw new ServiceException(ErrorType.INTERNAL, ex);
        } finally {
            // Restore auto-commit mode and close the connection
            if (connection != null) {
                connection.setAutoCommit(true);
            }
            readWriteLock.writeLock().unlock();
        }
    }

    private void bookingStatement(PreparedStatement updateStatement, String personName, int id) throws SQLException {
        updateStatement.setBoolean(1, false);
        updateStatement.setString(2, personName);
        updateStatement.setString(3, OffsetDateTime.now(ZoneId.of("Asia/Dubai")).toString());
        updateStatement.setInt(4, id);
    }

    private void returnStatement(PreparedStatement updateStatement, int id) throws SQLException {
        updateStatement.setBoolean(1, true);
        updateStatement.setInt(2, id);
    }

    private static DeviceInfo getDeviceInfoFromResultSet(ResultSet resultSet) throws SQLException {
        return DeviceInfo.builder()
                .id(resultSet.getInt("id"))
                .name(resultSet.getString("name"))
                .available(resultSet.getBoolean("available"))
                .lastBookedPersonName(Optional.ofNullable(resultSet.getString("lastBookedPersonName")))
                .lastBookedTime(Optional.ofNullable(resultSet.getString("lastBookedTime"))
                        .map(OffsetDateTime::parse))
                .build();
    }

    public boolean ensureDbTableExist() {
        String createTableSql = "CREATE TABLE IF NOT EXISTS devices ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT NOT NULL,"
                + "available INTEGER NOT NULL,"
                + "lastBookedPersonName TEXT,"
                + "lastBookedTime TEXT"
                + ");";

        boolean tableWasAlreadyThere;

        readWriteLock.writeLock().lock();
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {

            // Check if the table exists
            ResultSet resultSet =
                    statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='devices'");
            tableWasAlreadyThere = resultSet.next();

            statement.execute(createTableSql);

            if (tableWasAlreadyThere) {
                log.info("Devices table already exists");
            } else {
                log.info("Created the devices table");
            }

        } catch (SQLException e) {
            log.error("Exception when creating a table", e);
            throw new ServiceException(ErrorType.INTERNAL, e);
        } finally {
            readWriteLock.writeLock().unlock();
        }

        return tableWasAlreadyThere;
    }
}

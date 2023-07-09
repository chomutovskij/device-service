package com.achomutovskij.deviceservice.resources;

import com.achomutovskij.deviceservice.api.BookingErrors;
import com.achomutovskij.deviceservice.api.BookingRequest;
import com.achomutovskij.deviceservice.booking.api.UndertowDeviceBookingService;
import com.achomutovskij.deviceservice.database.DatabaseManager;
import com.palantir.conjure.java.api.errors.ErrorType;
import com.palantir.conjure.java.api.errors.ServiceException;
import com.palantir.logsafe.Preconditions;
import java.sql.SQLException;

public final class DeviceBookingResource implements UndertowDeviceBookingService {

    private final DatabaseManager databaseManager;

    public DeviceBookingResource(DatabaseManager databaseManager) {
        this.databaseManager = Preconditions.checkNotNull(databaseManager, "Database manager must be non-null");
    }

    @Override
    public void bookDevice(BookingRequest bookDeviceRequest) {
        if (bookDeviceRequest.getDeviceName().isEmpty()
                && bookDeviceRequest.getDeviceId().isEmpty()) {
            throw BookingErrors.requestMustHaveEitherDeviceIdOrName();
        }

        try {
            if (bookDeviceRequest.getDeviceName().isPresent()) {
                databaseManager.bookDevice(
                        bookDeviceRequest.getPerson(),
                        bookDeviceRequest.getDeviceName().get());
            }

            if (bookDeviceRequest.getDeviceId().isPresent()) {
                databaseManager.bookDevice(
                        bookDeviceRequest.getPerson(),
                        bookDeviceRequest.getDeviceId().getAsInt());
            }
        } catch (SQLException ex) {
            throw new ServiceException(ErrorType.INTERNAL, ex);
        }
    }

    @Override
    public void returnDevice(BookingRequest returnDeviceRequest) {
        if (returnDeviceRequest.getDeviceName().isEmpty()
                && returnDeviceRequest.getDeviceId().isEmpty()) {
            throw BookingErrors.requestMustHaveEitherDeviceIdOrName();
        }

        try {
            if (returnDeviceRequest.getDeviceName().isPresent()) {
                databaseManager.returnDevice(
                        returnDeviceRequest.getPerson(),
                        returnDeviceRequest.getDeviceName().get());
            }

            if (returnDeviceRequest.getDeviceId().isPresent()) {
                databaseManager.returnDevice(
                        returnDeviceRequest.getPerson(),
                        returnDeviceRequest.getDeviceId().getAsInt());
            }
        } catch (SQLException ex) {
            throw new ServiceException(ErrorType.INTERNAL, ex);
        }
    }
}

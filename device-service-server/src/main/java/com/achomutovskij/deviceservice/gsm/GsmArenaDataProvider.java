package com.achomutovskij.deviceservice.gsm;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import com.palantir.conjure.java.api.errors.ErrorType;
import com.palantir.conjure.java.api.errors.ServiceException;
import com.palantir.logsafe.Preconditions;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class GsmArenaDataProvider {

    private final Map<String, GsmNetworkDetails> dataMap;

    public GsmArenaDataProvider(String csvPath) {
        Preconditions.checkNotNull(csvPath, "Path needs to be non-null");
        this.dataMap = readData(csvPath);
    }

    private static Map<String, GsmNetworkDetails> readData(String csvPath) {
        Map<String, GsmNetworkDetails> dataMap = new HashMap<>();

        try (CSVReader reader = new CSVReader(Files.newBufferedReader(Path.of(csvPath), StandardCharsets.UTF_8))) {
            String[] nextLine;

            while (true) {
                try {
                    nextLine = reader.readNext();
                    if (nextLine == null) {
                        break;
                    }
                } catch (CsvValidationException ex) {
                    throw new ServiceException(ErrorType.INTERNAL, ex);
                }

                dataMap.put(
                        nextLine[0],
                        ImmutableGsmNetworkDetails.builder()
                                .technology(nextLine[1])
                                .twoGBands(nextLine[2])
                                .threeGBands(nextLine[4])
                                .fourGBands(nextLine[6])
                                .build());
            }
        } catch (IOException ex) {
            throw new ServiceException(ErrorType.INTERNAL, ex);
        }

        return dataMap;
    }

    public Optional<GsmNetworkDetails> lookupDevice(String deviceName) {
        return Optional.ofNullable(dataMap.get(deviceName));
    }
}

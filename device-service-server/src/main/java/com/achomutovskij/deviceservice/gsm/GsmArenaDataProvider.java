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

                dataMap.put(nextLine[0], new GsmNetworkDetails(nextLine[1], nextLine[2], nextLine[4], nextLine[6]));
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

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

import java.io.IOException;
import org.junit.jupiter.api.Test;

public class ConfigurationLoaderTest {

    @Test
    public void testConfigurationLoads() throws IOException {
        Configuration conf = ConfigurationLoader.load("src/test/resources/test_conf.yml");
        assertThat(conf.getHost()).isEqualTo("0.0.0.0");
        assertThat(conf.getPort()).isEqualTo(8345);
        assertThat(conf.getFirstStartupRegisterDevices()).hasSize(10);
        assertThat(conf.getApiKey()).hasValue("some-fake-api-key");
    }
}

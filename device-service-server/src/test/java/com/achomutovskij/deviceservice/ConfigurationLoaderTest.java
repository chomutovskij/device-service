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

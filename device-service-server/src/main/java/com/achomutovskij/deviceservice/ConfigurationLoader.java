package com.achomutovskij.deviceservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import java.io.File;
import java.io.IOException;

public final class ConfigurationLoader {

    private ConfigurationLoader() {
        // noop
    }

    public static Configuration load() throws IOException {
        return ConfigurationLoader.load("var/conf/conf.yml");
    }

    public static Configuration load(String path) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
        objectMapper.registerModule(new Jdk8Module());
        return objectMapper.readValue(new File(path), Configuration.class);
    }
}

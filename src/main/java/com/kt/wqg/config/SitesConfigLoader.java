package com.kt.wqg.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Path;

public final class SitesConfigLoader {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private SitesConfigLoader() {}

    public static SitesConfig load(Path path) throws IOException {
        return YAML.readValue(path.toFile(), SitesConfig.class);
    }
}

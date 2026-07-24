package com.kt.wqg.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.kt.wqg.config.SitesConfig;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 웹 콘솔이 sites.yaml 을 읽고 되쓰는 저장소.
 * UI 로 시스템을 편집하므로 주석 보존보다 값 왕복이 우선이다.
 */
public final class ConfigStore {

    private static final ObjectMapper YAML = new ObjectMapper(
            new YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER));

    private final Path file;

    public ConfigStore(Path file) {
        this.file = file;
    }

    public SitesConfig load() {
        try {
            return YAML.readValue(file.toFile(), SitesConfig.class);
        } catch (IOException e) {
            throw new UncheckedIOException("설정 로드 실패: " + file, e);
        }
    }

    public void save(SitesConfig config) {
        try {
            YAML.writeValue(file.toFile(), config);
        } catch (IOException e) {
            throw new UncheckedIOException("설정 저장 실패: " + file, e);
        }
    }

    /** 같은 name 의 시스템을 교체 저장한다 (없으면 예외). */
    public void replaceSite(SitesConfig.Site updated) {
        SitesConfig config = load();
        List<SitesConfig.Site> sites = new ArrayList<>(config.sites());
        int idx = -1;
        for (int i = 0; i < sites.size(); i++) {
            if (sites.get(i).name().equals(updated.name())) { idx = i; break; }
        }
        if (idx < 0) throw new IllegalArgumentException("시스템을 찾을 수 없음: " + updated.name());
        sites.set(idx, updated);
        save(new SitesConfig(sites, config.checks(), config.output()));
    }
}

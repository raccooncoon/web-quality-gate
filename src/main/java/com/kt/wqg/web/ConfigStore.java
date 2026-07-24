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
        int idx = indexOf(sites, updated.name());
        if (idx < 0) throw new IllegalArgumentException("시스템을 찾을 수 없음: " + updated.name());
        sites.set(idx, updated);
        save(new SitesConfig(sites, config.checks(), config.output()));
    }

    /** 새 시스템을 추가한다 (name 중복 시 예외). 기본값은 미설정 템플릿. */
    public void addSite(SitesConfig.Site site) {
        SitesConfig config = load();
        List<SitesConfig.Site> sites = new ArrayList<>(config.sites());
        if (indexOf(sites, site.name()) >= 0) {
            throw new IllegalArgumentException("이미 있는 시스템명: " + site.name());
        }
        sites.add(site);
        save(new SitesConfig(sites, config.checks(), config.output()));
    }

    /** 시스템을 삭제한다 (없으면 예외). */
    public void removeSite(String name) {
        SitesConfig config = load();
        List<SitesConfig.Site> sites = new ArrayList<>(config.sites());
        int idx = indexOf(sites, name);
        if (idx < 0) throw new IllegalArgumentException("시스템을 찾을 수 없음: " + name);
        sites.remove(idx);
        save(new SitesConfig(sites, config.checks(), config.output()));
    }

    private static int indexOf(List<SitesConfig.Site> sites, String name) {
        for (int i = 0; i < sites.size(); i++) {
            if (sites.get(i).name().equals(name)) return i;
        }
        return -1;
    }
}

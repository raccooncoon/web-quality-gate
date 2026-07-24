package com.kt.wqg.web;

import com.kt.wqg.config.SitesConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigStoreTest {

    private static final String YAML = """
            sites:
              - name: main-portal
                displayName: "메인포털"
                type: spa
                baseUrl: http://TODO
                auth: { type: none }
                urls:
                  - path: /
            checks:
              accessibility: { enabled: true, tags: [wcag2a] }
              markup: { enabled: true, vnuJarPath: /opt/wqg/vnu.jar }
              compatibility: { enabled: true, engines: [chromium], pixelDiffThreshold: 0.02 }
            output: { dir: ./reports }
            """;

    @Test
    void 시스템의_baseUrl을_수정해_저장하면_되읽힌다(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("sites.yaml");
        Files.writeString(file, YAML);
        ConfigStore store = new ConfigStore(file);

        SitesConfig.Site s = store.load().sites().get(0);
        SitesConfig.Site updated = new SitesConfig.Site(s.name(), s.displayName(), s.type(),
                "http://10.0.0.9", s.auth(), s.urls());
        store.replaceSite(updated);

        SitesConfig reloaded = new ConfigStore(file).load();
        assertEquals("http://10.0.0.9", reloaded.sites().get(0).baseUrl());
        assertEquals("메인포털", reloaded.sites().get(0).displayName());
        // 다른 섹션도 보존
        assertEquals(List.of("chromium"), reloaded.checks().compatibility().engines());
    }

    @Test
    void 시스템_추가_삭제가_왕복된다(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("sites.yaml");
        Files.writeString(file, YAML);
        ConfigStore store = new ConfigStore(file);

        store.addSite(new SitesConfig.Site("cloud-cmp", "클라우드포털", "spa", "http://TODO",
                new SitesConfig.Auth("none", null, null, null, null),
                List.of(new SitesConfig.Url("/", null))));
        assertEquals(2, new ConfigStore(file).load().sites().size());

        store.removeSite("main-portal");
        SitesConfig after = new ConfigStore(file).load();
        assertEquals(1, after.sites().size());
        assertEquals("cloud-cmp", after.sites().get(0).name());
    }

    @Test
    void 중복_추가와_없는_삭제는_예외(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("sites.yaml");
        Files.writeString(file, YAML);
        ConfigStore store = new ConfigStore(file);
        SitesConfig.Site dup = new SitesConfig.Site("main-portal", "x", "spa", "http://x",
                new SitesConfig.Auth("none", null, null, null, null), List.of(new SitesConfig.Url("/", null)));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> store.addSite(dup));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> store.removeSite("nope"));
    }
}

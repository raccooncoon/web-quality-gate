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
}

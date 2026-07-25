package com.kt.wqg.web;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * LLM 연결 설정(base URL·모델·API 키)을 로컬 파일에 저장/로드.
 * 파일에 값이 없으면 환경변수(WQG_LLM_*)로 폴백한다. 키는 이 PC 로컬에만 저장(git 제외).
 */
public final class LlmConfigStore {

    private final Path file;

    public LlmConfigStore(Path file) { this.file = file; }

    public String baseUrl() { return resolve("baseUrl", "WQG_LLM_BASE_URL", ""); }
    public String model()   { return resolve("model", "WQG_LLM_MODEL", "gpt-4o-mini"); }
    public String apiKey()  { return resolve("apiKey", "WQG_LLM_API_KEY", ""); }

    public boolean configured() { return !baseUrl().isBlank(); }

    /** 파일 값 우선, 없으면 환경변수, 그것도 없으면 기본값. */
    private String resolve(String key, String env, String def) {
        Properties p = load();
        String v = p.getProperty(key);
        if (v != null && !v.isBlank()) return v.trim();
        String e = System.getenv(env);
        if (e != null && !e.isBlank()) return e.trim();
        return def;
    }

    private Properties load() {
        Properties p = new Properties();
        if (Files.isRegularFile(file)) {
            try (var in = Files.newInputStream(file)) { p.load(in); } catch (IOException ignore) { }
        }
        return p;
    }

    public void save(String baseUrl, String model, String apiKey) {
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            StringBuilder sb = new StringBuilder("# web-quality-gate LLM 설정 (이 PC 로컬 전용, git 제외)\n");
            sb.append("baseUrl=").append(baseUrl == null ? "" : baseUrl.trim()).append('\n');
            sb.append("model=").append(model == null ? "" : model.trim()).append('\n');
            sb.append("apiKey=").append(apiKey == null ? "" : apiKey.trim()).append('\n');
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("LLM 설정 저장 실패: " + file, e);
        }
    }

    /** 현재 설정으로 클라이언트 생성. */
    public com.kt.wqg.llm.LlmClient client() {
        return new com.kt.wqg.llm.LlmClient(baseUrl(), model(), apiKey());
    }
}

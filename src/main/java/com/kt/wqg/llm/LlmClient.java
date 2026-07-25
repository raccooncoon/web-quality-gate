package com.kt.wqg.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Chat Completions 호환 LLM 클라이언트(폐쇄망 GenOS 서빙 / 외부 OpenAI 포맷 모두 지원).
 *
 * 설정(환경변수 또는 생성자):
 *   WQG_LLM_BASE_URL  예) http://genos.internal/v1   (끝에 /chat/completions 를 붙여 호출)
 *   WQG_LLM_MODEL     예) qwen2.5-vl-7b, gpt-4o-mini 등
 *   WQG_LLM_API_KEY   Authorization: Bearer 헤더 (없으면 생략 — 사내 서빙 무인증 대비)
 *
 * 폐쇄망 반입성: JDK java.net.http 만 사용(추가 의존성 없음). LLM 은 선택 기능이라 미설정 시 비활성.
 */
public final class LlmClient {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final String baseUrl;
    private final String model;
    private final String apiKey;
    private final HttpClient http;

    public LlmClient(String baseUrl, String model, String apiKey) {
        this.baseUrl = baseUrl == null ? null : baseUrl.replaceAll("/+$", "");
        this.model = model;
        this.apiKey = apiKey;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public static LlmClient fromEnv() {
        return new LlmClient(System.getenv("WQG_LLM_BASE_URL"),
                System.getenv().getOrDefault("WQG_LLM_MODEL", "gpt-4o-mini"),
                System.getenv("WQG_LLM_API_KEY"));
    }

    public boolean isConfigured() { return baseUrl != null && !baseUrl.isBlank(); }

    public String model() { return model; }

    /** system·user 프롬프트로 chat/completions 호출. 응답 message.content(문자열) 반환. */
    public String chat(String system, String user) {
        return chat(system, user, false);
    }

    /** jsonMode=true 면 response_format=json_object 로 강제(모델이 지원 시). */
    public String chat(String system, String user, boolean jsonMode) {
        return complete(system, user, jsonMode);
    }

    /**
     * 비전 호출 — user 메시지에 텍스트 + 이미지(data:image/...;base64) 를 함께 보낸다(OpenAI 비전 포맷).
     * 이미지가 없으면 일반 chat 과 동일.
     */
    public String chatVision(String system, String userText, List<String> imageDataUris, boolean jsonMode) {
        if (imageDataUris == null || imageDataUris.isEmpty()) return complete(system, userText, jsonMode);
        List<Object> content = new java.util.ArrayList<>();
        content.add(Map.of("type", "text", "text", userText));
        for (String uri : imageDataUris) {
            content.add(Map.of("type", "image_url", "image_url", Map.of("url", uri)));
        }
        return complete(system, content, jsonMode);
    }

    /** userContent 는 String(텍스트) 또는 List(비전 content 배열). */
    private String complete(String system, Object userContent, boolean jsonMode) {
        if (!isConfigured()) throw new IllegalStateException("LLM 미설정(WQG_LLM_BASE_URL)");
        try {
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("model", model);
            body.put("temperature", 0);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", system),
                    Map.of("role", "user", "content", userContent)));
            if (jsonMode) body.put("response_format", Map.of("type", "json_object"));

            HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json");
            if (apiKey != null && !apiKey.isBlank()) req.header("Authorization", "Bearer " + apiKey);
            req.POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));

            HttpResponse<String> resp = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new RuntimeException("LLM HTTP " + resp.statusCode() + ": "
                        + resp.body().substring(0, Math.min(300, resp.body().length())));
            }
            JsonNode root = JSON.readTree(resp.body());
            return root.path("choices").path(0).path("message").path("content").asText("");
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("LLM 호출 실패: " + e.getMessage(), e);
        }
    }
}

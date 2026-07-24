package com.kt.wqg.report;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * 모든 검사(접근성·마크업·호환성)가 공유하는 결과 스키마.
 * engine 은 호환성 검사에만 존재하므로 null 이면 JSON 에서 생략한다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CheckResult(
        String site,
        String url,
        String checkType,
        String timestamp,
        String engine,
        boolean passed,
        List<Violation> violations) {

    public record Violation(String id, String impact, String help, int nodes) {}

    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);

    public static String toJson(CheckResult result) {
        try {
            return JSON.writeValueAsString(result);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("결과 직렬화 실패: " + result.url(), e);
        }
    }
}

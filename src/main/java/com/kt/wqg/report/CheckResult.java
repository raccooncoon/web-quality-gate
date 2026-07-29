package com.kt.wqg.report;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * 모든 검사(접근성·마크업·호환성)가 공유하는 결과 스키마.
 * null 필드는 JSON 에서 생략한다 (engine·screenshots·diffRatio 는 호환성만 등).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CheckResult(
        String site,
        String url,
        String checkType,
        String timestamp,
        String engine,
        boolean passed,
        List<Violation> violations,
        Map<String, String> screenshots,   // 호환성: engine → report.html 기준 상대 png 경로
        Double diffRatio,                   // 호환성: 기준 엔진 대비 픽셀 차이 비율
        List<Pass> passes) {                // 접근성: 통과한 규칙과 검사 요소 수(적합 근거)

    /** 통과 규칙 하나 — 검사해서 정상이었던 요소 수 + 실제 요소(대상) 샘플 셀렉터. */
    public record Pass(String rule, int nodes, List<String> targets) {
        public Pass(String rule, int nodes) { this(rule, nodes, List.of()); }
    }

    /** 스크린샷·diff 가 없는 검사(마크업)용 생성자. */
    public CheckResult(String site, String url, String checkType, String timestamp,
                       String engine, boolean passed, List<Violation> violations) {
        this(site, url, checkType, timestamp, engine, passed, violations, null, null, null);
    }

    /** 접근성용(통과 내역 포함) 생성자. */
    public CheckResult(String site, String url, String checkType, String timestamp,
                       String engine, boolean passed, List<Violation> violations, List<Pass> passes) {
        this(site, url, checkType, timestamp, engine, passed, violations, null, null, passes);
    }

    /** 호환성용(스크린샷·diff) 생성자. */
    public CheckResult(String site, String url, String checkType, String timestamp,
                       String engine, boolean passed, List<Violation> violations,
                       Map<String, String> screenshots, Double diffRatio) {
        this(site, url, checkType, timestamp, engine, passed, violations, screenshots, diffRatio, null);
    }

    /** 접근성 위반의 문제 요소 샘플. */
    public record Element(String target, String html) {}

    public record Violation(String id, String impact, String help, int nodes,
                            String kwcag, List<Element> elements) {
        /** kwcag·요소 상세가 없는 위반(마크업·호환성)용 생성자. */
        public Violation(String id, String impact, String help, int nodes) {
            this(id, impact, help, nodes, null, null);
        }
    }

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

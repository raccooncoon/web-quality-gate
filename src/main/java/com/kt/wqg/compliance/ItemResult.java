package com.kt.wqg.compliance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** 한 시스템 기준의 항목별 검사 결과(모든 URL 롤업 후). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ItemResult(String code, ItemVerdict verdict, String source,
                         List<Evidence> evidence, String memo) {

    /** 항목 위반 증거 한 건. target = 실패한 요소 selector(대상). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Evidence(String url, String rule, String detail, String target) {
        /** target 없는 기존 호출부용(마크업·호환성·통과 증거 등 대상 selector가 없는 경우). */
        public Evidence(String url, String rule, String detail) {
            this(url, rule, detail, null);
        }
    }

    /** 자동 판정 결과(증거·메모 없음)를 만든다. */
    public static ItemResult of(String code, ItemVerdict verdict) {
        return new ItemResult(code, verdict, "auto", List.of(), null);
    }
}

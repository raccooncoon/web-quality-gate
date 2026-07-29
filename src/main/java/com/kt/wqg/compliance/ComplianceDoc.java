package com.kt.wqg.compliance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

/**
 * compliance.json 스키마: 실행 1회의 자동 판정 결과(항목 순서 유지).
 * excluded: 관리자가 검사 제외한 코드. pages: 페이지별 판정(대상 페이지·페이지별 평가표·동영상 콘텐츠 표용).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ComplianceDoc(String site, String displayName, String timestamp,
                            List<ItemResult> items, List<String> excluded,
                            List<Map<String, Object>> pages) {
    /** 제외 없는 기존 호출 호환. */
    public ComplianceDoc(String site, String displayName, String timestamp, List<ItemResult> items) {
        this(site, displayName, timestamp, items, List.of(), List.of());
    }
    /** pages 없는 기존 호출 호환. */
    public ComplianceDoc(String site, String displayName, String timestamp,
                         List<ItemResult> items, List<String> excluded) {
        this(site, displayName, timestamp, items, excluded, List.of());
    }
}

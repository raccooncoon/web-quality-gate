package com.kt.wqg.compliance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** compliance.json 스키마: 실행 1회의 자동 판정 결과(항목 순서 유지). excluded: 관리자가 검사 제외한 코드. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ComplianceDoc(String site, String displayName, String timestamp,
                            List<ItemResult> items, List<String> excluded) {
    /** 제외 없는 기존 호출 호환. */
    public ComplianceDoc(String site, String displayName, String timestamp, List<ItemResult> items) {
        this(site, displayName, timestamp, items, List.of());
    }
}

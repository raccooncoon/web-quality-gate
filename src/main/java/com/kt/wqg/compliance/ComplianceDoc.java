package com.kt.wqg.compliance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** compliance.json 스키마: 실행 1회의 자동 판정 결과(항목 순서 유지). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ComplianceDoc(String site, String displayName, String timestamp, List<ItemResult> items) {}

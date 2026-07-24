package com.kt.wqg.compliance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** KWCAG 2.2 검수 카탈로그의 한 항목(데이터). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogItem(int no, String code, String category, String siClassification,
                          String automation, String title, String criteria,
                          Resolver resolver, Guide guide) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Resolver(String type, List<String> rules) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Guide(String what, String why, String basis, String fix, String limit) {}

    public boolean isAccessibility() { return "accessibility".equals(category); }
    public boolean siAuto() { return "auto".equals(siClassification); }
}

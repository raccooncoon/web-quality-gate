package com.kt.wqg.compliance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

/** KWCAG 2.2 검수 카탈로그 로더. 클래스패스 리소스에서 37항목을 읽는다. */
public final class KwcagCatalog {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private final List<CatalogItem> items;

    private KwcagCatalog(List<CatalogItem> items) { this.items = items; }

    public static KwcagCatalog load() {
        try (InputStream in = KwcagCatalog.class.getResourceAsStream("/kwcag22-catalog.yaml")) {
            if (in == null) throw new IllegalStateException("kwcag22-catalog.yaml 리소스 없음");
            Wrapper w = YAML.readValue(in, Wrapper.class);
            return new KwcagCatalog(w.items());
        } catch (java.io.IOException e) {
            throw new UncheckedIOException("카탈로그 로드 실패", e);
        }
    }

    public List<CatalogItem> items() { return items; }

    /** 지정한 코드들을 제외한 카탈로그(검사 범위에서 제외). 매핑·집계·리포트가 자동으로 반영된다. */
    public KwcagCatalog without(java.util.Set<String> excludedCodes) {
        if (excludedCodes == null || excludedCodes.isEmpty()) return this;
        return new KwcagCatalog(items.stream().filter(c -> !excludedCodes.contains(c.code())).toList());
    }

    public CatalogItem byCode(String code) {
        return items.stream().filter(c -> c.code().equals(code)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("항목 코드 없음: " + code));
    }

    public List<CatalogItem> byBucket(String category, boolean siAuto) {
        return items.stream()
                .filter(c -> c.category().equals(category) && c.siAuto() == siAuto)
                .toList();
    }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private record Wrapper(List<CatalogItem> items) {}
}

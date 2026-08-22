package com.kt.wqg.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SitesConfig(List<Site> sites, Checks checks, Output output) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Site(String name, String displayName, String type,
                       String baseUrl, Auth auth, List<Url> urls,
                       @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> auto) {
        /** auto(자동 판별 필드 출처) 없는 기존 호출부용. */
        public Site(String name, String displayName, String type, String baseUrl, Auth auth, List<Url> urls) {
            this(name, displayName, type, baseUrl, auth, urls, null);
        }
        /** displayName 없는 기존 호출부(테스트 등)용. */
        public Site(String name, String type, String baseUrl, Auth auth, List<Url> urls) {
            this(name, null, type, baseUrl, auth, urls, null);
        }
        public boolean isSpa() { return "spa".equalsIgnoreCase(type); }
        /** 리포트 표기용 이름 — displayName 이 있으면 그것, 없으면 name. */
        public String label() {
            return displayName != null && !displayName.isBlank() ? displayName : name;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Auth(String type, String loginPath,
                       String idSelector, String pwSelector, String submitSelector) {
        /** form·SSO 는 매 검사 시 로그인이 필요. storage(세션 재사용)는 별도 처리. */
        public boolean required() {
            return "form".equalsIgnoreCase(type) || "sso".equalsIgnoreCase(type);
        }
        public boolean isSso() { return "sso".equalsIgnoreCase(type); }
        /** 저장된 로그인 세션(storageState)을 재사용하는 방식. */
        public boolean isStorage() { return "storage".equalsIgnoreCase(type); }
    }

    /**
     * 검사 경로. label 은 리포트 '대상 페이지' 카드에 쓸 표시명(예: "홈 &gt; 시작하기").
     * 비어 있으면 경로 기반 빵부스러기("홈 &gt; app &gt; w &gt; 1 &gt; start")로 자동 표기.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Url(String path, String readySelector, String label) {
        /** label 없는 기존 호출·설정과의 호환용. */
        public Url(String path, String readySelector) { this(path, readySelector, null); }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Checks(Accessibility accessibility, Markup markup, Compatibility compatibility) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Accessibility(boolean enabled, List<String> tags) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Markup(boolean enabled, String vnuJarPath) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Compatibility(boolean enabled, List<String> engines, double pixelDiffThreshold) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Output(String dir) {}
}

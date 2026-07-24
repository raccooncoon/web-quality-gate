package com.kt.wqg.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SitesConfig(List<Site> sites, Checks checks, Output output) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Site(String name, String type, String baseUrl, Auth auth, List<Url> urls) {
        public boolean isSpa() { return "spa".equalsIgnoreCase(type); }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Auth(String type, String loginPath,
                       String idSelector, String pwSelector, String submitSelector) {
        public boolean required() { return "form".equalsIgnoreCase(type); }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Url(String path, String readySelector) {}

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

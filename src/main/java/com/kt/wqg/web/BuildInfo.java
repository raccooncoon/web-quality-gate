package com.kt.wqg.web;

import java.util.Properties;

/** 빌드 버전·배포시각(build.properties, Maven 필터링). 배포시각은 한국시간(KST) 표기. */
public final class BuildInfo {

    public static final String VERSION;
    public static final String BUILD_TIME;

    static {
        String v = "dev", b = "-";
        try (var in = BuildInfo.class.getResourceAsStream("/build.properties")) {
            if (in != null) {
                Properties p = new Properties();
                p.load(in);
                v = p.getProperty("version", "dev");
                String raw = p.getProperty("buildTime", "-");
                try {
                    b = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                            .withZone(java.time.ZoneId.of("Asia/Seoul"))
                            .format(java.time.Instant.parse(raw)) + " KST";
                } catch (Exception e) {
                    b = raw.replace("T", " ").replace("Z", " UTC");
                }
            }
        } catch (Exception ignore) { /* 없으면 기본값 */ }
        VERSION = v;
        BUILD_TIME = b;
    }

    private BuildInfo() {}
}

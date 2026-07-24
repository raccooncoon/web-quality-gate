package com.kt.wqg.check;

import com.kt.wqg.config.SitesConfig;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * 사이트별 브라우저 컨텍스트를 만들고 URL을 순회하며 검사를 수행한다.
 */
public final class CheckRunner {

    /** 로그인 계정. 설정 파일이 아닌 환경변수에서만 온다. */
    record Credentials(String id, String pw) {}

    /** URL 한 건의 방문 결과. 실패해도 순회는 중단하지 않는다. */
    record UrlResult(String url, boolean ok, String error) {}

    /**
     * 사이트의 모든 URL을 순회한다.
     * URL 하나가 실패해도 원인만 기록하고 다음 URL로 넘어간다 —
     * 사이트 하나가 통째로 미검사로 남는 것을 막는다.
     */
    static List<UrlResult> visitAll(SitesConfig.Site site, Consumer<String> visit) {
        return site.urls().stream().map(u -> {
            String url = resolveUrl(site.baseUrl(), u.path());
            try {
                visit.accept(url);
                return new UrlResult(url, true, null);
            } catch (RuntimeException e) {
                return new UrlResult(url, false, e.getMessage());
            }
        }).toList();
    }

    /**
     * 사이트명으로 WQG_&lt;대문자&gt;_ID / _PW 환경변수를 조회한다.
     * 조회 함수를 주입받아 테스트에서 실제 환경변수 없이 검증 가능하게 둔다.
     */
    static Credentials credentialsOf(String siteName, UnaryOperator<String> env) {
        String prefix = "WQG_" + siteName.toUpperCase(Locale.ROOT).replace('-', '_');
        return new Credentials(require(env, prefix + "_ID"), require(env, prefix + "_PW"));
    }

    private static String require(UnaryOperator<String> env, String key) {
        String value = env.apply(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("환경변수 " + key + " 가 설정되지 않았습니다");
        }
        return value;
    }

    /** baseUrl과 path의 슬래시 유무에 상관없이 슬래시 하나로 이어붙인다. */
    static String resolveUrl(String baseUrl, String path) {
        String base = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
        String suffix = path.startsWith("/") ? path : "/" + path;
        return base + suffix;
    }
}

package com.kt.wqg;

import com.kt.wqg.config.SitesConfig;
import com.kt.wqg.config.SitesConfigLoader;

import java.nio.file.Path;

/**
 * web-quality-gate 진입점.
 *
 * 실행: java -jar web-quality-gate.jar [sites.yaml 경로]
 * 필수 환경변수: PLAYWRIGHT_BROWSERS_PATH (반입한 브라우저 번들 경로)
 *
 * 검사 매트릭스: 사이트 × URL × (접근성 | 마크업 | 호환성)
 */
public class Main {

    public static void main(String[] args) throws Exception {
        Path configPath = Path.of(args.length > 0 ? args[0] : "config/sites.yaml");
        SitesConfig config = SitesConfigLoader.load(configPath);

        // TODO Phase 3:
        //  1. CheckRunner — 사이트별 BrowserContext 생성, 로그인 처리
        //  2. AccessibilityCheck — AxeBuilder 기반, SPA는 readySelector 대기 후 분석
        //  3. MarkupCheck — mpa: 원본 응답 / spa: page.content() → vnu.jar 실행
        //  4. CompatibilityCheck — 3개 엔진 스크린샷 + 픽셀 diff (애니메이션 비활성화)
        //  5. ReportWriter — 검사 결과 JSON → reports/ 디렉터리 집계

        System.out.printf("로드된 사이트 수: %d%n", config.sites().size());
        config.sites().forEach(s ->
                System.out.printf("- %s (%s) urls=%d%n", s.name(), s.type(), s.urls().size()));
    }
}

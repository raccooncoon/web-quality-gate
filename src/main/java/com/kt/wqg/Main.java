package com.kt.wqg;

import com.kt.wqg.check.CheckService;
import com.kt.wqg.config.SitesConfig;
import com.kt.wqg.config.SitesConfigLoader;
import com.kt.wqg.web.WebConsole;

import java.nio.file.Path;
import java.util.List;

/**
 * web-quality-gate 진입점.
 *
 * 실행:
 *   java -jar web-quality-gate.jar serve [포트]           웹 콘솔 기동 (설정·실행·결과)
 *   java -jar web-quality-gate.jar [sites.yaml] [시스템명]  CLI 검사 (시스템명 없으면 전체)
 *
 * 필수 환경변수: PLAYWRIGHT_BROWSERS_PATH (반입한 브라우저 번들 경로).
 * form 로그인 시스템의 계정: WQG_&lt;시스템명 대문자&gt;_ID / _PW (또는 웹 콘솔에서 입력).
 */
public class Main {

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("serve")) {
            int port = args.length > 1 ? Integer.parseInt(args[1]) : 8080;
            new WebConsole(Path.of("config/sites.yaml"), port).start();
            return;
        }

        Path configPath = Path.of(args.length > 0 ? args[0] : "config/sites.yaml");
        String onlySite = args.length > 1 ? args[1] : null;
        SitesConfig config = SitesConfigLoader.load(configPath);

        List<SitesConfig.Site> targets = config.sites().stream()
                .filter(s -> onlySite == null || s.name().equals(onlySite))
                .toList();
        if (targets.isEmpty()) {
            System.err.printf("대상 시스템 없음%s%n", onlySite == null ? "" : ": " + onlySite);
            return;
        }

        for (SitesConfig.Site site : targets) {
            try {
                Path report = CheckService.runSite(config, site, System::getenv,
                        msg -> System.out.printf("[%s] %s%n", site.name(), msg));
                System.out.printf("[%s] 검사 완료 → %s%n", site.name(), report);
            } catch (RuntimeException e) {
                // 시스템 단위 실패 격리 — 하나가 죽어도 나머지는 계속
                System.err.printf("[%s] 검사 실패: %s%n", site.name(), e.getMessage());
            }
        }
    }
}

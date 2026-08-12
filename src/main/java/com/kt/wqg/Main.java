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
 *   java -jar web-quality-gate.jar serve [포트] [호스트]    웹 콘솔 기동 (설정·실행·결과)
 *      호스트 생략 시 127.0.0.1(로컬 전용). 0.0.0.0 또는 LAN IP 지정 시 다른 PC에서 접근 가능
 *      (GUI PC → 서버 세션 전송용). 폐쇄망 내부에서만 노출하세요.
 *   java -jar web-quality-gate.jar [sites.yaml] [시스템명]  CLI 검사 (시스템명 없으면 전체)
 *
 * 필수 환경변수: PLAYWRIGHT_BROWSERS_PATH (반입한 브라우저 번들 경로).
 * SSO 로그인 시스템의 계정: WQG_&lt;시스템명 대문자&gt;_ID / _PW (또는 웹 콘솔에서 입력).
 */
public class Main {

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("serve")) {
            int port = args.length > 1 ? Integer.parseInt(args[1]) : 8080;
            String host = args.length > 2 ? args[2] : "127.0.0.1";
            new WebConsole(Path.of("config/sites.yaml"), port, host).start();
            return;
        }

        // 폐쇄망 드라이버 진단: 환경·브라우저 번들 확인 + Playwright.create() 전체 원인 스택 +
        // 추출된 node 실행 파일을 직접 --version 실행해 'node 가 실제로 도는지'를 확정한다.
        //   java -jar ... doctor      ('Failed to create driver' 원인 규명용)
        if (args.length > 0 && args[0].equals("doctor")) {
            runDoctor();
            return;
        }

        // 폐쇄망 설치 검증용 스모크: chromium 헤드리스가 실제로 뜨고 네비게이션되는지 확인.
        // OS 라이브러리가 빠졌으면 여기서 브라우저 기동 오류로 즉시 드러난다.
        if (args.length > 0 && args[0].equals("--smoke")) {
            String url = args.length > 1 ? args[1] : "about:blank";
            try (var pw = com.microsoft.playwright.Playwright.create()) {
                var browser = pw.chromium().launch(
                        new com.microsoft.playwright.BrowserType.LaunchOptions().setHeadless(true));
                try {
                    var page = browser.newContext().newPage();
                    var resp = page.navigate(url);
                    System.out.printf("스모크 OK: chromium %s · %s → HTTP %d · title=\"%s\"%n",
                            browser.version(), url, resp == null ? 0 : resp.status(), page.title());
                } finally {
                    browser.close();
                }
            }
            return;
        }

        // 재검사 없이 저장된 compliance.json 으로 report.html 만 다시 생성(리포트 서식/문구 갱신용).
        //   java -jar ... regen-report [reportDir ...]   인자 없으면 reports/ 전체 순회
        if (args.length > 0 && args[0].equals("regen-report")) {
            SitesConfig cfg = SitesConfigLoader.load(Path.of("config/sites.yaml"));
            var cred = new com.kt.wqg.web.CredentialStore(
                    Path.of("config/credentials.properties")).resolver();
            List<Path> dirs = new java.util.ArrayList<>();
            if (args.length > 1) {
                for (int i = 1; i < args.length; i++) dirs.add(Path.of(args[i]));
            } else {
                Path root = Path.of(cfg.output().dir());
                if (java.nio.file.Files.isDirectory(root)) {
                    try (var s = java.nio.file.Files.walk(root, 2)) {
                        s.filter(p -> java.nio.file.Files.isDirectory(p)
                                && java.nio.file.Files.isRegularFile(p.resolve("compliance.json")))
                         .forEach(dirs::add);
                    }
                }
            }
            for (Path dir : dirs) {
                String siteName = dir.getParent().getFileName().toString();
                SitesConfig.Site site = cfg.sites().stream()
                        .filter(x -> x.name().equals(siteName)).findFirst().orElse(null);
                if (site == null) {
                    System.err.printf("사이트 설정 없음: %s (%s) — 건너뜀%n", siteName, dir);
                    continue;
                }
                try {
                    Path html = CheckService.writeComplianceReport(dir, site, cred);
                    System.out.println("리포트 재생성 → " + html);
                } catch (RuntimeException e) {
                    System.err.printf("재생성 실패: %s — %s%n", dir, e.getMessage());
                }
            }
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

        // 계정: 콘솔에서 저장한 로컬 파일 우선, 없으면 환경변수(주간 예약 실행에서 콘솔 설정 재사용)
        var credResolver = new com.kt.wqg.web.CredentialStore(
                configPath.resolveSibling("credentials.properties")).resolver();
        // 검사 제외 항목(콘솔 '점검 항목'에서 저장) — CLI/예약 실행도 동일 적용
        var excluded = new com.kt.wqg.web.ItemExclusionStore(
                configPath.resolveSibling("excluded-items.txt")).excluded();

        for (SitesConfig.Site site : targets) {
            try {
                Path report = CheckService.runSite(config, site, credResolver,
                        msg -> System.out.printf("[%s] %s%n", site.name(), msg), excluded);
                System.out.printf("[%s] 검사 완료 → %s%n", site.name(), report);
            } catch (RuntimeException e) {
                // 시스템 단위 실패 격리 — 하나가 죽어도 나머지는 계속
                System.err.printf("[%s] 검사 실패: %s%n", site.name(), e.getMessage());
            }
        }
    }

    /** 폐쇄망 드라이버 진단('Failed to create driver' 원인 규명). doctor 모드 본체. */
    private static void runDoctor() {
        var out = System.out;
        out.println("===== web-quality-gate 진단(doctor) =====");
        out.println("[시스템]");
        for (String k : new String[]{"java.version", "java.vendor", "java.home",
                "os.name", "os.arch", "user.name", "java.io.tmpdir"}) {
            out.printf("  %-16s = %s%n", k, System.getProperty(k));
        }
        out.println("[환경변수]");
        for (String k : new String[]{"PLAYWRIGHT_BROWSERS_PATH", "PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD",
                "WQG_VNU_JAR", "TEMP", "TMP"}) {
            out.printf("  %-30s = %s%n", k, System.getenv(k));
        }
        String bp = System.getenv("PLAYWRIGHT_BROWSERS_PATH");
        if (bp != null) {
            Path p = Path.of(bp);
            out.printf("[브라우저 번들] %s → %s%n", p,
                    java.nio.file.Files.isDirectory(p) ? "폴더 존재" : "!! 없음");
        }

        out.println();
        out.println("[1] Playwright.create() (드라이버 생성) 시도 ...");
        try (var pw = com.microsoft.playwright.Playwright.create()) {
            out.println("  드라이버 생성 OK");
            out.println("[2] chromium 헤드리스 기동 시도 ...");
            var b = pw.chromium().launch(
                    new com.microsoft.playwright.BrowserType.LaunchOptions().setHeadless(true));
            out.printf("  chromium %s 기동 OK%n", b.version());
            b.close();
            out.println();
            out.println("===== 결과: 정상 (드라이버·브라우저 모두 OK) =====");
        } catch (Throwable t) {
            out.println("  !! 실패 — 원인 체인:");
            for (Throwable c = t; c != null; c = c.getCause()) {
                out.printf("   - %s: %s%n", c.getClass().getName(), c.getMessage());
            }
            out.println("  ---- 전체 스택트레이스 ----");
            t.printStackTrace(out);
        }

        // create 가 실패해도, node 가 추출됐으면 직접 실행해 'node 자체가 도는지'를 확정한다.
        out.println();
        out.println("[3] 추출된 node 실행 파일 직접 테스트 (node --version) ...");
        try {
            Path tmp = Path.of(System.getProperty("java.io.tmpdir"));
            boolean win = System.getProperty("os.name").toLowerCase().contains("win");
            String nodeName = win ? "node.exe" : "node";
            var nodes = new java.util.ArrayList<Path>();
            try (var top = java.nio.file.Files.list(tmp)) {
                for (Path d : top.filter(java.nio.file.Files::isDirectory)
                        .filter(x -> x.getFileName().toString().toLowerCase().contains("playwright"))
                        .toList()) {
                    try (var s = java.nio.file.Files.walk(d, 8)) {
                        s.filter(x -> x.getFileName().toString().equals(nodeName)).forEach(nodes::add);
                    } catch (Exception ignore) { /* 접근 불가 하위폴더는 건너뜀 */ }
                }
            }
            if (nodes.isEmpty()) {
                out.println("  추출된 node 를 찾지 못함(드라이버가 아직 안 풀림).");
                out.println("  → 위 [1] 오류가 추출 단계 실패입니다. TEMP 권한/백신 차단을 확인하세요.");
            }
            for (Path node : nodes) {
                out.printf("  발견: %s%n", node);
                try {
                    Process proc = new ProcessBuilder(node.toString(), "--version")
                            .redirectErrorStream(true).start();
                    String ver = new String(proc.getInputStream().readAllBytes(),
                            java.nio.charset.StandardCharsets.UTF_8).trim();
                    boolean done = proc.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
                    out.printf("    실행결과: exit=%s, 출력=\"%s\"%n",
                            done ? proc.exitValue() : "timeout(응답없음)", ver);
                    if (done && proc.exitValue() == 0) {
                        out.println("    → node 실행 정상. (드라이버 실패라면 node 외 다른 원인)");
                    }
                } catch (Exception e) {
                    out.printf("    !! node 실행 실패: %s%n", e);
                    out.println("    → 이 메시지의 'CreateProcess error=숫자'가 근본 원인입니다"
                            + " (2=경로/추출, 5=백신·권한, 193/216=손상·아키텍처, 126/127=DLL/VC++).");
                }
            }
        } catch (Exception e) {
            out.printf("  node 탐색 실패: %s%n", e);
        }
        out.println();
        out.println("===== 진단 끝 (이 로그 전체를 그대로 보내주세요) =====");
    }
}

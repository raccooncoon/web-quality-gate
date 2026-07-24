package com.kt.wqg.check;

import com.kt.wqg.config.SitesConfig;
import com.kt.wqg.report.CheckResult;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 3개 엔진(chromium/firefox/webkit)에서 동일 URL 을 스크린샷 찍어 픽셀 diff 로 렌더링 차이를 검출한다.
 * 첫 엔진을 기준(baseline)으로 나머지 엔진을 비교하며, 캡처 전 CSS 주입으로
 * animation/transition 을 꺼 캡처 순간의 흔들림을 없앤다.
 * 캡처한 스크린샷은 리포트 디렉터리에 저장하고, 사용한 엔진 버전을 함께 기록한다.
 */
public final class CompatibilityCheck {

    /** 채널당 이 값 이하 차이는 같은 픽셀로 본다 (엔진 간 안티에일리어싱 노이즈 흡수). */
    private static final int CHANNEL_TOLERANCE = 12;

    /** 캡처 직전 주입해 애니메이션·트랜지션·캐럿 깜빡임을 정지시킨다. */
    private static final String FREEZE_CSS =
            "*,*::before,*::after{animation:none!important;transition:none!important;" +
            "caret-color:transparent!important;}";

    private CompatibilityCheck() {}

    /** 호환성 검사 결과와 실제 사용한 엔진 버전. */
    public record CompatOutput(List<CheckResult> results, Map<String, String> engineVersions) {}

    /** 한 엔진의 캡처 산출물. */
    private record Capture(String version, Map<String, String> relPaths, Map<String, byte[]> bytes) {}

    /**
     * 한 사이트의 모든 URL 에 대해 엔진 간 호환성 검사를 수행한다.
     * 로그인 세션(storageState)을 엔진마다 재사용해 재로그인 없이 동일 인증 상태로 캡처한다.
     *
     * @param storageState chromium 로그인 후 추출한 세션 상태(JSON). 인증 불필요 시 null.
     * @param reportDir    리포트 디렉터리. 스크린샷은 그 아래 screenshots/ 에 저장된다.
     */
    public static CompatOutput run(Playwright playwright, SitesConfig.Site site,
                                   String timestamp, List<String> engines,
                                   double threshold, String storageState, Path reportDir) {
        Path shotsDir = reportDir.resolve("screenshots");
        try {
            Files.createDirectories(shotsDir);
        } catch (IOException e) {
            throw new RuntimeException("스크린샷 디렉터리 생성 실패", e);
        }

        Map<String, String> versions = new LinkedHashMap<>();
        Map<String, Capture> captures = new LinkedHashMap<>();
        for (String engine : engines) {
            Capture c = captureAll(playwright, engine, site, storageState, shotsDir);
            captures.put(engine, c);
            versions.put(engine, c.version());
        }

        String baseline = engines.get(0);
        List<CheckResult> results = new ArrayList<>();
        for (SitesConfig.Url url : site.urls()) {
            String fullUrl = CheckRunner.resolveUrl(site.baseUrl(), url.path());
            byte[] base = captures.get(baseline).bytes().get(url.path());
            for (String engine : engines) {
                if (engine.equals(baseline)) continue;
                double ratio = diffRatio(decode(base), decode(captures.get(engine).bytes().get(url.path())));
                boolean passed = ratio <= threshold;

                Map<String, String> shots = new LinkedHashMap<>();
                shots.put(baseline, captures.get(baseline).relPaths().get(url.path()));
                shots.put(engine, captures.get(engine).relPaths().get(url.path()));

                List<CheckResult.Violation> violations = passed ? List.of() : List.of(
                        new CheckResult.Violation("pixel-diff", "visual",
                                String.format("%s 대비 %s 렌더링 차이 %.2f%% (기준 %.2f%%)",
                                        baseline, engine, ratio * 100, threshold * 100), 1));
                results.add(new CheckResult(site.name(), fullUrl, "compatibility", timestamp,
                        engine, passed, violations, shots, ratio));
            }
        }
        return new CompatOutput(results, versions);
    }

    private static Capture captureAll(Playwright playwright, String engine,
                                      SitesConfig.Site site, String storageState, Path shotsDir) {
        Browser browser = browserType(playwright, engine)
                .launch(new BrowserType.LaunchOptions().setHeadless(true));
        Browser.NewContextOptions opts = new Browser.NewContextOptions();
        if (storageState != null) opts.setStorageState(storageState);

        Map<String, String> relPaths = new LinkedHashMap<>();
        Map<String, byte[]> bytes = new LinkedHashMap<>();
        String version = browser.version();
        try (BrowserContext context = browser.newContext(opts)) {
            Page page = context.newPage();
            for (SitesConfig.Url url : site.urls()) {
                CheckRunner.navigateAndWait(page, site, url);
                page.addStyleTag(new Page.AddStyleTagOptions().setContent(FREEZE_CSS));
                String file = engine + "-" + slug(url.path()) + ".png";
                byte[] png = page.screenshot(new Page.ScreenshotOptions()
                        .setFullPage(true).setPath(shotsDir.resolve(file)));
                bytes.put(url.path(), png);
                relPaths.put(url.path(), "screenshots/" + file);
            }
        } finally {
            browser.close();
        }
        return new Capture(version, relPaths, bytes);
    }

    /** URL 경로를 파일명에 쓸 수 있는 슬러그로. "/" → root, "/a/b" → a_b */
    static String slug(String path) {
        String s = path.replaceAll("^/+|/+$", "").replaceAll("[^a-zA-Z0-9]+", "_");
        return s.isEmpty() ? "root" : s;
    }

    private static BrowserType browserType(Playwright playwright, String engine) {
        return switch (engine) {
            case "chromium" -> playwright.chromium();
            case "firefox" -> playwright.firefox();
            case "webkit" -> playwright.webkit();
            default -> throw new IllegalArgumentException("알 수 없는 엔진: " + engine);
        };
    }

    private static BufferedImage decode(byte[] png) {
        try {
            return ImageIO.read(new ByteArrayInputStream(png));
        } catch (IOException e) {
            throw new RuntimeException("스크린샷 디코드 실패", e);
        }
    }

    /**
     * 두 이미지의 픽셀 차이 비율(0.0~1.0)을 계산한다.
     * 비교 영역은 두 이미지를 덮는 최대 크기이며, 한쪽에만 존재하는 영역(크기 불일치)도 차이로 센다.
     * 채널당 CHANNEL_TOLERANCE 이하 차이는 같은 픽셀로 본다.
     */
    static double diffRatio(BufferedImage a, BufferedImage b) {
        int w = Math.max(a.getWidth(), b.getWidth());
        int h = Math.max(a.getHeight(), b.getHeight());
        long total = (long) w * h;
        long diff = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean inA = x < a.getWidth() && y < a.getHeight();
                boolean inB = x < b.getWidth() && y < b.getHeight();
                if (inA && inB) {
                    if (pixelDiffers(a.getRGB(x, y), b.getRGB(x, y))) diff++;
                } else {
                    diff++;   // 한쪽에만 존재하는 영역 = 차이
                }
            }
        }
        return (double) diff / total;
    }

    private static boolean pixelDiffers(int rgb1, int rgb2) {
        int dr = Math.abs(((rgb1 >> 16) & 0xFF) - ((rgb2 >> 16) & 0xFF));
        int dg = Math.abs(((rgb1 >> 8) & 0xFF) - ((rgb2 >> 8) & 0xFF));
        int db = Math.abs((rgb1 & 0xFF) - (rgb2 & 0xFF));
        return dr > CHANNEL_TOLERANCE || dg > CHANNEL_TOLERANCE || db > CHANNEL_TOLERANCE;
    }
}

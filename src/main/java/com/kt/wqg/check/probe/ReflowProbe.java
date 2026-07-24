package com.kt.wqg.check.probe;

import com.kt.wqg.compliance.Finding;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.List;

/**
 * 해상도·확대 대응(c-reflow) 프로브. 1920×1080·1366×768·200% 확대에서
 * 가로 오버플로(문서 폭 > 뷰포트 폭)를 검출한다. 임계값 없이 이진 판정.
 */
public final class ReflowProbe implements PageProbe {

    /** 한 뷰포트의 측정값. scrollWidth > innerWidth 면 가로 오버플로. */
    public record Viewport(String label, int scrollWidth, int innerWidth) {}

    @Override public String id() { return "reflow-zoom"; }

    @Override
    public List<Finding> run(Page page, String url) {
        com.microsoft.playwright.options.ViewportSize orig = page.viewportSize();
        List<Viewport> measured = new ArrayList<>();
        try {
            measured.add(measure(page, url, "1920x1080", 1920, 1080, false));
            measured.add(measure(page, url, "1366x768", 1366, 768, false));
            measured.add(measure(page, url, "확대200%", 1366, 768, true));
        } finally {
            // 측정용으로 바꾼 뷰포트를 원복해 이후 URL 검사 일관성을 유지
            if (orig != null) {
                page.setViewportSize(orig.width, orig.height);
            } else {
                page.setViewportSize(1280, 720); // Playwright 기본 뷰포트
            }
        }
        return evaluate(url, measured);
    }

    private Viewport measure(Page page, String url, String label, int w, int h, boolean zoom) {
        page.setViewportSize(w, h);
        if (zoom) {
            page.evaluate("() => { document.documentElement.style.zoom = '2'; }");
        } else {
            page.evaluate("() => { document.documentElement.style.zoom = ''; }");
        }
        Object sw = page.evaluate("() => document.documentElement.scrollWidth");
        Object iw = page.evaluate("() => window.innerWidth");
        page.evaluate("() => { document.documentElement.style.zoom = ''; }");
        return new Viewport(label, ((Number) sw).intValue(), ((Number) iw).intValue());
    }

    /** 순수 판정: 가로 오버플로가 있는 뷰포트마다 Finding 을 만든다. */
    public static List<Finding> evaluate(String url, List<Viewport> measured) {
        List<Finding> out = new ArrayList<>();
        for (Viewport v : measured) {
            int over = v.scrollWidth() - v.innerWidth();
            if (over > 0) {
                out.add(new Finding(url, "reflow-zoom",
                        v.label() + "에서 가로 오버플로 " + over + "px", null));
            }
        }
        return out;
    }
}

package com.kt.wqg.compliance;

import com.kt.wqg.report.CheckResult;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ComplianceMapperTest {
    private final KwcagCatalog catalog = KwcagCatalog.load();
    private final ComplianceMapper mapper = new ComplianceMapper(catalog);

    private CheckResult a11y(String url, CheckResult.Violation... vs) {
        return new CheckResult("s", url, "accessibility", "t", null, vs.length == 0, List.of(vs));
    }
    private CheckResult markup(String url, String... helps) {
        List<CheckResult.Violation> vs = java.util.Arrays.stream(helps)
                .map(h -> new CheckResult.Violation("markup-error", "error", h, 1)).toList();
        return new CheckResult("s", url, "markup", "t", null, vs.isEmpty(), vs);
    }
    private CheckResult.Violation v(String id) {
        return new CheckResult.Violation(id, "serious", id + " 위반", 1);
    }

    private CheckResult compat(String url, String engine, boolean passed, double ratio) {
        return new CheckResult("s", url, "compatibility", "t", engine, passed,
                List.of(), null, ratio);
    }

    @Test
    void 결과맵은_37항목을_모두_채운다() {
        assertEquals(37, mapper.map(List.of()).size());
    }

    @Test
    void axe_color_contrast_위반이면_1_4_3_부적합() {
        Map<String, ItemResult> m = mapper.map(List.of(a11y("https://x/a", v("color-contrast"))));
        assertEquals(ItemVerdict.부적합, m.get("1.4.3").verdict());
        assertFalse(m.get("1.4.3").evidence().isEmpty());
    }

    @Test
    void axe_위반없는_auto항목은_적합() {
        Map<String, ItemResult> m = mapper.map(List.of(a11y("https://x/a")));
        assertEquals(ItemVerdict.적합, m.get("1.4.3").verdict());
        assertEquals(ItemVerdict.적합, m.get("1.1.1").verdict());
    }

    @Test
    void axe_대상요소_없으면_해당없음_있으면_적합() {
        // color-contrast 는 통과 요소가 잡힘(대상 있음) → 1.4.3 적합(+근거)
        // no-autoplay-audio 는 통과에 없음(오디오 0개 = inapplicable) → 1.4.2 해당없음
        CheckResult r = new CheckResult("s", "https://x/a", "accessibility", "t", null, true,
                List.of(), List.of(new CheckResult.Pass("color-contrast", 1136)));
        Map<String, ItemResult> m = mapper.map(List.of(r));
        assertEquals(ItemVerdict.적합, m.get("1.4.3").verdict());
        assertFalse(m.get("1.4.3").evidence().isEmpty());
        assertEquals(ItemVerdict.해당없음, m.get("1.4.2").verdict());
    }

    @Test
    void 마크업_오류있으면_4_1_1_부적합_c_webstandard는_수동확인() {
        Map<String, ItemResult> m = mapper.map(List.of(markup("https://x/a", "Element X is not allowed")));
        assertEquals(ItemVerdict.부적합, m.get("4.1.1").verdict()); // auto
        assertEquals(ItemVerdict.수동확인필요, m.get("c-webstandard").verdict()); // screen
    }

    @Test
    void c_deprecated는_obsolete_문구만_잡는다() {
        Map<String, ItemResult> only = mapper.map(List.of(markup("u", "The center element is obsolete")));
        assertEquals(ItemVerdict.부적합, only.get("c-deprecated").verdict());
        Map<String, ItemResult> none = mapper.map(List.of(markup("u", "Stray end tag")));
        assertEquals(ItemVerdict.적합, none.get("c-deprecated").verdict());
    }

    @Test
    void na_항목은_해당없음_probe의_auto는_매칭없으면_적합() {
        Map<String, ItemResult> m = mapper.map(List.of());
        assertEquals(ItemVerdict.해당없음, m.get("2.4.4").verdict());
        assertEquals(ItemVerdict.적합, m.get("c-browser").verdict()); // probe auto → no findings = 적합
    }

    @Test
    void probe_finding_이_probe항목에_반영된다() {
        // c-reflow(probe, auto) 는 reflow-zoom 위반이 있으면 부적합+증거
        java.util.List<Finding> pf = java.util.List.of(new Finding("https://x/a", "reflow-zoom", "가로 오버플로", null));
        Map<String, ItemResult> m = mapper.map(List.of(), pf);
        ItemResult r = m.get("c-reflow");
        assertEquals(ItemVerdict.부적합, r.verdict());
        assertFalse(r.evidence().isEmpty());
    }

    @Test
    void 호환성_실패가_있으면_c_browser_부적합() {
        Map<String, ItemResult> m = mapper.map(List.of(
                compat("https://x/a", "firefox", false, 0.08)));
        assertEquals(ItemVerdict.부적합, m.get("c-browser").verdict());
        assertFalse(m.get("c-browser").evidence().isEmpty());
    }

    @Test
    void 호환성_전부통과면_c_browser_적합() {
        Map<String, ItemResult> m = mapper.map(List.of(
                compat("https://x/a", "firefox", true, 0.0),
                compat("https://x/a", "webkit", true, 0.0)));
        assertEquals(ItemVerdict.적합, m.get("c-browser").verdict());
    }

    @Test
    void 호환성_결과없으면_c_browser_적합_처리() {
        // 호환성 미실행 시 위반 근거 없음 → 적합(자동)
        assertEquals(ItemVerdict.적합, mapper.map(List.of()).get("c-browser").verdict());
    }
}

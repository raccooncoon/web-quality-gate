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
    void na_항목은_해당없음_probe항목은_수동확인필요() {
        Map<String, ItemResult> m = mapper.map(List.of());
        assertEquals(ItemVerdict.해당없음, m.get("2.4.4").verdict());
        assertEquals(ItemVerdict.수동확인필요, m.get("c-browser").verdict()); // probe → 계획 3
    }
}

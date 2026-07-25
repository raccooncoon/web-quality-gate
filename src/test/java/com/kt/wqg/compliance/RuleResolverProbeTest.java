package com.kt.wqg.compliance;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RuleResolverProbeTest {
    private CatalogItem probe(String code, String automation, String... rules) {
        return new CatalogItem(0, code, "compatibility", "manual", automation, code, "",
                new CatalogItem.Resolver("probe", List.of(rules)),
                new CatalogItem.Guide("w","y","b","f","l"));
    }

    @Test
    void probe_screen_violation_은_부적합_증거포함() {
        // 안전 자동화: 명백한 위반(violation) finding → 부적합
        List<Finding> f = List.of(new Finding("https://x/a", "reflow-zoom", "가로 오버플로 40px", null));
        ItemResult r = RuleResolver.resolve(probe("c-reflow", "screen", "reflow-zoom"), f);
        assertEquals(ItemVerdict.부적합, r.verdict());
        assertEquals(1, r.evidence().size());
        assertEquals("reflow-zoom", r.evidence().get(0).rule());
    }

    @Test
    void probe_auto_매칭없으면_적합_있으면_부적합() {
        CatalogItem p = probe("x", "auto", "some-probe");
        assertEquals(ItemVerdict.적합, RuleResolver.resolve(p, List.of()).verdict());
        List<Finding> f = List.of(new Finding("u", "some-probe", "h", null));
        assertEquals(ItemVerdict.부적합, RuleResolver.resolve(p, f).verdict());
    }

    @Test
    void probe_manual_은_대상없으면_해당없음_review면_수동확인() {
        CatalogItem p = probe("1.3.2", "manual", "linear-order");
        assertEquals(ItemVerdict.해당없음, RuleResolver.resolve(p, List.of()).verdict());
        List<Finding> review = List.of(new Finding("u", "linear-order", "순서 확인", null, "review"));
        assertEquals(ItemVerdict.수동확인필요, RuleResolver.resolve(p, review).verdict());
    }
}

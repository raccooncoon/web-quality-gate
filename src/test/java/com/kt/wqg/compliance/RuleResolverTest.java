package com.kt.wqg.compliance;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RuleResolverTest {

    private CatalogItem axe(String code, String automation, String... rules) {
        return new CatalogItem(0, code, "accessibility", "manual", automation, code, "",
                new CatalogItem.Resolver("axe", List.of(rules)),
                new CatalogItem.Guide("w", "y", "b", "f", "l"));
    }

    @Test
    void auto_axe_매칭없으면_적합() {
        ItemResult r = RuleResolver.resolve(axe("1.4.3", "auto", "color-contrast"), List.of());
        assertEquals(ItemVerdict.적합, r.verdict());
        assertEquals("auto", r.source());
    }

    @Test
    void auto_axe_매칭있으면_부적합_증거포함() {
        List<Finding> f = List.of(new Finding("https://x/a", "color-contrast", "명도비 부족", "span.t"));
        ItemResult r = RuleResolver.resolve(axe("1.4.3", "auto", "color-contrast"), f);
        assertEquals(ItemVerdict.부적합, r.verdict());
        assertEquals(1, r.evidence().size());
        assertEquals("color-contrast", r.evidence().get(0).rule());
        assertEquals("https://x/a", r.evidence().get(0).url());
    }

    @Test
    void 다른_rule_은_매칭되지_않는다() {
        List<Finding> f = List.of(new Finding("u", "image-alt", "h", null));
        assertEquals(ItemVerdict.적합, RuleResolver.resolve(axe("1.4.3", "auto", "color-contrast"), f).verdict());
    }

    @Test
    void screen_axe_는_매칭유무와_무관하게_수동확인필요() {
        assertEquals(ItemVerdict.수동확인필요,
                RuleResolver.resolve(axe("3.3.4", "screen", "autocomplete-valid"), List.of()).verdict());
        List<Finding> f = List.of(new Finding("u", "autocomplete-valid", "h", null));
        ItemResult r = RuleResolver.resolve(axe("3.3.4", "screen", "autocomplete-valid"), f);
        assertEquals(ItemVerdict.수동확인필요, r.verdict());
        assertEquals(1, r.evidence().size(), "스크리닝 증거 첨부");
    }

    @Test
    void na_는_해당없음() {
        CatalogItem na = new CatalogItem(0, "2.4.4", "accessibility", "manual", "na", "t", "",
                new CatalogItem.Resolver("none", List.of()), new CatalogItem.Guide("w","y","b","f","l"));
        assertEquals(ItemVerdict.해당없음, RuleResolver.resolve(na, List.of()).verdict());
    }

    @Test
    void probe_나_none_타입은_수동확인필요() {
        CatalogItem probe = new CatalogItem(0, "1.2.1", "accessibility", "manual", "screen", "t", "",
                new CatalogItem.Resolver("probe", List.of("media-captions")), new CatalogItem.Guide("w","y","b","f","l"));
        assertEquals(ItemVerdict.수동확인필요, RuleResolver.resolve(probe, List.of()).verdict());
    }
}

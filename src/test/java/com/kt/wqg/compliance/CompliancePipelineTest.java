package com.kt.wqg.compliance;

import com.kt.wqg.report.CheckResult;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class CompliancePipelineTest {
    private final KwcagCatalog catalog = KwcagCatalog.load();
    private final ComplianceMapper mapper = new ComplianceMapper(catalog);
    private final ComplianceEvaluator evaluator = new ComplianceEvaluator(catalog);

    @Test
    void axe위반이_있으면_해당항목_부적합이_준수율에_반영되고_미확정으로_미완료() {
        // color-contrast 위반 1건 → 1.4.3 부적합. 나머지 axe auto 항목은 적합.
        // screen/probe/manual 은 수동확인필요 → 종합 '미완료'
        CheckResult r = new CheckResult("s", "https://x/a", "accessibility", "t", null, false,
                List.of(new CheckResult.Violation("color-contrast", "serious", "명도비", 1)));
        Map<String, ItemResult> items = mapper.map(List.of(r));
        ComplianceEvaluator.Compliance c = evaluator.evaluate(items);

        assertEquals(ItemVerdict.부적합, items.get("1.4.3").verdict());
        assertTrue(c.pendingTotal() > 0, "수동확인필요 항목이 있어 미확정");
        assertEquals("미완료", c.status());
        // 인식의 용이성(1.x) 그룹에 1.4.3 부적합이 집계됐는지
        ComplianceEvaluator.CategoryScore perceivable = c.categories().stream()
                .filter(s -> s.category().equals("1")).findFirst().orElseThrow();
        assertTrue(perceivable.부적합() >= 1);
    }
}

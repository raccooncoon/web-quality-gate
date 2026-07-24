package com.kt.wqg.check;

import com.deque.html.axecore.results.CheckedNode;
import com.deque.html.axecore.results.Rule;
import com.kt.wqg.report.CheckResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AccessibilityCheckTest {

    private Rule rule(String id, String impact, String help, int nodeCount) {
        Rule r = new Rule();
        r.setId(id);
        r.setImpact(impact);
        r.setHelp(help);
        r.setNodes(java.util.stream.IntStream.range(0, nodeCount)
                .mapToObj(i -> new CheckedNode()).map(n -> (CheckedNode) n).toList());
        return r;
    }

    @Test
    void axe_위반을_스키마_Violation으로_매핑한다() {
        List<CheckResult.Violation> violations = AccessibilityCheck.toViolations(List.of(
                rule("image-alt", "critical", "이미지에 대체텍스트 필요", 3),
                rule("color-contrast", "serious", "명암비 부족", 12)));

        assertEquals(2, violations.size());
        assertEquals("image-alt", violations.get(0).id());
        assertEquals("critical", violations.get(0).impact());
        assertEquals("이미지에 대체텍스트 필요", violations.get(0).help());
        assertEquals(3, violations.get(0).nodes());
        assertEquals(12, violations.get(1).nodes());
    }

    @Test
    void 위반이_없으면_빈_목록() {
        assertEquals(List.of(), AccessibilityCheck.toViolations(List.of()));
    }
}

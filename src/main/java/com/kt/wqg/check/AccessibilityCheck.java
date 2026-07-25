package com.kt.wqg.check;

import com.deque.html.axecore.playwright.AxeBuilder;
import com.deque.html.axecore.results.AxeResults;
import com.deque.html.axecore.results.CheckedNode;
import com.deque.html.axecore.results.Rule;
import com.kt.wqg.report.CheckResult;
import com.kt.wqg.report.KwcagMapping;
import com.microsoft.playwright.Page;

import java.util.List;

/**
 * axe-core 로 페이지 접근성을 검사한다.
 * SPA 는 readySelector 대기 + networkidle 후 분석해 빈 화면 오탐(위반 0건)을 막는다.
 */
public final class AccessibilityCheck {

    private AccessibilityCheck() {}

    /**
     * 이미 로그인된 페이지에서 url 을 열고 접근성을 분석한다.
     *
     * @param tags AxeBuilder.withTags 에 넘길 WCAG 태그 (sites.yaml checks.accessibility.tags)
     */
    public static CheckResult run(Page page, com.kt.wqg.config.SitesConfig.Site site,
                                  com.kt.wqg.config.SitesConfig.Url url,
                                  String timestamp, List<String> tags) {
        String fullUrl = CheckRunner.navigateAndWait(page, site, url);

        AxeResults results = new AxeBuilder(page).withTags(tags).analyze();
        List<CheckResult.Violation> violations = toViolations(results.getViolations());
        List<CheckResult.Pass> passes = toPasses(results.getPasses());

        return new CheckResult(site.name(), fullUrl, "accessibility", timestamp, null,
                violations.isEmpty(), violations, passes);
    }

    /** axe 통과 규칙(Rule)을 규칙 id + 검사(통과) 요소 수로 매핑한다(적합 근거용). */
    static List<CheckResult.Pass> toPasses(List<Rule> rules) {
        if (rules == null) return List.of();
        return rules.stream()
                .map(r -> new CheckResult.Pass(r.getId(), r.getNodes() == null ? 0 : r.getNodes().size()))
                .filter(p -> p.nodes() > 0)
                .toList();
    }

    /**
     * axe 위반(Rule)을 리포트 스키마 Violation 으로 매핑한다.
     * nodes 는 영향 노드 수, kwcag 는 KWCAG 2.1 병기, elements 는 문제 요소 샘플(최대 5개).
     */
    static List<CheckResult.Violation> toViolations(List<Rule> rules) {
        return rules.stream().map(r -> {
            List<CheckedNode> nodes = r.getNodes() == null ? List.of() : r.getNodes();
            List<CheckResult.Element> elements = nodes.stream()
                    .limit(5)
                    .map(n -> new CheckResult.Element(targetOf(n.getTarget()), n.getHtml()))
                    .toList();
            return new CheckResult.Violation(
                    r.getId(), r.getImpact(), r.getHelp(), nodes.size(),
                    KwcagMapping.of(r.getId()), elements.isEmpty() ? null : elements);
        }).toList();
    }

    /** axe 의 target(보통 셀렉터 리스트)을 사람이 읽을 문자열로 변환. */
    private static String targetOf(Object target) {
        if (target == null) return null;
        if (target instanceof List<?> list) {
            return list.stream().map(String::valueOf).reduce((a, b) -> a + " " + b).orElse(null);
        }
        return String.valueOf(target);
    }
}

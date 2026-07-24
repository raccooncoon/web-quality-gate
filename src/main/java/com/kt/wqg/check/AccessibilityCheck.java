package com.kt.wqg.check;

import com.deque.html.axecore.playwright.AxeBuilder;
import com.deque.html.axecore.results.AxeResults;
import com.deque.html.axecore.results.Rule;
import com.kt.wqg.report.CheckResult;
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

        return new CheckResult(site.name(), fullUrl, "accessibility", timestamp, null,
                violations.isEmpty(), violations);
    }

    /** axe 위반(Rule)을 리포트 스키마 Violation 으로 매핑한다. nodes 는 영향 노드 수. */
    static List<CheckResult.Violation> toViolations(List<Rule> rules) {
        return rules.stream()
                .map(r -> new CheckResult.Violation(
                        r.getId(), r.getImpact(), r.getHelp(),
                        r.getNodes() == null ? 0 : r.getNodes().size()))
                .toList();
    }
}

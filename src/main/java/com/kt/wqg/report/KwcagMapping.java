package com.kt.wqg.report;

import java.util.Map;

/**
 * axe-core rule id → KWCAG 2.1(한국형 웹 콘텐츠 접근성 지침) 검사항목 매핑.
 *
 * 주의: axe(WCAG 2.1 기준)와 KWCAG 는 1:1 대응이 아니므로 참고용 자동 매핑이다.
 * 공식 인증 판정이 아니라, 국내 산출물에서 위반을 KWCAG 항목으로 읽기 쉽게 병기하기 위함.
 */
public final class KwcagMapping {

    private KwcagMapping() {}

    private static final Map<String, String> MAP = Map.ofEntries(
            // 인식의 용이성
            e("image-alt", "5.1.1 적절한 대체 텍스트 제공"),
            e("input-image-alt", "5.1.1 적절한 대체 텍스트 제공"),
            e("area-alt", "5.1.1 적절한 대체 텍스트 제공"),
            e("role-img-alt", "5.1.1 적절한 대체 텍스트 제공"),
            e("svg-img-alt", "5.1.1 적절한 대체 텍스트 제공"),
            e("object-alt", "5.1.1 적절한 대체 텍스트 제공"),
            e("td-headers-attr", "5.2.1 표의 구성"),
            e("th-has-data-cells", "5.2.1 표의 구성"),
            e("scope-attr-valid", "5.2.1 표의 구성"),
            e("list", "5.2.2 콘텐츠의 선형 구조"),
            e("listitem", "5.2.2 콘텐츠의 선형 구조"),
            e("definition-list", "5.2.2 콘텐츠의 선형 구조"),
            e("dlitem", "5.2.2 콘텐츠의 선형 구조"),
            e("link-in-text-block", "5.3.1 색에 무관한 콘텐츠 인식"),
            e("color-contrast", "5.4.1 텍스트 콘텐츠의 명도 대비"),
            e("color-contrast-enhanced", "5.4.1 텍스트 콘텐츠의 명도 대비"),
            e("no-autoplay-audio", "5.4.2 자동 재생 금지"),
            // 운용의 용이성
            e("accesskeys", "6.1.1 키보드 사용 보장"),
            e("tabindex", "6.1.2 초점 이동과 표시"),
            e("focus-order-semantics", "6.1.2 초점 이동과 표시"),
            e("meta-refresh", "6.2.1 응답 시간 조절"),
            e("blink", "6.3.1 깜빡임과 번쩍임 사용 제한"),
            e("marquee", "6.3.1 깜빡임과 번쩍임 사용 제한"),
            e("bypass", "6.4.1 반복 영역 건너뛰기"),
            e("region", "6.4.1 반복 영역 건너뛰기"),
            e("landmark-one-main", "6.4.1 반복 영역 건너뛰기"),
            e("skip-link", "6.4.1 반복 영역 건너뛰기"),
            e("document-title", "6.4.2 제목 제공"),
            e("frame-title", "6.4.2 제목 제공"),
            e("empty-heading", "6.4.2 제목 제공"),
            e("heading-order", "6.4.2 제목 제공"),
            e("page-has-heading-one", "6.4.2 제목 제공"),
            e("link-name", "6.5.1 적절한 링크 텍스트 제공"),
            // 이해의 용이성
            e("html-has-lang", "7.1.1 기본 언어 표시"),
            e("html-lang-valid", "7.1.1 기본 언어 표시"),
            e("valid-lang", "7.1.1 기본 언어 표시"),
            e("label", "7.3.2 레이블 제공"),
            e("select-name", "7.3.2 레이블 제공"),
            e("button-name", "7.3.2 레이블 제공"),
            e("input-button-name", "7.3.2 레이블 제공"),
            e("aria-input-field-name", "7.3.2 레이블 제공"),
            e("form-field-multiple-labels", "7.3.2 레이블 제공"),
            e("aria-toggle-field-name", "7.3.2 레이블 제공"),
            // 견고성
            e("duplicate-id", "7.4.1 마크업 오류 방지"),
            e("duplicate-id-active", "7.4.1 마크업 오류 방지"),
            e("duplicate-id-aria", "7.4.1 마크업 오류 방지"),
            e("aria-required-attr", "7.4.1 마크업 오류 방지"),
            e("aria-valid-attr", "7.4.1 마크업 오류 방지"),
            e("aria-valid-attr-value", "7.4.1 마크업 오류 방지"),
            e("aria-roles", "7.4.1 마크업 오류 방지"),
            e("aria-allowed-attr", "7.4.1 마크업 오류 방지"),
            e("aria-allowed-role", "7.4.1 마크업 오류 방지"),
            e("aria-hidden-focus", "7.4.1 마크업 오류 방지"),
            e("aria-required-children", "7.4.1 마크업 오류 방지"),
            e("aria-required-parent", "7.4.1 마크업 오류 방지"));

    private static Map.Entry<String, String> e(String k, String v) {
        return Map.entry(k, v);
    }

    /** axe rule id 에 대응하는 KWCAG 2.1 항목 문자열. 매핑이 없으면 null. */
    public static String of(String axeRuleId) {
        return MAP.get(axeRuleId);
    }
}

package com.kt.wqg.check;

import com.kt.wqg.compliance.KwcagCatalog;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckServiceTest {

    @Test
    void 카탈로그에_compat_항목이_있으면_호환성_검사가_필요하다() {
        // 기본 카탈로그에는 c-browser(resolver.type=compat)가 있어 호환성 검사가 필요.
        assertTrue(CheckService.catalogNeeds(KwcagCatalog.load(), "compat"));
    }

    @Test
    void 호환성_항목_전부_제외되면_호환성_검사가_불필요하다() {
        // c-browser 를 제외하면 compat 타입 항목이 사라져 3엔진 캡처를 건너뛰어야 한다.
        KwcagCatalog without = KwcagCatalog.load().without(Set.of("c-browser"));
        assertFalse(CheckService.catalogNeeds(without, "compat"),
                "compat 항목 전부 제외 시 호환성 검사는 불필요해야 한다");
    }

    @Test
    void 일부_항목만_제외되면_해당_검사는_여전히_필요하다() {
        // 4.1.1 은 남기고 c-* 마크업만 제외해도 markup 타입(4.1.1)이 남아 마크업 검사는 필요.
        KwcagCatalog cat = KwcagCatalog.load().without(Set.of("c-webstandard", "c-deprecated"));
        assertTrue(CheckService.catalogNeeds(cat, "markup"),
                "4.1.1(markup)이 남아 있으면 마크업 검사는 필요해야 한다");
    }

    @Test
    void 표시명이_있으면_대상페이지_제목으로_그대로_쓴다() {
        assertEquals("홈 > 시작하기", CheckService.breadcrumb("/app/w/1/start", "홈 > 시작하기"));
    }

    @Test
    void 표시명이_없으면_경로기반_빵부스러기로_표기한다() {
        // page.title() 을 쓰지 않는다 — 경로 세그먼트만으로 "홈 > app > w > 1 > start".
        assertEquals("홈 > app > w > 1 > start", CheckService.breadcrumb("/app/w/1/start", null));
        assertEquals("홈 > app > w > 1 > start", CheckService.breadcrumb("/app/w/1/start", "   "));
    }

    @Test
    void 루트경로_표시명_없으면_홈만() {
        assertEquals("홈", CheckService.breadcrumb("/", null));
    }
}

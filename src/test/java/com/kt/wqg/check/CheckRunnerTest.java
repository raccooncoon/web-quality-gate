package com.kt.wqg.check;

import com.kt.wqg.config.SitesConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckRunnerTest {

    @Test
    void baseUrl과_path를_이어붙인다() {
        assertEquals("http://10.0.0.1/board/list",
                CheckRunner.resolveUrl("http://10.0.0.1", "/board/list"));
    }

    @Test
    void baseUrl_끝_슬래시가_중복되지_않는다() {
        assertEquals("http://10.0.0.1/board/list",
                CheckRunner.resolveUrl("http://10.0.0.1/", "/board/list"));
    }

    @Test
    void path에_앞_슬래시가_없어도_붙인다() {
        assertEquals("http://10.0.0.1/board/list",
                CheckRunner.resolveUrl("http://10.0.0.1", "board/list"));
    }

    @Test
    void 루트_경로는_슬래시_하나로_끝난다() {
        assertEquals("http://10.0.0.1/",
                CheckRunner.resolveUrl("http://10.0.0.1/", "/"));
    }

    @Test
    void 사이트명을_대문자_환경변수로_바꿔_계정을_읽는다() {
        Map<String, String> env = Map.of(
                "WQG_SITE_A_ID", "tester",
                "WQG_SITE_A_PW", "secret");

        CheckRunner.Credentials c = CheckRunner.credentialsOf("site-a", env::get);

        assertEquals("tester", c.id());
        assertEquals("secret", c.pw());
    }

    @Test
    void 계정_환경변수가_없으면_변수명을_알려주며_실패한다() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> CheckRunner.credentialsOf("site-a", key -> null));

        assertTrue(e.getMessage().contains("WQG_SITE_A_ID"),
                "누락된 환경변수명이 메시지에 있어야 한다: " + e.getMessage());
    }

    @Test
    void URL_하나가_실패해도_나머지를_계속_방문한다() {
        SitesConfig.Site site = new SitesConfig.Site("site-c", "mpa", "http://10.0.0.3", null,
                List.of(new SitesConfig.Url("/", null),
                        new SitesConfig.Url("/notice/list", null),
                        new SitesConfig.Url("/faq", null)));
        List<String> visited = new ArrayList<>();

        List<CheckRunner.UrlResult> results = CheckRunner.visitAll(site, url -> {
            visited.add(url);
            if (url.endsWith("/notice/list")) {
                throw new RuntimeException("타임아웃");
            }
        });

        assertEquals(List.of("http://10.0.0.3/", "http://10.0.0.3/notice/list", "http://10.0.0.3/faq"),
                visited, "실패한 URL 이후에도 방문이 계속돼야 한다");
        assertEquals(List.of(true, false, true), results.stream().map(CheckRunner.UrlResult::ok).toList());
    }

    @Test
    void 실패한_URL은_원인을_결과에_남긴다() {
        SitesConfig.Site site = new SitesConfig.Site("site-c", "mpa", "http://10.0.0.3", null,
                List.of(new SitesConfig.Url("/faq", null)));

        List<CheckRunner.UrlResult> results = CheckRunner.visitAll(site, url -> {
            throw new RuntimeException("타임아웃");
        });

        assertEquals(1, results.size());
        assertTrue(results.get(0).error().contains("타임아웃"),
                "실패 원인이 결과에 남아야 한다: " + results.get(0).error());
    }

    @Test
    void 로그인_후_여전히_로그인페이지면_실패로_본다() {
        assertFalse(CheckRunner.loginSucceeded(
                "https://defect.example.com/login", "https://defect.example.com/login"));
    }

    @Test
    void 로그인_후_로그인페이지를_벗어나면_성공으로_본다() {
        assertTrue(CheckRunner.loginSucceeded(
                "https://defect.example.com/login", "https://defect.example.com/dashboard"));
    }

    @Test
    void 로그인_실패로_쿼리파라미터가_붙어도_실패로_본다() {
        assertFalse(CheckRunner.loginSucceeded(
                "https://defect.example.com/login", "https://defect.example.com/login?error"));
    }

    @Test
    void 절대경로_내부링크는_경로로_수집한다() {
        assertEquals("/defects",
                CheckRunner.internalPath("https://defect.example.com", "https://defect.example.com/defects"));
    }

    @Test
    void 상대경로_내부링크도_경로로_수집한다() {
        assertEquals("/defects",
                CheckRunner.internalPath("https://defect.example.com", "/defects"));
    }

    @Test
    void 외부도메인_링크는_수집하지_않는다() {
        assertNull(CheckRunner.internalPath("https://defect.example.com", "https://google.com/"));
    }

    @Test
    void 로그아웃_링크는_수집하지_않는다() {
        assertNull(CheckRunner.internalPath("https://defect.example.com", "/logout"));
    }

    @Test
    void 앵커_mailto_javascript_링크는_수집하지_않는다() {
        assertNull(CheckRunner.internalPath("https://defect.example.com", "#top"));
        assertNull(CheckRunner.internalPath("https://defect.example.com", "mailto:a@b.com"));
        assertNull(CheckRunner.internalPath("https://defect.example.com", "javascript:void(0)"));
    }

    @Test
    void 쿼리와_프래그먼트를_떼고_경로만_남긴다() {
        assertEquals("/defects",
                CheckRunner.internalPath("https://defect.example.com", "/defects?page=2#list"));
    }
}

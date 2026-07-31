package com.kt.wqg.report;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * report.html 을 인쇄 레이아웃 그대로 PDF 로 변환하고, 여러 회차를 하나의 ZIP 으로 묶는다.
 * Playwright(chromium headless)의 page.pdf() 를 쓰며 @page/@media print CSS 를 존중한다.
 */
public final class ReportPdf {

    private ReportPdf() {}

    /** 단일 report.html → PDF 바이트(Browser 재사용). 스크린샷은 상대경로라 file:// 로 로드된다. */
    public static byte[] render(Browser browser, Path reportDir) {
        Path html = reportDir.resolve("report.html");
        if (!Files.isRegularFile(html)) throw new IllegalStateException("report.html 없음: " + reportDir);
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            page.navigate(html.toUri().toString(), new Page.NavigateOptions().setTimeout(30000));
            try {
                page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(8000));
            } catch (RuntimeException ignore) { /* 이미지 로드 지연은 무시하고 렌더 */ }
            return page.pdf(new Page.PdfOptions()
                    .setPrintBackground(true)         // 배지·표 배경색 유지
                    .setPreferCSSPageSize(true));     // @page A4 + landscape(mxland) 존중
        }
    }

    /**
     * 여러 report 디렉터리를 각각 PDF 로 만들어 하나의 ZIP(byte[])으로 묶는다.
     * entryName 은 확장자 없는 파일명을 돌려주며, 중복이면 _2, _3 … 을 붙인다. 렌더 실패한 회차는 건너뛴다.
     */
    public static byte[] bundleZip(List<Path> reportDirs, Function<Path, String> entryName) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (Playwright pw = Playwright.create()) {
            Browser browser = pw.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            try (ZipOutputStream zip = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
                Set<String> used = new HashSet<>();
                for (Path dir : reportDirs) {
                    byte[] pdf;
                    try { pdf = render(browser, dir); }
                    catch (RuntimeException e) { continue; }   // 손상·미완 회차는 제외
                    String base = entryName.apply(dir);
                    String name = base + ".pdf";
                    for (int i = 2; !used.add(name); i++) name = base + "_" + i + ".pdf";
                    zip.putNextEntry(new ZipEntry(name));
                    zip.write(pdf);
                    zip.closeEntry();
                }
            } finally { browser.close(); }
        } catch (IOException e) {
            throw new UncheckedIOException("PDF 묶음 생성 실패", e);
        }
        return baos.toByteArray();
    }
}

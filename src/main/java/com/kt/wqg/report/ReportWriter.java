package com.kt.wqg.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 검사 결과를 reports/&lt;site&gt;/&lt;yyyyMMdd-HHmm&gt;/index.json 으로 집계 저장한다.
 */
public final class ReportWriter {

    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private ReportWriter() {}

    /** 타입별 통과/실패 집계. */
    public record TypeSummary(int total, int passed, int failed) {}

    /** 전체 및 타입별 집계. */
    public record Summary(int total, int passed, int failed, Map<String, TypeSummary> byType) {}

    /** 리포트 최상위 스키마 (index.json). */
    public record Report(String site, String timestamp, Summary summary, List<CheckResult> results) {}

    /** 결과 목록을 전체·타입별로 집계한다. */
    public static Summary summarize(List<CheckResult> results) {
        int passed = (int) results.stream().filter(CheckResult::passed).count();

        Map<String, int[]> acc = new LinkedHashMap<>();  // type -> [total, passed]
        for (CheckResult r : results) {
            int[] c = acc.computeIfAbsent(r.checkType(), k -> new int[2]);
            c[0]++;
            if (r.passed()) c[1]++;
        }
        Map<String, TypeSummary> byType = new LinkedHashMap<>();
        acc.forEach((type, c) -> byType.put(type, new TypeSummary(c[0], c[1], c[0] - c[1])));

        return new Summary(results.size(), passed, results.size() - passed, byType);
    }

    /**
     * reports/&lt;site&gt;/&lt;timestamp&gt;/index.json 을 쓰고 그 경로를 반환한다.
     *
     * @param outputDir 최상위 출력 디렉터리 (sites.yaml output.dir)
     */
    public static Path write(String outputDir, String site, String timestamp,
                             List<CheckResult> results) {
        Report report = new Report(site, timestamp, summarize(results), results);
        Path dir = Path.of(outputDir, site, timestamp);
        Path index = dir.resolve("index.json");
        try {
            Files.createDirectories(dir);
            JSON.writeValue(index.toFile(), report);
            return index;
        } catch (IOException e) {
            throw new RuntimeException("리포트 저장 실패: " + index, e);
        }
    }
}

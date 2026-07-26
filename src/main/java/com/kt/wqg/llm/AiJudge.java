package com.kt.wqg.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * 수동확인 항목에 대한 LLM 초안 판정. 자동 수집한 기준·가이드·증거를 근거로
 * 적합/부적합/판단보류 초안과 이유를 생성한다. 최종 판정은 사람(평가자)이 확정한다.
 */
public final class AiJudge {

    private static final ObjectMapper JSON = new ObjectMapper();

    private AiJudge() {}

    public record Draft(String verdict, String reason, double confidence) {}

    private static final String SYSTEM = """
            당신은 한국형 웹 콘텐츠 접근성 지침(KWCAG 2.2) 전문 평가자입니다.
            주어진 '검사 항목 기준'과 자동 도구가 수집한 '증거'만을 근거로 판정합니다.
            판정값은 반드시 '적합', '부적합', '판단보류' 중 하나입니다.

            [판정 원칙 — 반드시 지킬 것]
            - 첨부된 화면에 '실제로 보이는' 것에만 근거하라. 통념·일반론으로 위반을 단정하지 말 것.
            - 위반 '사례'를 화면에서 구체적으로 지목하지 못하면 부적합으로 하지 말고 '적합' 또는 '판단보류'로 한다.
            - 요소의 '종류'를 구분하라: 버튼·탭·메뉴·툴바 액션·아이콘·배지·네비게이션 항목은 테두리·배경·모양·위치·맥락으로
              구분되므로, 색만으로 구분된다고 보지 않는다(그 자체로 위반이 아님). 각 항목 기준이 지목하는 대상에만 판정을 한정하라.
            - (예: '색에 무관한 인식/1.4.1 링크'는 문장·문단 속 '인라인 텍스트 링크'가 오직 색으로만 구분될 때만 부적합이며,
              버튼·탭·목록 액션 등은 대상이 아니다. 인라인 텍스트 링크가 화면에 없거나 밑줄 등 단서가 있으면 적합.)

            증거가 판단에 불충분하면 '판단보류'로 하고, 사람이 무엇을 확인해야 하는지 이유에 적는다.
            이유는 한국어 2~3문장, 화면에서 근거를 구체적으로 지목. 과장·추측 금지.
            출력은 오직 JSON 하나: {"verdict":"적합|부적합|판단보류","reason":"...","confidence":0.0~1.0}""";

    public static Draft judge(LlmClient llm, String code, String title, String criteria,
                              String what, String why, List<String> evidence) {
        return judge(llm, code, title, criteria, what, why, evidence, List.of());
    }

    /** imageDataUris(스크린샷)가 있으면 비전으로 화면을 함께 보고 판단한다. */
    public static Draft judge(LlmClient llm, String code, String title, String criteria,
                              String what, String why, List<String> evidence, List<String> imageDataUris) {
        boolean hasImg = imageDataUris != null && !imageDataUris.isEmpty();
        String user = "[항목] " + code + " " + title
                + "\n[세부 기준] " + n(criteria)
                + "\n[무엇을 검사] " + n(what)
                + "\n[왜 필요] " + n(why)
                + "\n[자동 수집 증거]\n" + (evidence == null || evidence.isEmpty()
                        ? "(자동 증거 없음)" : String.join("\n", evidence))
                + (hasImg ? "\n[첨부 화면] 실제 렌더링된 페이지 스크린샷을 함께 제공합니다. 화면을 직접 근거로 삼아 판단하세요."
                          : "");
        String reply = llm.chatVision(SYSTEM, user, imageDataUris, true);
        return parse(reply);
    }

    /** 모델 응답에서 JSON 을 뽑아 Draft 로. 코드펜스·잡텍스트가 섞여도 첫 {..} 를 파싱. */
    static Draft parse(String reply) {
        String s = reply == null ? "" : reply.trim();
        int a = s.indexOf('{'), b = s.lastIndexOf('}');
        if (a >= 0 && b > a) {
            try {
                JsonNode j = JSON.readTree(s.substring(a, b + 1));
                String v = normalize(j.path("verdict").asText(""));
                String reason = j.path("reason").asText("").trim();
                double conf = j.path("confidence").asDouble(0.0);
                if (!v.isEmpty()) return new Draft(v, reason.isEmpty() ? reply.strip() : reason, conf);
            } catch (Exception ignore) { /* 파싱 실패 시 아래 폴백 */ }
        }
        // JSON 이 아니면 원문을 이유로, 판단보류
        return new Draft("판단보류", s.isEmpty() ? "모델 응답 없음" : s, 0.0);
    }

    private static String normalize(String v) {
        v = v == null ? "" : v.trim();
        if (v.contains("적합") && !v.contains("부적합")) return "적합";
        if (v.contains("부적합")) return "부적합";
        if (v.contains("보류") || v.contains("불충분") || v.contains("확인")) return "판단보류";
        return "";
    }

    private static String n(String s) { return s == null ? "" : s; }
}

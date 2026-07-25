package com.kt.wqg.compliance;

/**
 * 한 URL 에서 검출된 검사 신호 한 건.
 * kind: "violation"(명백한 위반 → 부적합 기여) | "review"(대상은 있으나 품질 판단 필요 → 수동확인 기여).
 * axe·markup·기존 프로브 위반은 모두 violation. 존재-검사 프로브가 review 를 낸다.
 * 대상이 아예 없으면 프로브는 아무 Finding 도 내지 않아 상위에서 '해당없음' 으로 처리된다.
 */
public record Finding(String url, String rule, String help, String target, String kind) {

    /** 기존 호출 호환 — 위반(violation) 신호. */
    public Finding(String url, String rule, String help, String target) {
        this(url, rule, help, target, "violation");
    }

    public boolean isReview() { return "review".equals(kind); }
}

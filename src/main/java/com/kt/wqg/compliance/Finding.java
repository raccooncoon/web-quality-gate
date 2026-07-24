package com.kt.wqg.compliance;

/** 한 URL 에서 검출된 검사 신호(axe rule 위반 등) 한 건. */
public record Finding(String url, String rule, String help, String target) {}

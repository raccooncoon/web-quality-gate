package com.kt.wqg.check.probe;

import com.kt.wqg.compliance.Finding;
import com.microsoft.playwright.Page;
import java.util.List;

/** 로그인된 page 에서 특정 검사항목의 신호를 추출하는 커스텀 프로브. */
public interface PageProbe {
    String id();
    List<Finding> run(Page page, String url);
}

package com.orbitworkbench.resume.domain;

/** 五个稳定区块，顺序即 PDF 渲染顺序（13 §5）。 */
public enum ResumeSectionKey {

    BASIC_INFO("基本信息"),
    EDUCATION("教育经历"),
    WORK_EXPERIENCE("工作经历"),
    PROJECT_EXPERIENCE("项目经历"),
    SKILLS("技能标签");

    private final String heading;

    ResumeSectionKey(String heading) {
        this.heading = heading;
    }

    public String heading() {
        return heading;
    }
}

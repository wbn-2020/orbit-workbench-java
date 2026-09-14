package com.orbitworkbench.aiconnection.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 业务侧 AI 调用场景。取值必须与 {@code ai_scenario_route.scenario_code} 的 CHECK 约束一致。
 */
public enum AiScenario {

    INTERVIEW_QUESTION("面试出题"),
    INTERVIEW_REPORT("面试报告"),
    PROJECT_FACT("项目画像事实"),
    KNOWLEDGE_ANSWER("知识库问答"),
    USER_FACT("用户画像沉淀"),
    PROFILE_DIGEST("画像编译"),
    CRAFT_DISTILL("方法论提炼");

    private static final List<AiScenario> ALL = List.of(values());

    private final String label;

    AiScenario(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static List<AiScenario> all() {
        return ALL;
    }

    public static Optional<AiScenario> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(scenario -> scenario.name().equalsIgnoreCase(value.trim()))
                .findFirst();
    }
}

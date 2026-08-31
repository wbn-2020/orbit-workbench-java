package com.orbitworkbench.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * 架构约束测试：把 AGENTS.md 与架构文档里已经确认的边界固化成可执行规则。
 * 只看主代码，测试类允许为了装配而直连 mapper。
 */
@AnalyzeClasses(packages = "com.orbitworkbench",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule httpLayerNeverReachesMappers = noClasses()
            .that().resideInAnyPackage("..api..")
            .should().dependOnClassesThat().resideInAnyPackage("..infrastructure.mapper..")
            .because("HTTP 层必须经 application 服务访问持久层，否则事务与归属校验会被绕过");

    @ArchTest
    static final ArchRule businessCodeMustNotDependOnModelProviderSdks = noClasses()
            .that().resideOutsideOfPackages("..ai.infrastructure.adapter..",
                    "..aiconnection.infrastructure..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("com.openai..", "org.springframework.ai..", "io.github.sashirestella..")
            .because("业务代码不得依赖某一家模型供应商的请求或响应对象，只能通过 AiInvocation/AiStreamEvent 契约");
}

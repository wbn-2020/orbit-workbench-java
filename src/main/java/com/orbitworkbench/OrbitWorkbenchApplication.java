package com.orbitworkbench;

import com.orbitworkbench.shared.config.AgentRuntimeProperties;
import com.orbitworkbench.shared.config.CryptoProperties;
import com.orbitworkbench.shared.config.DatasetProperties;
import com.orbitworkbench.shared.config.McpProperties;
import com.orbitworkbench.shared.config.StorageProperties;
import com.orbitworkbench.shared.config.ToolRuntimeProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@MapperScan({
        "com.orbitworkbench.aiconnection.infrastructure.mapper",
        "com.orbitworkbench.agent.infrastructure.mapper",
        "com.orbitworkbench.artifact.infrastructure.mapper",
        "com.orbitworkbench.artifactexport.infrastructure.mapper",
        "com.orbitworkbench.content.infrastructure.mapper",
        "com.orbitworkbench.document.infrastructure.mapper",
        "com.orbitworkbench.dataset.infrastructure.mapper",
        "com.orbitworkbench.analysis.infrastructure.mapper",
        "com.orbitworkbench.identity.infrastructure.mapper",
        "com.orbitworkbench.mcp.infrastructure.mapper",
        "com.orbitworkbench.memory.infrastructure.mapper",
        "com.orbitworkbench.search.infrastructure.mapper",
        "com.orbitworkbench.skill.infrastructure.mapper",
        "com.orbitworkbench.statistics.infrastructure.mapper",
        "com.orbitworkbench.task.infrastructure.mapper",
        "com.orbitworkbench.tool.infrastructure.mapper",
        "com.orbitworkbench.workflow.infrastructure.mapper",
        "com.orbitworkbench.workspace.infrastructure.mapper"
})
@EnableConfigurationProperties({
        AgentRuntimeProperties.class,
        CryptoProperties.class,
        DatasetProperties.class,
        McpProperties.class,
        StorageProperties.class,
        ToolRuntimeProperties.class
})
@SpringBootApplication
public class OrbitWorkbenchApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrbitWorkbenchApplication.class, args);
    }
}

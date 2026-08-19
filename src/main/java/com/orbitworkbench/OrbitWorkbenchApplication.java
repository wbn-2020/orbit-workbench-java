package com.orbitworkbench;

import com.orbitworkbench.shared.config.AgentRuntimeProperties;
import com.orbitworkbench.shared.config.CryptoProperties;
import com.orbitworkbench.shared.config.StorageProperties;
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
        "com.orbitworkbench.document.infrastructure.mapper",
        "com.orbitworkbench.identity.infrastructure.mapper",
        "com.orbitworkbench.task.infrastructure.mapper",
        "com.orbitworkbench.workspace.infrastructure.mapper"
})
@EnableConfigurationProperties({
        AgentRuntimeProperties.class,
        CryptoProperties.class,
        StorageProperties.class
})
@SpringBootApplication
public class OrbitWorkbenchApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrbitWorkbenchApplication.class, args);
    }
}

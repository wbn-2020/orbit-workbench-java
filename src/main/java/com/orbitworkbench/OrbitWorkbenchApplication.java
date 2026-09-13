package com.orbitworkbench;

import com.orbitworkbench.shared.config.CryptoProperties;
import com.orbitworkbench.shared.config.StorageProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAsync
@EnableScheduling
// 新增业务模块必须在这里登记自己的 mapper 包，漏登记会让应用启动即 No qualifying bean。
@MapperScan({
        "com.orbitworkbench.aiconnection.infrastructure.mapper",
        "com.orbitworkbench.backup.infrastructure.mapper",
        "com.orbitworkbench.capability.infrastructure.mapper",
        "com.orbitworkbench.identity.infrastructure.mapper",
        "com.orbitworkbench.interview.infrastructure.mapper",
        "com.orbitworkbench.interviewer.infrastructure.mapper",
        "com.orbitworkbench.jobapplication.infrastructure.mapper",
        "com.orbitworkbench.jobmatch.infrastructure.mapper",
        "com.orbitworkbench.jobprofile.infrastructure.mapper",
        "com.orbitworkbench.knowledge.infrastructure.mapper",
        "com.orbitworkbench.notification.infrastructure.mapper",
        "com.orbitworkbench.practice.infrastructure.mapper",
        "com.orbitworkbench.preference.infrastructure.mapper",
        "com.orbitworkbench.project.infrastructure.mapper",
        "com.orbitworkbench.resume.infrastructure.mapper",
        "com.orbitworkbench.schedule.infrastructure.mapper",
        "com.orbitworkbench.search.infrastructure.mapper",
        "com.orbitworkbench.studyplan.infrastructure.mapper",
        "com.orbitworkbench.userfact.infrastructure.mapper",
        "com.orbitworkbench.workspace.infrastructure.mapper",
        "com.orbitworkbench.worklog.infrastructure.mapper",
        "com.orbitworkbench.learning.infrastructure.mapper",
        "com.orbitworkbench.focus.infrastructure.mapper",
        "com.orbitworkbench.workbench.infrastructure.mapper"
})
@EnableConfigurationProperties({
        CryptoProperties.class,
        StorageProperties.class
})
@SpringBootApplication
public class OrbitWorkbenchApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrbitWorkbenchApplication.class, args);
    }
}

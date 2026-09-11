package com.orbitworkbench.backup.api;

import com.orbitworkbench.backup.api.DataBackupDtos.BackupPayload;
import com.orbitworkbench.backup.api.DataBackupDtos.ClearDataRequest;
import com.orbitworkbench.backup.api.DataBackupDtos.DataOperationSummary;
import com.orbitworkbench.backup.application.DataBackupService;
import com.orbitworkbench.identity.application.OrbitUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 数据出口接口：导出备份、覆盖式导入、清空当前用户数据。 */
@RestController
@RequestMapping("/api/v1/data")
public class DataBackupController {

    private final DataBackupService service;

    public DataBackupController(DataBackupService service) {
        this.service = service;
    }

    @GetMapping("/export")
    public BackupPayload export(Authentication authentication) {
        return service.export(userId(authentication));
    }

    @PostMapping("/import")
    public DataOperationSummary restore(@RequestBody BackupPayload payload, Authentication authentication) {
        return service.restore(userId(authentication), payload);
    }

    @PostMapping("/clear")
    public DataOperationSummary clear(@RequestBody ClearDataRequest request, Authentication authentication) {
        return service.clear(userId(authentication), request == null ? null : request.confirm());
    }

    private Long userId(Authentication authentication) {
        return ((OrbitUserDetails) authentication.getPrincipal()).userId();
    }
}

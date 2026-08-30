package com.orbitworkbench.schedule.api;

import com.orbitworkbench.identity.application.OrbitUserDetails;
import com.orbitworkbench.schedule.api.ScheduleDtos.AgendaItemResponse;
import com.orbitworkbench.schedule.api.ScheduleDtos.CreateScheduleRequest;
import com.orbitworkbench.schedule.api.ScheduleDtos.ScheduleEventResponse;
import com.orbitworkbench.schedule.api.ScheduleDtos.UpdateScheduleRequest;
import com.orbitworkbench.schedule.application.ScheduleService;
import com.orbitworkbench.schedule.domain.ScheduleStatus;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/schedule")
public class ScheduleController {

    private final ScheduleService service;

    public ScheduleController(ScheduleService service) {
        this.service = service;
    }

    @GetMapping("/agenda")
    public List<AgendaItemResponse> agenda(
            @RequestParam String from,
            @RequestParam String to,
            Authentication authentication) {
        return service.agenda(userId(authentication), parse(from), parse(to));
    }

    private static Instant parse(String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "时间格式应为 ISO-8601 UTC，例如 2026-08-30T08:00:00Z");
        }
    }

    @PostMapping("/events")
    public ScheduleEventResponse create(@Valid @RequestBody CreateScheduleRequest request,
                                        Authentication authentication) {
        return service.createCustom(userId(authentication), request);
    }

    @PutMapping("/events/{id}")
    public ScheduleEventResponse update(@PathVariable Long id,
                                        @Valid @RequestBody UpdateScheduleRequest request,
                                        Authentication authentication) {
        return service.updateCustom(userId(authentication), id, request);
    }

    @PostMapping("/events/{id}/complete")
    public ScheduleEventResponse complete(@PathVariable Long id, Authentication authentication) {
        return service.setStatus(userId(authentication), id, ScheduleStatus.COMPLETED);
    }

    @PostMapping("/events/{id}/cancel")
    public ScheduleEventResponse cancel(@PathVariable Long id, Authentication authentication) {
        return service.setStatus(userId(authentication), id, ScheduleStatus.CANCELLED);
    }

    @DeleteMapping("/events/{id}")
    public void delete(@PathVariable Long id, Authentication authentication) {
        service.deleteCustom(userId(authentication), id);
    }

    private Long userId(Authentication authentication) {
        return ((OrbitUserDetails) authentication.getPrincipal()).userId();
    }
}

package com.orbitworkbench.interview.api;

import com.orbitworkbench.identity.application.OrbitUserDetails;
import com.orbitworkbench.interview.application.InterviewQuestionService;
import com.orbitworkbench.interview.application.InterviewReportService;
import com.orbitworkbench.interview.application.InterviewSessionService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/interview-sessions")
public class InterviewSessionController {

    private final InterviewSessionService service;
    private final InterviewReportService reportService;
    private final InterviewQuestionService questionService;

    public InterviewSessionController(InterviewSessionService service,
                                      InterviewReportService reportService,
                                      InterviewQuestionService questionService) {
        this.service = service;
        this.reportService = reportService;
        this.questionService = questionService;
    }

    @PostMapping
    public Object create(@Valid @RequestBody InterviewDtos.CreateSessionRequest request,
                         Authentication authentication) {
        return service.create(userId(authentication), request);
    }

    @GetMapping
    public Object list(@RequestParam(required = false) String status,
                       Authentication authentication) {
        return service.list(userId(authentication), status);
    }

    @GetMapping("/{id}")
    public InterviewDtos.SessionDetailResponse get(@PathVariable Long id, Authentication authentication) {
        return service.get(userId(authentication), id);
    }

    @PostMapping("/{id}/start")
    public InterviewDtos.SessionResponse start(@PathVariable Long id, Authentication authentication) {
        return service.start(userId(authentication), id);
    }

    @PostMapping("/{id}/pause")
    public InterviewDtos.SessionResponse pause(@PathVariable Long id, Authentication authentication) {
        return service.pause(userId(authentication), id);
    }

    @PostMapping("/{id}/resume")
    public InterviewDtos.SessionResponse resume(@PathVariable Long id, Authentication authentication) {
        return service.resume(userId(authentication), id);
    }

    @PostMapping("/{id}/cancel")
    public InterviewDtos.SessionResponse cancel(@PathVariable Long id, Authentication authentication) {
        return service.cancel(userId(authentication), id);
    }

    @PostMapping("/{id}/end")
    public InterviewDtos.SessionResponse end(@PathVariable Long id, Authentication authentication) {
        return service.end(userId(authentication), id);
    }

    @PostMapping("/{id}/turns")
    public InterviewDtos.TurnResponse addTurn(@PathVariable Long id,
                                              @Valid @RequestBody InterviewDtos.AddTurnRequest request,
                                              Authentication authentication) {
        return service.addTurn(userId(authentication), id, request);
    }

    @PostMapping("/{id}/questions/next")
    public InterviewDtos.TurnResponse nextQuestion(
            @PathVariable Long id,
            @Valid @RequestBody InterviewDtos.NextQuestionRequest request,
            Authentication authentication) {
        return questionService.next(userId(authentication), id, request);
    }

    @PostMapping(value = "/{id}/questions/next/stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public reactor.core.publisher.Flux<org.springframework.http.codec.ServerSentEvent<String>> streamQuestion(
            @PathVariable Long id,
            @Valid @RequestBody InterviewDtos.NextQuestionRequest request,
            Authentication authentication) {
        return questionService.streamNext(userId(authentication), id, request);
    }

    @PostMapping("/{id}/turns/{turnId}/answer")
    public InterviewDtos.TurnResponse submitAnswer(@PathVariable Long id,
                                                   @PathVariable Long turnId,
                                                   @Valid @RequestBody InterviewDtos.SubmitAnswerRequest request,
                                                   Authentication authentication) {
        return service.submitAnswer(userId(authentication), id, turnId, request);
    }

    @GetMapping("/{id}/report")
    public InterviewDtos.ReportStateResponse report(@PathVariable Long id, Authentication authentication) {
        return reportService.getReportState(userId(authentication), id);
    }

    @PostMapping("/{id}/report/generate")
    public InterviewDtos.ReportStateResponse generateReport(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) InterviewDtos.GenerateReportRequest request,
            Authentication authentication) {
        return reportService.generate(userId(authentication), id,
                request == null ? null : request.connectionId());
    }

    @PostMapping("/{id}/report/retry")
    public InterviewDtos.ReportStateResponse retryReport(@PathVariable Long id, Authentication authentication) {
        return reportService.retry(userId(authentication), id);
    }

    private Long userId(Authentication authentication) {
        return ((OrbitUserDetails) authentication.getPrincipal()).userId();
    }
}

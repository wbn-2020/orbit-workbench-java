package com.orbitworkbench.knowledge.api;

import com.fasterxml.jackson.databind.node.TextNode;
import com.orbitworkbench.aiconnection.application.AiScenarioExecutionService;
import com.orbitworkbench.aiconnection.application.ScenarioStreamSession;
import com.orbitworkbench.aiconnection.domain.AiScenario;
import com.orbitworkbench.identity.application.OrbitUserDetails;
import com.orbitworkbench.knowledge.application.KnowledgeBuildService;
import com.orbitworkbench.knowledge.application.KnowledgeService;
import com.orbitworkbench.knowledge.application.ProjectFactService;
import com.orbitworkbench.shared.api.ApiException;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/v1")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;
    private final KnowledgeBuildService knowledgeBuildService;
    private final ProjectFactService factService;
    private final AiScenarioExecutionService aiScenarioExecution;

    public KnowledgeController(KnowledgeService knowledgeService,
                               KnowledgeBuildService knowledgeBuildService,
                               ProjectFactService factService,
                               AiScenarioExecutionService aiScenarioExecution) {
        this.knowledgeService = knowledgeService;
        this.knowledgeBuildService = knowledgeBuildService;
        this.factService = factService;
        this.aiScenarioExecution = aiScenarioExecution;
    }

    @PostMapping("/projects/{projectId}/versions/{versionId}/knowledge/build")
    public KnowledgeDtos.BuildResultResponse build(
            @PathVariable Long projectId,
            @PathVariable Long versionId,
            Authentication authentication) {
        return knowledgeBuildService.rebuild(userId(authentication), projectId, versionId);
    }

    @PostMapping("/knowledge/ask")
    public KnowledgeDtos.AskResponse ask(
            @Valid @RequestBody KnowledgeDtos.AskRequest request,
            Authentication authentication) {
        return knowledgeService.ask(userId(authentication), request.question(), request.projectVersionId());
    }

    /**
     * 流式问答：检索准备同步完成（无命中直接 done+insufficient，不开流）；
     * 命中后 start -> delta* -> done 事件序，失败下发 error 并正常收流。
     * 流式路径不切换备用账户（与面试出题流式同决策：流已开始后切换会拼接错乱）。
     */
    @PostMapping(value = "/knowledge/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> askStream(
            @Valid @RequestBody KnowledgeDtos.AskRequest request,
            Authentication authentication) {
        Long userId = userId(authentication);
        KnowledgeService.AskStreamPreparation preparation =
                knowledgeService.prepareAsk(userId, request.question(), request.projectVersionId());
        if (preparation.insufficient()) {
            return Flux.just(sse("done",
                    "{\"insufficient\":true,\"answer\":\"资料中未找到与问题相关的内容。\"}"));
        }
        ScenarioStreamSessionHandle handle = openStream(userId, preparation);
        StringBuilder buffer = new StringBuilder();
        Flux<ServerSentEvent<String>> body = handle.deltas()
                .map((String delta) -> {
                    buffer.append(delta);
                    return sse("delta", delta);
                });
        Flux<ServerSentEvent<String>> tail = Flux.defer(() -> {
            handle.succeed(buffer.length());
            String answer = buffer.toString().trim();
            return Flux.just(sse("done",
                    "{\"insufficient\":false,\"answer\":" + jsonEscape(answer) + "}"));
        });
        return body.concatWith(tail)
                .onErrorResume((Throwable failure) -> {
                    ApiException mapped = handle.fail(asRuntime(failure), buffer.length());
                    return Flux.just(sse("error", mapped.getMessage()));
                });
    }

    private ScenarioStreamSessionHandle openStream(Long userId,
                                                   KnowledgeService.AskStreamPreparation preparation) {
        ScenarioStreamSession session = aiScenarioExecution.stream(
                AiScenario.KNOWLEDGE_ANSWER, userId, null,
                knowledgeService.answerSystemPrompt(),
                preparation.userPrompt(),
                knowledgeService.answerMaxTokens(),
                knowledgeService.answerTimeout());
        try {
            session.begin();
        } catch (ApiException exception) {
            // 开流前的拒绝（账户不可用等）：语义是「流未开始」，转为 error 事件而非 5xx 半开流。
            return new ScenarioStreamSessionHandle.Failed(exception);
        }
        return new ScenarioStreamSessionHandle.Live(session);
    }

    /** 已开流/开流失败的统一句柄，收尾语义一致。 */
    private sealed interface ScenarioStreamSessionHandle {
        Flux<String> deltas();

        void succeed(int fullTextChars);

        ApiException fail(RuntimeException failure, int partialChars);

        record Failed(ApiException rejection) implements ScenarioStreamSessionHandle {

            @Override
            public Flux<String> deltas() {
                return Flux.error(rejection);
            }

            @Override
            public void succeed(int fullTextChars) {
                // 流未开始，无审计可收
            }

            @Override
            public ApiException fail(RuntimeException failure, int partialChars) {
                return rejection;
            }
        }

        record Live(ScenarioStreamSession session)
                implements ScenarioStreamSessionHandle {

            @Override
            public Flux<String> deltas() {
                return session.deltas();
            }

            @Override
            public void succeed(int fullTextChars) {
                session.succeed(fullTextChars);
            }

            @Override
            public ApiException fail(RuntimeException failure, int partialChars) {
                return session.fail(failure, partialChars);
            }
        }
    }

    private static RuntimeException asRuntime(Throwable failure) {
        return failure instanceof RuntimeException runtime ? runtime : new IllegalStateException(failure);
    }

    private static ServerSentEvent<String> sse(String event, String data) {
        return ServerSentEvent.<String>builder().event(event).data(data).build();
    }

    private static String jsonEscape(String raw) {
        return TextNode.valueOf(raw).toString();
    }

    @PostMapping("/projects/{projectId}/versions/{versionId}/facts/generate")
    public KnowledgeDtos.FactsListResponse generateFacts(
            @PathVariable Long projectId,
            @PathVariable Long versionId,
            @Valid @RequestBody(required = false) KnowledgeDtos.GenerateFactsRequest request,
            Authentication authentication) {
        return factService.generate(userId(authentication), projectId, versionId,
                request == null ? null : request.connectionId());
    }

    @GetMapping("/projects/{projectId}/versions/{versionId}/facts")
    public KnowledgeDtos.FactsListResponse facts(
            @PathVariable Long projectId,
            @PathVariable Long versionId,
            Authentication authentication) {
        return factService.list(userId(authentication), versionId);
    }

    @PostMapping("/projects/{projectId}/versions/{versionId}/facts/{factId}/confirm")
    public KnowledgeDtos.FactResponse confirmFact(
            @PathVariable Long projectId,
            @PathVariable Long versionId,
            @PathVariable Long factId,
            @Valid @RequestBody KnowledgeDtos.ConfirmFactRequest request,
            Authentication authentication) {
        return factService.confirm(userId(authentication), projectId, versionId, factId, request);
    }

    @PostMapping("/projects/{projectId}/versions/{versionId}/facts/{factId}/archive")
    public KnowledgeDtos.FactResponse archiveFact(
            @PathVariable Long projectId,
            @PathVariable Long versionId,
            @PathVariable Long factId,
            Authentication authentication) {
        return factService.archive(userId(authentication), projectId, versionId, factId);
    }

    private Long userId(Authentication authentication) {
        return ((OrbitUserDetails) authentication.getPrincipal()).userId();
    }
}

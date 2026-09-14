package com.orbitworkbench.practice.api;

import com.orbitworkbench.craft.api.CraftDtos.CraftForWrongAnswerResponse;
import com.orbitworkbench.craft.application.WrongAnswerCraftService;
import com.orbitworkbench.identity.application.OrbitUserDetails;
import com.orbitworkbench.practice.api.PracticeDtos.AttemptRequest;
import com.orbitworkbench.practice.api.PracticeDtos.ClassificationRequest;
import com.orbitworkbench.practice.api.PracticeDtos.CreateItemRequest;
import com.orbitworkbench.practice.api.PracticeDtos.ImportResponse;
import com.orbitworkbench.practice.api.PracticeDtos.ItemDetailResponse;
import com.orbitworkbench.practice.api.PracticeDtos.ItemListResponse;
import com.orbitworkbench.practice.application.PracticeService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 错题本接口（`15` §7）。所有读写的用户边界都由条目自身的 user_id 决定，
 * 别人的条目一律 404，与报告中心同口径，不泄露存在性。
 */
@RestController
@RequestMapping("/api/v1/practice-items")
public class PracticeItemController {

    private final PracticeService service;
    private final WrongAnswerCraftService craftSuggestionService;

    public PracticeItemController(PracticeService service, WrongAnswerCraftService craftSuggestionService) {
        this.service = service;
        this.craftSuggestionService = craftSuggestionService;
    }

    @GetMapping
    public ItemListResponse list(@RequestParam(required = false) String mastery,
                                 @RequestParam(required = false) String sourceType,
                                 @RequestParam(required = false) String topic,
                                 @RequestParam(required = false) String archived,
                                 @RequestParam(required = false) String page,
                                 @RequestParam(required = false) String size,
                                 Authentication authentication) {
        return service.list(userId(authentication), mastery, sourceType, topic, archived, page, size);
    }

    @GetMapping("/summary")
    public Object summary(@RequestParam(required = false) String archived, Authentication authentication) {
        return service.summary(userId(authentication), archived);
    }

    @GetMapping("/{id}")
    public ItemDetailResponse detail(@PathVariable Long id, Authentication authentication) {
        return service.detail(userId(authentication), id);
    }

    /** V57：这道错题该用哪条套路治——纯关键词派生（复用 V51 同一张维度表），无 AI、无落库。 */
    @GetMapping("/{id}/craft-suggestion")
    public CraftForWrongAnswerResponse craftSuggestion(@PathVariable Long id, Authentication authentication) {
        return craftSuggestionService.forItem(userId(authentication), id);
    }

    @PostMapping
    public Object create(@Valid @RequestBody CreateItemRequest request, Authentication authentication) {
        return service.create(userId(authentication), request);
    }

    @PostMapping("/from-report/{sessionId}")
    public ImportResponse importFromReport(@PathVariable Long sessionId, Authentication authentication) {
        return service.importFromReport(userId(authentication), sessionId);
    }

    @PostMapping("/from-session/{sessionId}")
    public ImportResponse importFromSession(@PathVariable Long sessionId, Authentication authentication) {
        return service.importFromSession(userId(authentication), sessionId);
    }

    @PostMapping("/{id}/attempts")
    public ItemDetailResponse addAttempt(@PathVariable Long id,
                                        @Valid @RequestBody AttemptRequest request,
                                        Authentication authentication) {
        return service.addAttempt(userId(authentication), id, request);
    }

    @PostMapping("/{id}/classification")
    public Object classify(@PathVariable Long id,
                           @Valid @RequestBody ClassificationRequest request,
                           Authentication authentication) {
        return service.classify(userId(authentication), id, request);
    }

    @PostMapping("/{id}/archive")
    public Object archive(@PathVariable Long id, Authentication authentication) {
        return service.setArchived(userId(authentication), id, true);
    }

    @PostMapping("/{id}/unarchive")
    public Object unarchive(@PathVariable Long id, Authentication authentication) {
        return service.setArchived(userId(authentication), id, false);
    }

    private Long userId(Authentication authentication) {
        return ((OrbitUserDetails) authentication.getPrincipal()).userId();
    }
}

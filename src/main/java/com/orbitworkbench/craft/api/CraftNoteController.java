package com.orbitworkbench.craft.api;

import com.orbitworkbench.craft.api.CraftDtos.CraftListResponse;
import com.orbitworkbench.craft.api.CraftDtos.CraftNoteResponse;
import com.orbitworkbench.craft.api.CraftDtos.DistillCraftResponse;
import com.orbitworkbench.craft.api.CraftDtos.PinCraftRequest;
import com.orbitworkbench.craft.api.CraftDtos.SaveCraftRequest;
import com.orbitworkbench.craft.application.CraftNoteService;
import com.orbitworkbench.identity.application.OrbitUserDetails;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 可复用「本事」接口：列表、录入、编辑、确认、归档、置顶与 AI 提炼。 */
@RestController
@RequestMapping("/api/v1/crafts")
public class CraftNoteController {

    private final CraftNoteService service;

    public CraftNoteController(CraftNoteService service) {
        this.service = service;
    }

    @GetMapping
    public CraftListResponse list(Authentication authentication) {
        return new CraftListResponse(service.list(userId(authentication)));
    }

    @PostMapping
    public CraftNoteResponse create(@Valid @RequestBody SaveCraftRequest request,
                                    Authentication authentication) {
        return service.createManual(userId(authentication), request);
    }

    @PutMapping("/{id}")
    public CraftNoteResponse update(@PathVariable Long id,
                                    @Valid @RequestBody SaveCraftRequest request,
                                    Authentication authentication) {
        return service.update(userId(authentication), id, request);
    }

    @PutMapping("/{id}/confirm")
    public CraftNoteResponse confirm(@PathVariable Long id,
                                     @Valid @RequestBody SaveCraftRequest request,
                                     Authentication authentication) {
        return service.confirm(userId(authentication), id, request);
    }

    @PostMapping("/{id}/archive")
    public CraftNoteResponse archive(@PathVariable Long id, Authentication authentication) {
        return service.archive(userId(authentication), id);
    }

    @PostMapping("/{id}/pin")
    public CraftNoteResponse pin(@PathVariable Long id,
                                 @Valid @RequestBody PinCraftRequest request,
                                 Authentication authentication) {
        return service.setPinned(userId(authentication), id, request.pinned());
    }

    /** AI 提炼入口：同步调用模型，耗时可能到分钟级；连接可指定，未指定走场景路由。 */
    @PostMapping("/distill")
    public DistillCraftResponse distill(@RequestParam(required = false) Long connectionId,
                                        Authentication authentication) {
        return new DistillCraftResponse(service.distill(userId(authentication), connectionId));
    }

    private Long userId(Authentication authentication) {
        return ((OrbitUserDetails) authentication.getPrincipal()).userId();
    }
}

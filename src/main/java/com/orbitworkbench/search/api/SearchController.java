package com.orbitworkbench.search.api;

import com.orbitworkbench.identity.application.OrbitUserDetails;
import com.orbitworkbench.search.application.SearchService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final SearchService service;

    public SearchController(SearchService service) {
        this.service = service;
    }

    @GetMapping
    public SearchDtos.SearchResponse search(@RequestParam(name = "q", required = false) String query,
                                            Authentication authentication) {
        return service.search(userId(authentication), query);
    }

    private Long userId(Authentication authentication) {
        return ((OrbitUserDetails) authentication.getPrincipal()).userId();
    }
}

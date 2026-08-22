package com.orbitworkbench.search.api;

import com.orbitworkbench.search.api.SearchDtos.SearchResultResponse;
import com.orbitworkbench.search.application.SearchService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public List<SearchResultResponse> search(
            @RequestParam String q,
            @RequestParam Long workspaceId,
            @RequestParam(required = false) String types,
            @RequestParam(defaultValue = "10") int limit) {
        return searchService.search(q, workspaceId, types, limit);
    }
}

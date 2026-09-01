package com.orbitworkbench.jobmatch.api;

import com.orbitworkbench.identity.application.OrbitUserDetails;
import com.orbitworkbench.jobmatch.api.JobMatchDtos.CreatePostingRequest;
import com.orbitworkbench.jobmatch.api.JobMatchDtos.JdVersionRequest;
import com.orbitworkbench.jobmatch.api.JobMatchDtos.MatchListResponse;
import com.orbitworkbench.jobmatch.api.JobMatchDtos.MatchViewResponse;
import com.orbitworkbench.jobmatch.api.JobMatchDtos.MetaRequest;
import com.orbitworkbench.jobmatch.api.JobMatchDtos.PostingDetailResponse;
import com.orbitworkbench.jobmatch.api.JobMatchDtos.PostingListResponse;
import com.orbitworkbench.jobmatch.api.JobMatchDtos.SaveMatchRequest;
import com.orbitworkbench.jobmatch.application.JobMatchService;
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

/**
 * 岗位与 JD 匹配接口（`17` §8）。所有读写的用户边界都是岗位自身的 user_id，
 * 别人的岗位一律 404，不泄露存在性；这里的 {@code q} 只过滤用户自己录入的岗位，
 * 不是需求排除的「岗位搜索」（`11` §5.4）。
 */
@RestController
@RequestMapping("/api/v1/job-postings")
public class JobPostingController {

    private final JobMatchService service;

    public JobPostingController(JobMatchService service) {
        this.service = service;
    }

    @GetMapping
    public PostingListResponse list(@RequestParam(required = false) String archived,
                                    @RequestParam(required = false) String q,
                                    @RequestParam(required = false) String page,
                                    @RequestParam(required = false) String size,
                                    Authentication authentication) {
        return service.list(userId(authentication), archived, q, page, size);
    }

    @PostMapping
    public PostingDetailResponse create(@Valid @RequestBody CreatePostingRequest request,
                                        Authentication authentication) {
        return service.create(userId(authentication), request);
    }

    @GetMapping("/{id}")
    public PostingDetailResponse detail(@PathVariable Long id, Authentication authentication) {
        return service.detail(userId(authentication), id);
    }

    @PutMapping("/{id}/meta")
    public PostingDetailResponse updateMeta(@PathVariable Long id,
                                            @Valid @RequestBody MetaRequest request,
                                            Authentication authentication) {
        return service.updateMeta(userId(authentication), id, request);
    }

    @PutMapping("/{id}/jd")
    public PostingDetailResponse saveJdVersion(@PathVariable Long id,
                                               @Valid @RequestBody JdVersionRequest request,
                                               Authentication authentication) {
        return service.saveJdVersion(userId(authentication), id, request);
    }

    @PostMapping("/{id}/active-version")
    public PostingDetailResponse setActiveVersion(@PathVariable Long id,
                                                  @Valid @RequestBody JobMatchDtos.ActiveVersionRequest request,
                                                  Authentication authentication) {
        return service.setActiveVersion(userId(authentication), id, request.versionId());
    }

    @PostMapping("/{id}/archive")
    public PostingDetailResponse archive(@PathVariable Long id, Authentication authentication) {
        return service.setArchived(userId(authentication), id, true);
    }

    @PostMapping("/{id}/unarchive")
    public PostingDetailResponse unarchive(@PathVariable Long id, Authentication authentication) {
        return service.setArchived(userId(authentication), id, false);
    }

    @GetMapping("/{id}/match")
    public MatchViewResponse match(@PathVariable Long id,
                                   @RequestParam(required = false) Long resumeVersionId,
                                   Authentication authentication) {
        return service.match(userId(authentication), id, resumeVersionId);
    }

    @PostMapping("/{id}/matches")
    public MatchViewResponse saveMatch(@PathVariable Long id,
                                       @RequestBody(required = false) SaveMatchRequest request,
                                       Authentication authentication) {
        return service.saveMatch(userId(authentication), id,
                request == null ? null : request.resumeVersionId());
    }

    @GetMapping("/{id}/matches")
    public MatchListResponse matches(@PathVariable Long id,
                                     @RequestParam(required = false) String page,
                                     @RequestParam(required = false) String size,
                                     Authentication authentication) {
        return service.matches(userId(authentication), id, page, size);
    }

    private Long userId(Authentication authentication) {
        return ((OrbitUserDetails) authentication.getPrincipal()).userId();
    }
}

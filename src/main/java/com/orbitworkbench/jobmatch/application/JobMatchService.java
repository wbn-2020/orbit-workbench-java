package com.orbitworkbench.jobmatch.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.orbitworkbench.jobmatch.api.JobMatchDtos.ApplicationRef;
import com.orbitworkbench.jobmatch.api.JobMatchDtos.CountsResponse;
import com.orbitworkbench.jobmatch.api.JobMatchDtos.CreatePostingRequest;
import com.orbitworkbench.jobmatch.api.JobMatchDtos.EvidenceResponse;
import com.orbitworkbench.jobmatch.api.JobMatchDtos.JdVersionRequest;
import com.orbitworkbench.jobmatch.api.JobMatchDtos.MatchHistoryResponse;
import com.orbitworkbench.jobmatch.api.JobMatchDtos.MatchListResponse;
import com.orbitworkbench.jobmatch.api.JobMatchDtos.MatchViewResponse;
import com.orbitworkbench.jobmatch.api.JobMatchDtos.MetaRequest;
import com.orbitworkbench.jobmatch.api.JobMatchDtos.PostingDetailResponse;
import com.orbitworkbench.jobmatch.api.JobMatchDtos.PostingListResponse;
import com.orbitworkbench.jobmatch.api.JobMatchDtos.PostingSummaryResponse;
import com.orbitworkbench.jobmatch.api.JobMatchDtos.RequirementPayload;
import com.orbitworkbench.jobmatch.api.JobMatchDtos.RequirementResponse;
import com.orbitworkbench.jobmatch.api.JobMatchDtos.RequirementResultResponse;
import com.orbitworkbench.jobmatch.api.JobMatchDtos.ResumeRef;
import com.orbitworkbench.jobmatch.api.JobMatchDtos.ScoringResponse;
import com.orbitworkbench.jobmatch.api.JobMatchDtos.VersionSummaryResponse;
import com.orbitworkbench.jobmatch.domain.JobMatchResultRecord;
import com.orbitworkbench.jobmatch.domain.JobMatchRow;
import com.orbitworkbench.jobmatch.domain.JobPostingListRow;
import com.orbitworkbench.jobmatch.domain.JobPostingRecord;
import com.orbitworkbench.jobmatch.domain.JobPostingVersionRecord;
import com.orbitworkbench.jobmatch.domain.MatchConfirmation;
import com.orbitworkbench.jobmatch.domain.MatchVerdict;
import com.orbitworkbench.jobmatch.domain.RequirementCategory;
import com.orbitworkbench.jobmatch.infrastructure.mapper.JobMatchResultMapper;
import com.orbitworkbench.jobmatch.infrastructure.mapper.JobPostingMapper;
import com.orbitworkbench.jobapplication.application.JobApplicationService;
import com.orbitworkbench.jobapplication.api.JobApplicationDtos.ApplicationResponse;
import com.orbitworkbench.jobprofile.application.JobProfileService;
import com.orbitworkbench.jobprofile.api.JobProfileDtos.JobProfileResponse;
import com.orbitworkbench.jobprofile.api.JobProfileDtos.JobProfileStateResponse;
import com.orbitworkbench.resume.api.ResumeDtos.ItemResponse;
import com.orbitworkbench.resume.api.ResumeDtos.SectionResponse;
import com.orbitworkbench.resume.api.ResumeDtos.VersionDetail;
import com.orbitworkbench.resume.api.ResumeDtos.ResumeStateResponse;
import com.orbitworkbench.resume.application.ResumeService;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 岗位与 JD 匹配（C-05，口径见 `17` §5–§8）。
 *
 * <p>两条贯穿全类的约束：判定只陈述「这一版简历文本里找不找得到这条要求的字样」，
 * 不陈述能力与录用可能；以及四个匹配分数列一律留 {@code null}（ADR-0011），
 * 因为本期没有任何可量化的经验/项目输入，填数字就是编。
 */
@Service
public class JobMatchService {

    /** 对照规则版本：改变归一化、区块映射或归类规则都必须推进它（同 D-043 的教训，跨版本结论不可比较）。 */
    public static final String MATCH_RULE_VERSION = "match-1";
    public static final int MAX_REQUIREMENTS = 30;
    public static final int MAX_REQUIREMENT_CHARS = 128;
    public static final int MAX_JD_CHARS = 20000;
    /** 单条要求最多展示这么多处命中依据；总数仍随 evidenceCount 下发，界面据此说明「还有 N 处未显示」。 */
    public static final int MAX_EVIDENCES = 3;

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;
    private static final int SNIPPET_RADIUS = 40;
    /** 「未评分」的说明句子：响应里以 scoring.enabled=false 显式表达，而不是让键静默缺失。 */
    private static final String SCORING_REASON =
            "本期没有可量化的经验与项目输入（求职档案只有档位、简历经历是自由文本），未定义评分口径，因此不产出 0-100 匹配分。";

    private static final Map<String, String> SECTION_LABELS = Map.of(
            "BASIC_INFO", "基本信息",
            "EDUCATION", "教育经历",
            "WORK_EXPERIENCE", "工作经历",
            "PROJECT_EXPERIENCE", "项目经历",
            "SKILLS", "技能");

    private final JobPostingMapper postingMapper;
    private final JobMatchResultMapper matchMapper;
    private final ResumeService resumeService;
    private final JobApplicationService applicationService;
    private final JobProfileService jobProfileService;
    private final ObjectMapper objectMapper;

    public JobMatchService(JobPostingMapper postingMapper,
                           JobMatchResultMapper matchMapper,
                           ResumeService resumeService,
                           JobApplicationService applicationService,
                           JobProfileService jobProfileService,
                           ObjectMapper objectMapper) {
        this.postingMapper = postingMapper;
        this.matchMapper = matchMapper;
        this.resumeService = resumeService;
        this.applicationService = applicationService;
        this.jobProfileService = jobProfileService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public PostingListResponse list(Long userId, String archived, String keyword, String page, String size) {
        Boolean archivedValue = parseArchived(archived);
        int pageNumber = parsePositive(page, 1, "page");
        int pageSize = Math.min(parsePositive(size, DEFAULT_SIZE, "size"), MAX_SIZE);
        String trimmed = keyword == null || keyword.isBlank() ? null : keyword.trim();
        long total = postingMapper.countByUser(userId, archivedValue, trimmed);
        List<PostingSummaryResponse> items = total == 0
                ? List.of()
                : postingMapper.listByUser(userId, archivedValue, trimmed, pageSize, (pageNumber - 1) * pageSize)
                        .stream()
                        .map(this::summary)
                        .toList();
        return new PostingListResponse(items, total, pageNumber, pageSize, MAX_REQUIREMENTS, MAX_JD_CHARS);
    }

    @Transactional
    public PostingDetailResponse create(Long userId, CreatePostingRequest request) {
        List<Requirement> requirements = parseRequirements(request.requirements());
        String jdText = requireText(request.jdText(), MAX_JD_CHARS, "jdText");
        validateApplication(userId, request.applicationId());
        Instant now = Instant.now();
        JobPostingRecord posting = new JobPostingRecord();
        posting.setUserId(userId);
        posting.setApplicationId(request.applicationId());
        posting.setCompany(requireText(request.company(), 128, "company"));
        posting.setTitle(requireText(request.title(), 128, "title"));
        posting.setCity(trimToLength(request.city(), 64, "city"));
        posting.setSalaryNote(trimToLength(request.salaryNote(), 128, "salaryNote"));
        posting.setSource(trimToLength(request.source(), 64, "source"));
        posting.setActiveVersionId(null);
        posting.setArchived(false);
        posting.setCreatedAt(now);
        posting.setUpdatedAt(now);
        postingMapper.insert(posting);
        // 两张表互为外键，必须三步走完（`17` §6）：只插岗位不插版本会留下一个没有活动版本的岗位。
        JobPostingVersionRecord version = newVersion(posting.getId(), 1, jdText, requirements, now);
        postingMapper.insertVersion(version);
        postingMapper.updateActiveVersion(posting.getId(), userId, version.getId(), now);
        return detail(userId, posting.getId());
    }

    @Transactional(readOnly = true)
    public PostingDetailResponse detail(Long userId, Long id) {
        JobPostingRecord posting = owned(userId, id);
        List<JobPostingVersionRecord> versions = postingMapper.listVersions(id);
        JobPostingVersionRecord active = posting.getActiveVersionId() == null
                ? null
                : postingMapper.findVersionOfPosting(id, posting.getActiveVersionId());
        List<Requirement> requirements = active == null
                ? List.of()
                : readRequirements(active.getRequiredSkillsJson());
        List<VersionSummaryResponse> versionViews = versions.stream()
                .map(version -> new VersionSummaryResponse(version.getId(), version.getVersionNumber(),
                        version.getRuleVersion(),
                        readRequirements(version.getRequiredSkillsJson()).size(),
                        version.getJdText() == null ? 0 : version.getJdText().length(),
                        active != null && active.getId().equals(version.getId()),
                        version.getCreatedAt()))
                .toList();
        return new PostingDetailResponse(posting.getId(), posting.getCompany(), posting.getTitle(),
                posting.getCity(), posting.getSalaryNote(), posting.getSource(),
                posting.isArchived(), posting.getCreatedAt(), posting.getUpdatedAt(),
                applicationRef(userId, posting.getApplicationId()),
                posting.getActiveVersionId(),
                active == null ? null : active.getJdText(),
                requirements.stream()
                        .map(item -> new RequirementResponse(item.id(), item.category().name(),
                                item.category().displayName(), item.text()))
                        .toList(),
                active == null ? null : active.getRuleVersion(),
                versionViews,
                new ScoringResponse(false, SCORING_REASON),
                SCORING_REASON);
    }

    @Transactional
    public PostingDetailResponse updateMeta(Long userId, Long id, MetaRequest request) {
        JobPostingRecord posting = owned(userId, id);
        validateApplication(userId, request.applicationId());
        Instant updatedAt = Instant.now();
        int updated = postingMapper.updateMeta(id, userId,
                request.applicationId(),
                requireText(request.company(), 128, "company"),
                requireText(request.title(), 128, "title"),
                trimToLength(request.city(), 64, "city"),
                trimToLength(request.salaryNote(), 128, "salaryNote"),
                trimToLength(request.source(), 64, "source"),
                parseInstant(request.expectedUpdatedAt(), posting.getUpdatedAt()),
                updatedAt);
        if (updated != 1) {
            throw conflict("岗位信息已被修改，请刷新后重试");
        }
        return detail(userId, id);
    }

    /**
     * 改 JD 正文或改要求清单都产出一个新版本；旧版本保持不可变，否则历史匹配记录引用的版本号会指向
     * 一份已经不存在的内容（`17` §6）。两者都没变时拒绝，而不是攒一个空版本。
     */
    @Transactional
    public PostingDetailResponse saveJdVersion(Long userId, Long id, JdVersionRequest request) {
        JobPostingRecord posting = owned(userId, id);
        List<Requirement> requirements = parseRequirements(request.requirements());
        String jdText = requireText(request.jdText(), MAX_JD_CHARS, "jdText");
        Integer max = postingMapper.maxVersionNumber(id);
        int nextNumber = (max == null ? 0 : max) + 1;
        JobPostingVersionRecord current = posting.getActiveVersionId() == null
                ? null
                : postingMapper.findVersionOfPosting(id, posting.getActiveVersionId());
        if (current != null
                && normalize(current.getJdText()).text().equals(normalize(jdText).text())
                && canonical(readRequirements(current.getRequiredSkillsJson())).equals(canonical(requirements))) {
            throw conflict("JD 正文与要求清单都没有变化，不生成新版本");
        }
        Instant now = Instant.now();
        JobPostingVersionRecord version = newVersion(id, nextNumber, jdText, requirements, now);
        postingMapper.insertVersion(version);
        postingMapper.updateActiveVersion(id, userId, version.getId(), now);
        return detail(userId, id);
    }

    @Transactional
    public PostingDetailResponse setActiveVersion(Long userId, Long id, Long versionId) {
        JobPostingRecord posting = owned(userId, id);
        JobPostingVersionRecord version = postingMapper.findVersionOfPosting(id, versionId);
        if (version == null) {
            throw invalid("版本不存在或不属于这个岗位");
        }
        postingMapper.updateActiveVersion(id, userId, versionId, Instant.now());
        return detail(userId, posting.getId());
    }

    @Transactional
    public PostingDetailResponse setArchived(Long userId, Long id, boolean archived) {
        owned(userId, id);
        postingMapper.updateArchived(id, userId, archived, Instant.now());
        return detail(userId, id);
    }

    /** 实时对照：不落库，回答「按现在的材料判成什么样」（`17` §5）。 */
    @Transactional(readOnly = true)
    public MatchViewResponse match(Long userId, Long id, Long resumeVersionId) {
        JobPostingRecord posting = owned(userId, id);
        JobPostingVersionRecord version = requireActiveVersion(posting);
        MatchOutcome outcome = compute(userId, version, resumeVersionId);
        return view(posting, version, outcome, null, null, null, null);
    }

    /**
     * 保存一次对照结果。结论由服务端**重新计算**，不接受前端回传——否则任何人都能把判定写成想要的样子，
     * 这条链路留下的快照也就没有意义了（`17` §6）。
     */
    @Transactional
    public MatchViewResponse saveMatch(Long userId, Long id, Long resumeVersionId) {
        JobPostingRecord posting = owned(userId, id);
        JobPostingVersionRecord version = requireActiveVersion(posting);
        List<Requirement> requirements = readRequirements(version.getRequiredSkillsJson());
        if (requirements.isEmpty()) {
            throw conflict("这个岗位还没有录入 JD 要求，先添加要求再保存匹配结果");
        }
        MatchOutcome outcome = compute(userId, version, resumeVersionId);
        Instant now = Instant.now();
        JobMatchResultRecord record = new JobMatchResultRecord();
        record.setUserId(userId);
        record.setJobPostingVersionId(version.getId());
        record.setResumeVersionId(outcome.resumeVersionId());
        record.setProfileSnapshotJson(outcome.profileSnapshotJson());
        record.setRuleVersion(MATCH_RULE_VERSION);
        // 分数列留空：见类注释与 ADR-0011。
        record.setTotalScore(null);
        record.setSkillScore(null);
        record.setExperienceScore(null);
        record.setProjectScore(null);
        record.setMatchedJson(writeMatchedItems(outcome.items()));
        record.setGapsJson(writeUnresolvedItems(outcome.items()));
        record.setConfirmationStatus(MatchConfirmation.PENDING);
        record.setConfirmedAt(null);
        record.setCreatedAt(now);
        matchMapper.insert(record);
        return view(posting, version, outcome, record.getId(), MatchConfirmation.PENDING, null, now);
    }

    @Transactional(readOnly = true)
    public MatchListResponse matches(Long userId, Long id, String page, String size) {
        owned(userId, id);
        int pageNumber = parsePositive(page, 1, "page");
        int pageSize = Math.min(parsePositive(size, DEFAULT_SIZE, "size"), MAX_SIZE);
        long total = matchMapper.countByPosting(userId, id);
        List<MatchHistoryResponse> items = total == 0
                ? List.of()
                : matchMapper.listByPosting(userId, id, pageSize, (pageNumber - 1) * pageSize)
                        .stream()
                        .map(this::history)
                        .toList();
        return new MatchListResponse(items, total, pageNumber, pageSize);
    }

    /** 已保存的匹配读回当时存下的结论，不重新计算——重算会把「当时」说成「现在」。 */
    @Transactional(readOnly = true)
    public MatchViewResponse matchDetail(Long userId, Long matchId) {
        JobMatchRow row = ownedMatch(userId, matchId);
        JobPostingVersionRecord version = postingMapper.findVersion(row.getJobPostingVersionId());
        if (version == null) {
            throw notFound("匹配结果引用的 JD 版本已不存在");
        }
        JobPostingRecord posting = postingMapper.findById(version.getJobPostingId());
        if (posting == null || !posting.getUserId().equals(userId)) {
            throw notFound("匹配结果不存在");
        }
        List<RequirementResultResponse> items = readStoredItems(row.getMatchedJson(), row.getGapsJson());
        CountsResponse counts = counts(items);
        ResumeRef resume = row.getResumeVersionId() == null
                ? null
                : new ResumeRef(row.getResumeVersionId(), row.getResumeVersionNumber(), row.getResumeStatus());
        return new MatchViewResponse(row.getMatchId(), posting.getId(), posting.getCompany(), posting.getTitle(),
                version.getId(), version.getVersionNumber(), row.getRuleVersion(),
                resume, resume == null ? null : "SAVED", profileSnapshotAt(row.getProfileSnapshotJson()),
                counts, new ScoringResponse(false, SCORING_REASON),
                row.getConfirmationStatus() == null ? null : row.getConfirmationStatus().name(),
                row.getConfirmedAt(), row.getCreatedAt(), items);
    }

    @Transactional
    public MatchViewResponse confirm(Long userId, Long matchId, String status, String expectedStatus) {
        JobMatchRow row = ownedMatch(userId, matchId);
        MatchConfirmation target = parseEnum(MatchConfirmation.class, status, "status");
        if (row.getConfirmationStatus() == target) {
            return matchDetail(userId, matchId);
        }
        MatchConfirmation expected = expectedStatus == null || expectedStatus.isBlank()
                ? null
                : parseEnum(MatchConfirmation.class, expectedStatus, "expectedStatus");
        if (expected != null && expected != row.getConfirmationStatus()) {
            throw conflict("这条匹配结果已在别处改过确认状态，请刷新后重试");
        }
        int updated = matchMapper.updateConfirmation(matchId, userId, target, expected, Instant.now());
        if (updated != 1) {
            throw conflict("这条匹配结果已在别处改过确认状态，请刷新后重试");
        }
        return matchDetail(userId, matchId);
    }

    // ---------- 对照计算 ----------

    private MatchOutcome compute(Long userId, JobPostingVersionRecord version, Long requestedResumeVersionId) {
        List<Requirement> requirements = readRequirements(version.getRequiredSkillsJson());
        ResumeChoice choice = chooseResume(userId, requestedResumeVersionId);
        List<RequirementResultResponse> items = new ArrayList<>();
        for (Requirement requirement : requirements) {
            items.add(judge(requirement, choice));
        }
        JobProfileStateResponse profile = jobProfileService.get(userId);
        String snapshotJson = writeProfileSnapshot(profile);
        Instant snapshotAt = profile.profile() == null ? null : profile.profile().updatedAt();
        return new MatchOutcome(items,
                choice == null ? null : choice.detail().id(),
                choice == null ? null : choice.detail().versionNumber(),
                choice == null ? null : choice.detail().status(),
                choice == null ? null : choice.source(),
                snapshotJson,
                snapshotAt);
    }

    private RequirementResultResponse judge(Requirement requirement, ResumeChoice choice) {
        String needle = normalize(requirement.text()).text();
        if (needle.isEmpty()) {
            return result(requirement, MatchVerdict.NEED_CONFIRMATION,
                    "这条要求去掉空白与不可见字符后没有可比对内容，规则判不了。", List.of());
        }
        if (requirement.category() == RequirementCategory.OTHER) {
            return result(requirement, MatchVerdict.NEED_CONFIRMATION,
                    "「其他」类要求不参与文本对照：技能、经验、项目之外的表述无法用文本比对判定。", List.of());
        }
        if (choice == null) {
            return result(requirement, MatchVerdict.NEED_CONFIRMATION,
                    "还没有可比对的简历版本，任何条目都判不了。先在简历工作台建一版简历再来对照。", List.of());
        }
        List<EvidenceResponse> evidences = new ArrayList<>();
        int hits = 0;
        int populatedSections = 0;
        List<String> emptySectionLabels = new ArrayList<>();
        for (String sectionKey : requirement.category().sectionKeys()) {
            List<ItemResponse> sectionItems = itemsOf(choice.detail(), sectionKey);
            if (sectionItems.isEmpty()) {
                emptySectionLabels.add(SECTION_LABELS.getOrDefault(sectionKey, sectionKey));
                continue;
            }
            populatedSections++;
            for (ItemResponse item : sectionItems) {
                EvidenceResponse evidence = evidenceIn(item, sectionKey, needle);
                if (evidence != null) {
                    hits++;
                    if (evidences.size() < MAX_EVIDENCES) {
                        evidences.add(evidence);
                    }
                }
            }
        }
        if (hits > 0) {
            RequirementResultResponse matched = result(requirement, MatchVerdict.MATCHED,
                    "在这一版简历的文本里找到了这条要求的字样。", evidences);
            return withEvidenceCount(matched, hits);
        }
        if (populatedSections == 0) {
            return result(requirement, MatchVerdict.NEED_CONFIRMATION,
                    "这一版简历的「" + String.join("」「", emptySectionLabels) + "」区块没有内容，没有可比对的材料。",
                    List.of());
        }
        return result(requirement, MatchVerdict.GAP,
                "这一版简历文本里没有这条要求的字样——只说明文本里找不到，不说明不具备这项能力。", List.of());
    }

    private static EvidenceResponse evidenceIn(ItemResponse item, String sectionKey, String needle) {
        Hay label = normalize(item.label());
        if (label.text().contains(needle)) {
            return new EvidenceResponse(sectionKey, SECTION_LABELS.getOrDefault(sectionKey, sectionKey),
                    item.id(), item.label(), label.snippetAt(label.text().indexOf(needle), needle.length()));
        }
        Hay text = normalize(item.text());
        if (text.text().contains(needle)) {
            return new EvidenceResponse(sectionKey, SECTION_LABELS.getOrDefault(sectionKey, sectionKey),
                    item.id(), item.label(), text.snippetAt(text.text().indexOf(needle), needle.length()));
        }
        return null;
    }

    private static List<ItemResponse> itemsOf(VersionDetail detail, String sectionKey) {
        if (detail.sections() == null) {
            return List.of();
        }
        for (SectionResponse section : detail.sections()) {
            if (sectionKey.equals(section.key())) {
                return section.items() == null ? List.of() : section.items();
            }
        }
        return List.of();
    }

    /**
     * 选哪一版简历来比：显式指定 &gt; 当前生效版本 &gt; 唯一草稿。缺省不报错，
     * 因为「没有可比对材料」本身就是 `17` §5.3 要如实呈现的一种结论。
     */
    private ResumeChoice chooseResume(Long userId, Long requestedVersionId) {
        if (requestedVersionId != null) {
            return new ResumeChoice(resumeService.detail(userId, requestedVersionId), "REQUESTED");
        }
        ResumeStateResponse state = resumeService.state(userId);
        if (!state.exists()) {
            return null;
        }
        if (state.activeVersionId() != null) {
            return new ResumeChoice(resumeService.detail(userId, state.activeVersionId()), "ACTIVE");
        }
        if (state.draft() != null) {
            return new ResumeChoice(state.draft(), "DRAFT");
        }
        return null;
    }

    // ---------- JSON 承载 ----------

    /** {@code required_skills_json} 存裸数组，列表页的 {@code JSON_LENGTH} 才直接就是要求条数。 */
    private String writeRequirements(List<Requirement> requirements) {
        ArrayNode array = objectMapper.createArrayNode();
        for (Requirement requirement : requirements) {
            ObjectNode node = array.addObject();
            node.put("id", requirement.id());
            node.put("category", requirement.category().name());
            node.put("text", requirement.text());
        }
        return array.toString();
    }

    private List<Requirement> readRequirements(String json) {
        List<Requirement> requirements = new ArrayList<>();
        for (JsonNode node : readArray(json)) {
            RequirementCategory category = parseEnum(RequirementCategory.class,
                    node.path("category").asText(null), "category");
            requirements.add(new Requirement(node.path("id").asText(null), category,
                    node.path("text").asText("")));
        }
        return requirements;
    }

    private String canonical(List<Requirement> requirements) {
        return writeRequirements(requirements);
    }

    private String writeMatchedItems(List<RequirementResultResponse> items) {
        ArrayNode array = objectMapper.createArrayNode();
        for (RequirementResultResponse item : items) {
            if (!MatchVerdict.MATCHED.name().equals(item.verdict())) {
                continue;
            }
            ObjectNode node = array.addObject();
            fillIdentity(node, item);
            node.put("verdict", item.verdict());
            node.put("reason", item.reason());
            ArrayNode evidenceArray = node.putArray("evidences");
            for (EvidenceResponse evidence : item.evidences()) {
                ObjectNode child = evidenceArray.addObject();
                child.put("sectionKey", evidence.sectionKey());
                child.put("itemId", evidence.itemId());
                child.put("itemLabel", evidence.itemLabel());
                child.put("snippet", evidence.snippet());
            }
            node.put("evidenceCount", item.evidenceCount());
        }
        return array.toString();
    }

    /**
     * 待补齐与需人工确认共用 {@code gaps_json}：V28 只有 {@code matched_json}/{@code gaps_json} 两个清单列，
     * 三栏因此在存储层靠 {@code verdict} 区分。读回时仍还原成三栏，语义不丢。
     */
    private String writeUnresolvedItems(List<RequirementResultResponse> items) {
        ArrayNode array = objectMapper.createArrayNode();
        for (RequirementResultResponse item : items) {
            if (MatchVerdict.MATCHED.name().equals(item.verdict())) {
                continue;
            }
            ObjectNode node = array.addObject();
            fillIdentity(node, item);
            node.put("verdict", item.verdict());
            node.put("reason", item.reason());
        }
        return array.toString();
    }

    private static void fillIdentity(ObjectNode node, RequirementResultResponse item) {
        node.put("requirementId", item.requirementId());
        node.put("category", item.category());
        node.put("text", item.text());
    }

    private List<RequirementResultResponse> readStoredItems(String matchedJson, String gapsJson) {
        List<RequirementResultResponse> items = new ArrayList<>();
        for (JsonNode node : readArray(matchedJson)) {
            List<EvidenceResponse> evidences = new ArrayList<>();
            for (JsonNode evidence : node.path("evidences")) {
                evidences.add(new EvidenceResponse(evidence.path("sectionKey").asText(null),
                        SECTION_LABELS.getOrDefault(evidence.path("sectionKey").asText(""), ""),
                        evidence.path("itemId").asText(null),
                        evidence.path("itemLabel").asText(null),
                        evidence.path("snippet").asText(null)));
            }
            items.add(response(node, evidences));
        }
        for (JsonNode node : readArray(gapsJson)) {
            items.add(response(node, List.of()));
        }
        return items;
    }

    private static RequirementResultResponse response(JsonNode node, List<EvidenceResponse> evidences) {
        String verdict = node.path("verdict").asText(MatchVerdict.GAP.name());
        return new RequirementResultResponse(node.path("requirementId").asText(null),
                node.path("category").asText(null),
                categoryLabel(node.path("category").asText(null)),
                node.path("text").asText(""),
                verdict, verdictLabel(verdict), node.path("reason").asText(null),
                evidences, node.path("evidenceCount").asInt(evidences.size()));
    }

    private JsonNode readArray(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createArrayNode();
        }
        try {
            JsonNode parsed = objectMapper.readTree(json);
            return parsed.isArray() ? parsed : objectMapper.createArrayNode();
        } catch (Exception exception) {
            // 存储层被外部改动只影响这一条可读性，不该把整页打挂；按空清单处理并在日志里留痕。
            return objectMapper.createArrayNode();
        }
    }

    private String writeProfileSnapshot(JobProfileStateResponse state) {
        if (!state.completed() || state.profile() == null) {
            return null;
        }
        JobProfileResponse profile = state.profile();
        ObjectNode node = objectMapper.createObjectNode();
        node.put("updatedAt", profile.updatedAt() == null ? null : profile.updatedAt().toString());
        node.put("targetRole", profile.targetRole());
        node.put("targetExperienceBand", profile.targetExperienceBand());
        node.put("careerStage", profile.careerStage());
        node.put("javaSkillLevel", profile.javaSkillLevel());
        node.put("aiSkillLevel", profile.aiSkillLevel());
        return node.toString();
    }

    private Instant profileSnapshotAt(String json) {
        JsonNode node = readObject(json);
        String raw = node.path("updatedAt").asText(null);
        return raw == null || raw.isBlank() ? null : Instant.parse(raw);
    }

    private JsonNode readObject(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode parsed = objectMapper.readTree(json);
            return parsed.isObject() ? parsed : objectMapper.createObjectNode();
        } catch (Exception exception) {
            return objectMapper.createObjectNode();
        }
    }

    // ---------- 组装响应 ----------

    private PostingSummaryResponse summary(JobPostingListRow row) {
        return new PostingSummaryResponse(row.getPostingId(), row.getCompany(), row.getTitle(),
                row.getCity(), row.getSalaryNote(), row.getSource(), row.isArchived(), row.getUpdatedAt(),
                row.getActiveVersionId(), row.getActiveVersionNumber(),
                row.getRequirementCount() == null ? 0 : row.getRequirementCount(),
                row.getApplicationId() == null ? null
                        : new ApplicationRef(row.getApplicationId(), row.getApplicationCompany(),
                                row.getApplicationRole(), row.getApplicationStage()),
                row.getLastMatchedAt(), row.getLastMatchConfirmationStatus());
    }

    private ApplicationRef applicationRef(Long userId, Long applicationId) {
        if (applicationId == null) {
            return null;
        }
        ApplicationResponse application = applicationService.get(userId, applicationId);
        return new ApplicationRef(application.id(), application.company(), application.role(), application.stage());
    }

    private MatchHistoryResponse history(JobMatchRow row) {
        List<RequirementResultResponse> items = readStoredItems(row.getMatchedJson(), row.getGapsJson());
        ResumeRef resume = row.getResumeVersionId() == null
                ? null
                : new ResumeRef(row.getResumeVersionId(), row.getResumeVersionNumber(), row.getResumeStatus());
        return new MatchHistoryResponse(row.getMatchId(), row.getJdVersionNumber(), resume, row.getRuleVersion(),
                counts(items),
                row.getConfirmationStatus() == null ? null : row.getConfirmationStatus().name(),
                row.getConfirmedAt(), row.getCreatedAt());
    }

    private MatchViewResponse view(JobPostingRecord posting, JobPostingVersionRecord version, MatchOutcome outcome,
                                   Long matchId, MatchConfirmation status, Instant confirmedAt, Instant savedAt) {
        ResumeRef resume = outcome.resumeVersionId() == null
                ? null
                : new ResumeRef(outcome.resumeVersionId(), outcome.resumeVersionNumber(), outcome.resumeStatus());
        return new MatchViewResponse(matchId, posting.getId(), posting.getCompany(), posting.getTitle(),
                version.getId(), version.getVersionNumber(), MATCH_RULE_VERSION,
                resume, outcome.resumeSource(), outcome.profileSnapshotAt(),
                counts(outcome.items()), new ScoringResponse(false, SCORING_REASON),
                status == null ? null : status.name(), confirmedAt, savedAt, outcome.items());
    }

    private static CountsResponse counts(List<RequirementResultResponse> items) {
        int matched = 0;
        int gaps = 0;
        int needConfirmation = 0;
        for (RequirementResultResponse item : items) {
            switch (MatchVerdict.valueOf(item.verdict())) {
                case MATCHED -> matched++;
                case GAP -> gaps++;
                case NEED_CONFIRMATION -> needConfirmation++;
            }
        }
        return new CountsResponse(items.size(), matched, gaps, needConfirmation);
    }

    private static RequirementResultResponse result(Requirement requirement, MatchVerdict verdict,
                                                    String reason, List<EvidenceResponse> evidences) {
        return new RequirementResultResponse(requirement.id(), requirement.category().name(),
                requirement.category().displayName(), requirement.text(),
                verdict.name(), verdict.displayName(), reason, evidences, evidences.size());
    }

    private static RequirementResultResponse withEvidenceCount(RequirementResultResponse base, int evidenceCount) {
        return new RequirementResultResponse(base.requirementId(), base.category(), base.categoryLabel(),
                base.text(), base.verdict(), base.verdictLabel(), base.reason(), base.evidences(), evidenceCount);
    }

    private static String categoryLabel(String category) {
        try {
            return RequirementCategory.valueOf(category).displayName();
        } catch (IllegalArgumentException | NullPointerException exception) {
            return category;
        }
    }

    private static String verdictLabel(String verdict) {
        try {
            return MatchVerdict.valueOf(verdict).displayName();
        } catch (IllegalArgumentException | NullPointerException exception) {
            return verdict;
        }
    }

    // ---------- 校验与解析 ----------

    private JobPostingRecord owned(Long userId, Long id) {
        JobPostingRecord posting = postingMapper.findOwned(userId, id);
        if (posting == null) {
            throw notFound("岗位不存在");
        }
        return posting;
    }

    private JobMatchRow ownedMatch(Long userId, Long matchId) {
        JobMatchRow row = matchMapper.findOwned(userId, matchId);
        if (row == null) {
            throw notFound("匹配结果不存在");
        }
        return row;
    }

    private JobPostingVersionRecord requireActiveVersion(JobPostingRecord posting) {
        JobPostingVersionRecord version = posting.getActiveVersionId() == null
                ? null
                : postingMapper.findVersionOfPosting(posting.getId(), posting.getActiveVersionId());
        if (version == null) {
            throw notFound("岗位没有可用的 JD 版本");
        }
        return version;
    }

    private void validateApplication(Long userId, Long applicationId) {
        if (applicationId != null) {
            // 归属校验交给投递模块自己：越权时它抛 404，本模块不重复实现一套查询。
            applicationService.get(userId, applicationId);
        }
    }

    private JobPostingVersionRecord newVersion(Long postingId, int versionNumber, String jdText,
                                               List<Requirement> requirements, Instant now) {
        JobPostingVersionRecord version = new JobPostingVersionRecord();
        version.setJobPostingId(postingId);
        version.setVersionNumber(versionNumber);
        version.setJdText(jdText);
        version.setRequiredSkillsJson(writeRequirements(requirements));
        version.setRuleVersion(MATCH_RULE_VERSION);
        version.setCreatedAt(now);
        return version;
    }

    private List<Requirement> parseRequirements(List<RequirementPayload> payloads) {
        if (payloads == null || payloads.isEmpty()) {
            return List.of();
        }
        if (payloads.size() > MAX_REQUIREMENTS) {
            throw invalid("要求清单最多 " + MAX_REQUIREMENTS + " 条，当前 " + payloads.size() + " 条");
        }
        Set<String> seen = new HashSet<>();
        List<Requirement> requirements = new ArrayList<>();
        for (int index = 0; index < payloads.size(); index++) {
            RequirementPayload payload = payloads.get(index);
            String id = requireText(payload.id(), 64, "requirements[" + index + "].id");
            if (!seen.add(id)) {
                throw invalid("requirements 里有重复的 id：" + id);
            }
            requirements.add(new Requirement(id,
                    parseEnum(RequirementCategory.class, payload.category(), "requirements[" + index + "].category"),
                    requireText(payload.text(), MAX_REQUIREMENT_CHARS, "requirements[" + index + "].text")));
        }
        return requirements;
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String raw, String field) {
        for (E candidate : type.getEnumConstants()) {
            if (candidate.name().equals(raw == null ? "" : raw.trim())) {
                return candidate;
            }
        }
        throw invalid(field + " 取值不合法，可选：" + String.join("、",
                Arrays.stream(type.getEnumConstants()).map(Enum::name).toList()));
    }

    /** 归档语义沿用错题本与报告中心：缺省只看未归档，显式 all 才两批一起给。 */
    private static Boolean parseArchived(String raw) {
        if (raw == null || raw.isBlank() || "false".equalsIgnoreCase(raw.trim())) {
            return Boolean.FALSE;
        }
        if ("true".equalsIgnoreCase(raw.trim())) {
            return Boolean.TRUE;
        }
        if ("all".equalsIgnoreCase(raw.trim())) {
            return null;
        }
        throw invalid("archived 只接受 true / false / all");
    }

    private static int parsePositive(String raw, int fallback, String field) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        int value;
        try {
            value = Integer.parseInt(raw.trim());
        } catch (NumberFormatException exception) {
            throw invalid(field + " 必须是整数");
        }
        if (value < 1) {
            throw invalid(field + " 必须大于 0");
        }
        return value;
    }

    private static Instant parseInstant(String raw, Instant fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Instant.parse(raw.trim());
        } catch (DateTimeParseException exception) {
            throw invalid("expectedUpdatedAt 格式不正确，请使用接口返回的 updatedAt 原值");
        }
    }

    private static String requireText(String raw, int maxChars, String field) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            throw invalid(field + " 不能为空");
        }
        if (value.length() > maxChars) {
            throw invalid(field + " 长度不得超过 " + maxChars + " 字");
        }
        return value;
    }

    private static String trimToLength(String raw, int maxChars, String field) {
        String value = trimToNull(raw);
        if (value != null && value.length() > maxChars) {
            throw invalid(field + " 长度不得超过 " + maxChars + " 字");
        }
        return value;
    }

    private static String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        return value.isEmpty() ? null : value;
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST, message);
    }

    private static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT, message);
    }

    private static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, message);
    }

    /**
     * 归一化（`17` §5.2）：剔除零宽与控制字符、回车换行与全角空格归一、全角标点转半角、大小写折叠、
     * 连续空白折叠为一个空格。同时保留每个归一化字符在原串里的位置，
     * 这样命中依据仍能按**用户原话**截出片段，而不是显示一份被改写过的文本。
     */
    private static Hay normalize(String raw) {
        String source = raw == null ? "" : raw;
        StringBuilder out = new StringBuilder(source.length());
        int[] origin = new int[source.length() + 1];
        for (int index = 0; index < source.length(); index++) {
            char character = source.charAt(index);
            char mapped;
            if (character == '\uFEFF' || character == '\u200B'
                    || character == '\u200C' || character == '\u200D') {
                continue;
            }
            if (character == '\r' || character == '\n'
                    || character == '\t' || character == '\u3000') {
                mapped = ' ';
            } else if (character >= '\uFF01' && character <= '\uFF5E') {
                mapped = (char) (character - 0xFEE0);
            } else if (Character.isISOControl(character)) {
                continue;
            } else {
                mapped = character;
            }
            char lowered = Character.toLowerCase(mapped);
            if (lowered == ' ' && out.length() > 0 && out.charAt(out.length() - 1) == ' ') {
                continue;
            }
            origin[out.length()] = index;
            out.append(lowered);
        }
        int end = out.length();
        while (end > 0 && out.charAt(end - 1) == ' ') {
            end--;
        }
        int start = 0;
        while (start < end && out.charAt(start) == ' ') {
            start++;
        }
        int length = end - start;
        int[] mapped = new int[length];
        System.arraycopy(origin, start, mapped, 0, length);
        return new Hay(source, out.substring(start, end), mapped);
    }

    /** 归一化串 + 原串 + 位置映射。片段一律从原串截取。 */
    private record Hay(String original, String text, int[] origin) {

        String snippetAt(int index, int needleLength) {
            if (origin.length == 0 || index < 0) {
                return text;
            }
            int from = origin[index];
            int to = origin[Math.min(index + needleLength, origin.length - 1)] + 1;
            int start = Math.max(0, from - SNIPPET_RADIUS);
            int end = Math.min(original.length(), to + SNIPPET_RADIUS);
            String snippet = original.substring(start, end)
                    .replace('\r', ' ')
                    .replace('\n', ' ')
                    .replace('\t', ' ');
            return (start > 0 ? "…" : "") + snippet + (end < original.length() ? "…" : "");
        }
    }

    private record Requirement(String id, RequirementCategory category, String text) {
    }

    private record ResumeChoice(VersionDetail detail, String source) {
    }

    private record MatchOutcome(List<RequirementResultResponse> items,
                                Long resumeVersionId,
                                Integer resumeVersionNumber,
                                String resumeStatus,
                                String resumeSource,
                                String profileSnapshotJson,
                                Instant profileSnapshotAt) {
    }
}

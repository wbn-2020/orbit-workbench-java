package com.orbitworkbench.resume.application;

import com.orbitworkbench.jobprofile.domain.JobProfileRecord;
import com.orbitworkbench.jobprofile.infrastructure.mapper.JobProfileMapper;
import com.orbitworkbench.knowledge.domain.ProjectFactRecord;
import com.orbitworkbench.knowledge.infrastructure.mapper.ProjectFactMapper;
import com.orbitworkbench.project.domain.ProjectRecord;
import com.orbitworkbench.project.domain.ProjectVersionRecord;
import com.orbitworkbench.project.infrastructure.mapper.ProjectMapper;
import com.orbitworkbench.resume.api.ResumeDtos.ActiveRequest;
import com.orbitworkbench.resume.api.ResumeDtos.ResumeStateResponse;
import com.orbitworkbench.resume.api.ResumeDtos.DraftSaveRequest;
import com.orbitworkbench.resume.api.ResumeDtos.ExportRecordResponse;
import com.orbitworkbench.resume.api.ResumeDtos.ItemResponse;
import com.orbitworkbench.resume.api.ResumeDtos.SectionResponse;
import com.orbitworkbench.resume.api.ResumeDtos.VersionDetail;
import com.orbitworkbench.resume.api.ResumeDtos.VersionSummary;
import com.orbitworkbench.resume.application.ResumeSections.Item;
import com.orbitworkbench.resume.application.ResumeSections.Model;
import com.orbitworkbench.resume.application.ResumeSections.Section;
import com.orbitworkbench.resume.application.ResumeSections.Source;
import com.orbitworkbench.resume.domain.ResumeDocumentRecord;
import com.orbitworkbench.resume.domain.ResumeExportRecord;
import com.orbitworkbench.resume.domain.ResumeExportSummary;
import com.orbitworkbench.resume.domain.ResumeItemKind;
import com.orbitworkbench.resume.domain.ResumeSectionKey;
import com.orbitworkbench.resume.domain.ResumeSourceType;
import com.orbitworkbench.resume.domain.ResumeVersionRecord;
import com.orbitworkbench.resume.domain.ResumeVersionStatus;
import com.orbitworkbench.resume.infrastructure.mapper.ResumeExportMapper;
import com.orbitworkbench.resume.infrastructure.mapper.ResumeMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 简历工作台主链（13 §5–§6）：单用户单简历、DRAFT/FINAL 版本、逐条来源追溯。
 * 归属一律先按 userId 取 resume_document，再用 resumeId 取版本——V28 的 resume_version 没有 user_id 列。
 */
@Service
public class ResumeService {

    private static final int MAX_BOOTSTRAP_ITEMS_PER_SECTION = 20;

    private final ResumeMapper resumeMapper;
    private final ResumeExportMapper exportMapper;
    private final ResumeSectionsJson json;
    private final JobProfileMapper profileMapper;
    private final ProjectMapper projectMapper;
    private final ProjectFactMapper factMapper;

    public ResumeService(ResumeMapper resumeMapper, ResumeExportMapper exportMapper,
                         ResumeSectionsJson json, JobProfileMapper profileMapper,
                         ProjectMapper projectMapper, ProjectFactMapper factMapper) {
        this.resumeMapper = resumeMapper;
        this.exportMapper = exportMapper;
        this.json = json;
        this.profileMapper = profileMapper;
        this.projectMapper = projectMapper;
        this.factMapper = factMapper;
    }

    public ResumeStateResponse state(Long userId) {
        ResumeDocumentRecord document = resumeMapper.findDocumentByUser(userId);
        if (document == null) {
            return new ResumeStateResponse(false, null, null, null, null, null, List.of());
        }
        List<ResumeVersionRecord> versions = resumeMapper.listVersions(document.getId());
        Map<Long, ResumeExportSummary> summaries = exportSummaries(document.getId(), userId);
        List<VersionSummary> summariesByVersion = versions.stream()
                .map(version -> summary(version, summaries.get(version.getId())))
                .toList();
        ResumeVersionRecord draft = resumeMapper.findDraft(document.getId());
        return new ResumeStateResponse(true, document.getId(), document.getTitle(),
                document.getActiveVersionId(),
                document.getActiveVersionId() == null ? null : summariesByVersion.stream()
                        .filter(record -> record.id().equals(document.getActiveVersionId()))
                        .findFirst().orElse(null),
                draft == null ? null : detail(userId, draft.getId()),
                summariesByVersion);
    }

    public VersionDetail detail(Long userId, Long versionId) {
        ResumeDocumentRecord document = requireDocument(userId);
        ResumeVersionRecord version = requireVersion(document, versionId);
        List<ExportRecordResponse> exports = exportMapper.listByVersion(versionId, userId).stream()
                .map(record -> new ExportRecordResponse(record.getId(), record.getStatus().name(),
                        record.getSizeBytes(), record.getFontName(), record.getFailureReason(),
                        record.getCreatedAt()))
                .toList();
        return toDetail(version, exports);
    }

    @Transactional
    public VersionDetail bootstrap(Long userId, String requestedTitle) {
        if (resumeMapper.findDocumentByUser(userId) != null) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "简历已存在，请直接编辑草稿或从历史版本复制");
        }
        Instant now = Instant.now();
        String title = requestedTitle == null || requestedTitle.isBlank() ? "我的简历" : requestedTitle.trim();
        ResumeDocumentRecord document = new ResumeDocumentRecord();
        document.setUserId(userId);
        document.setTitle(title.length() > 128 ? title.substring(0, 128) : title);
        document.setActiveVersionId(null);
        document.setCreatedAt(now);
        document.setUpdatedAt(now);
        resumeMapper.insertDocument(document);

        Model model = buildInitialDraft(userId);
        ResumeVersionRecord version = newVersion(document.getId(), resumeMapper.nextVersionNumber(document.getId()),
                json.write(model), null, "由现有资料生成初稿", now);
        resumeMapper.insertVersion(version);
        return toDetail(version, List.of());
    }

    @Transactional
    public VersionDetail saveDraft(Long userId, DraftSaveRequest request) {
        ResumeDocumentRecord document = requireDocument(userId);
        ResumeVersionRecord draft = requireDraftForUpdate(document);
        Model model = ResumeSections.normalize(request.sections());
        String content = json.write(model);
        Instant now = Instant.now();
        Instant expected = request.expectedUpdatedAt() == null ? draft.getUpdatedAt()
                : request.expectedUpdatedAt();
        if (resumeMapper.updateDraftSections(draft.getId(), document.getId(), content,
                trimmed(request.changeSummary()), expected, now) != 1) {
            throw conflict("简历内容已变化，请刷新后重试");
        }
        String title = request.title() == null || request.title().isBlank()
                ? document.getTitle() : request.title().trim();
        if (!title.equals(document.getTitle())) {
            resumeMapper.updateDocumentMeta(document.getId(), userId, title,
                    document.getActiveVersionId(), now);
        }
        draft.setSectionsJson(content);
        draft.setChangeSummary(trimmed(request.changeSummary()));
        draft.setUpdatedAt(now);
        return toDetail(draft, exportRecords(draft.getId(), userId));
    }

    @Transactional
    public VersionDetail finalizeDraft(Long userId, String changeSummary) {
        ResumeDocumentRecord document = requireDocument(userId);
        ResumeVersionRecord draft = requireDraftForUpdate(document);
        Model model = json.read(draft.getSectionsJson());
        if (model.itemCount() == 0) {
            throw ResumeSections.invalid("简历正文为空，不能定稿");
        }
        Instant now = Instant.now();
        String snapshot = json.writeNullable(sourceSnapshot(model, now));
        String summary = changeSummary == null || changeSummary.isBlank()
                ? draft.getChangeSummary() : changeSummary.trim();
        if (resumeMapper.finalizeDraft(draft.getId(), document.getId(), snapshot, summary, now) != 1) {
            throw conflict("草稿状态已变化，请刷新后重试");
        }
        resumeMapper.updateDocumentMeta(document.getId(), userId, document.getTitle(), draft.getId(), now);
        draft.setStatus(ResumeVersionStatus.FINAL);
        draft.setSourceSnapshotJson(snapshot);
        draft.setChangeSummary(summary);
        draft.setUpdatedAt(now);
        return toDetail(draft, exportRecords(draft.getId(), userId));
    }

    @Transactional
    public VersionDetail duplicate(Long userId, Long versionId) {
        ResumeDocumentRecord document = requireDocument(userId);
        ResumeVersionRecord source = requireVersion(document, versionId);
        if (resumeMapper.findDraftForUpdate(document.getId()) != null) {
            throw conflict("已有未定稿草稿，请先定稿或丢弃后再复制");
        }
        Instant now = Instant.now();
        ResumeVersionRecord copy = newVersion(document.getId(),
                resumeMapper.nextVersionNumber(document.getId()), source.getSectionsJson(),
                null, "复制自版本 " + source.getVersionNumber(), now);
        resumeMapper.insertVersion(copy);
        return toDetail(copy, exportRecords(copy.getId(), userId));
    }

    @Transactional
    public ResumeStateResponse setActive(Long userId, ActiveRequest request) {
        ResumeDocumentRecord document = requireDocument(userId);
        ResumeVersionRecord version = requireVersion(document, request.versionId());
        if (version.getStatus() != ResumeVersionStatus.FINAL) {
            throw ResumeSections.invalid("只有已定稿版本可设为当前版本");
        }
        if (resumeMapper.updateDocumentMeta(document.getId(), userId, document.getTitle(),
                version.getId(), Instant.now()) != 1) {
            throw conflict("简历状态已变化，请刷新后重试");
        }
        return state(userId);
    }

    /** 只带入产品内确有来源的区块；教育与工作经历没有来源，绝不伪造（13 §3）。 */
    private Model buildInitialDraft(Long userId) {
        List<Item> basic = new ArrayList<>();
        JobProfileRecord profile = profileMapper.findByUserId(userId);
        if (profile != null) {
            addField(basic, "期望岗位", profile.getTargetRole(), ResumeSourceType.JOB_PROFILE,
                    profile.getId(), "求职档案");
            addField(basic, "经验档位", experienceBandLabel(profile.getTargetExperienceBand()),
                    ResumeSourceType.JOB_PROFILE, profile.getId(), "求职档案");
            addField(basic, "目标公司", profile.getTargetCompany(), ResumeSourceType.JOB_PROFILE,
                    profile.getId(), "求职档案");
            addField(basic, "职业阶段", careerStageLabel(profile.getCareerStage()),
                    ResumeSourceType.JOB_PROFILE, profile.getId(), "求职档案");
            LocalDate interviewDate = profile.getTargetInterviewDate();
            addField(basic, "目标面试时间", interviewDate == null ? null : interviewDate.toString(),
                    ResumeSourceType.JOB_PROFILE, profile.getId(), "求职档案");
        }

        List<Item> projects = new ArrayList<>();
        List<Item> skills = new ArrayList<>();
        for (ProjectRecord project : projectMapper.findProjectsByUserId(userId)) {
            if (projects.size() >= MAX_BOOTSTRAP_ITEMS_PER_SECTION
                    && skills.size() >= MAX_BOOTSTRAP_ITEMS_PER_SECTION) {
                break;
            }
            ProjectVersionRecord latest = projectMapper.findLatestVersion(project.getId());
            if (latest == null) {
                continue;
            }
            for (ProjectFactRecord fact : factMapper.listByVersion(userId, latest.getId())) {
                String content = fact.getContent();
                if (content == null || content.isBlank()) {
                    continue;
                }
                if ("TECH_STACK".equals(fact.getFactType())) {
                    if (skills.size() < MAX_BOOTSTRAP_ITEMS_PER_SECTION) {
                        skills.add(entry(ResumeSectionKey.SKILLS, fact.getTitle(), content,
                                ResumeSourceType.PROJECT_FACT, fact.getId(),
                                project.getName() + " · 版本 " + latest.getVersionNumber() + " · 已确认事实"));
                    }
                } else if ("RESPONSIBILITY".equals(fact.getFactType())
                        || "BUSINESS".equals(fact.getFactType())) {
                    if (projects.size() < MAX_BOOTSTRAP_ITEMS_PER_SECTION) {
                        projects.add(entry(ResumeSectionKey.PROJECT_EXPERIENCE, fact.getTitle(), content,
                                ResumeSourceType.PROJECT_FACT, fact.getId(),
                                project.getName() + " · 版本 " + latest.getVersionNumber() + " · 已确认事实"));
                    }
                }
            }
        }

        List<Section> sections = List.of(
                new Section(ResumeSectionKey.BASIC_INFO, renumber(basic)),
                new Section(ResumeSectionKey.EDUCATION, List.of()),
                new Section(ResumeSectionKey.WORK_EXPERIENCE, List.of()),
                new Section(ResumeSectionKey.PROJECT_EXPERIENCE, renumber(projects)),
                new Section(ResumeSectionKey.SKILLS, renumber(skills)));
        return new Model(ResumeSections.SCHEMA_VERSION, sections);
    }

    /** 枚举码不得出现在简历正文与导出 PDF 里；未知取值原样保留，避免丢数据。 */
    private static String careerStageLabel(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw) {
            case "GRADUATE" -> "应届生";
            case "CAREER_TRANSITION" -> "转行";
            case "JOB_CHANGE" -> "在职跳槽";
            default -> raw;
        };
    }

    private static String experienceBandLabel(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw) {
            case "GRADUATE" -> "应届";
            case "ONE_TO_THREE_YEARS" -> "1-3 年";
            case "THREE_TO_FIVE_YEARS" -> "3-5 年";
            case "FIVE_PLUS_YEARS" -> "5 年以上";
            // CUSTOM 没有自由文本列可依据，宁可不带入也不把枚举码写进简历。
            case "CUSTOM" -> null;
            default -> raw;
        };
    }

    private void addField(List<Item> target, String label, String value, ResumeSourceType type,
                          Long refId, String sourceLabel) {
        if (value == null || value.isBlank() || target.size() >= ResumeSections.MAX_ITEMS_PER_SECTION) {
            return;
        }
        target.add(new Item(UUID.randomUUID().toString(), target.size() + 1, ResumeItemKind.FIELD,
                label, value.trim(), new Source(type, refId, sourceLabel), false));
    }

    private Item entry(ResumeSectionKey key, String label, String content, ResumeSourceType type,
                       Long refId, String sourceLabel) {
        String text = content.trim();
        if (text.length() > 2000) {
            text = text.substring(0, 2000);
        }
        return new Item(UUID.randomUUID().toString(), 1,
                key == ResumeSectionKey.SKILLS ? ResumeItemKind.FIELD : ResumeItemKind.ENTRY,
                label == null || label.isBlank() ? null : label.trim(), text,
                new Source(type, refId, sourceLabel), false);
    }

    private List<Item> renumber(List<Item> items) {
        List<Item> result = new ArrayList<>();
        int order = 1;
        for (Item item : items) {
            result.add(new Item(item.id(), order++, item.kind(), item.label(), item.text(),
                    item.source(), item.edited()));
        }
        return List.copyOf(result);
    }

    private List<Map<String, Object>> sourceSnapshot(Model model, Instant capturedAt) {
        Map<String, Map<String, Object>> distinct = new LinkedHashMap<>();
        for (Section section : model.sections()) {
            for (Item item : section.items()) {
                Source source = item.source();
                if (source == null || source.type() == ResumeSourceType.MANUAL) {
                    continue;
                }
                distinct.putIfAbsent(source.type() + ":" + source.refId(), Map.of(
                        "type", source.type().name(),
                        "refId", source.refId() == null ? -1L : source.refId(),
                        "label", source.label() == null ? "" : source.label(),
                        "section", section.key().name(),
                        "itemId", item.id(),
                        "capturedAt", capturedAt.toString()));
            }
        }
        return List.copyOf(distinct.values());
    }

    private ResumeVersionRecord newVersion(Long resumeId, int versionNumber, String sectionsJson,
                                           String snapshotJson, String changeSummary, Instant now) {
        ResumeVersionRecord version = new ResumeVersionRecord();
        version.setResumeId(resumeId);
        version.setVersionNumber(versionNumber);
        version.setStatus(ResumeVersionStatus.DRAFT);
        version.setSectionsJson(sectionsJson);
        version.setSourceSnapshotJson(snapshotJson);
        version.setChangeSummary(changeSummary);
        version.setCreatedAt(now);
        version.setUpdatedAt(now);
        return version;
    }

    private ResumeDocumentRecord requireDocument(Long userId) {
        return ResumeSupport.requireDocument(resumeMapper, userId);
    }

    private ResumeVersionRecord requireVersion(ResumeDocumentRecord document, Long versionId) {
        return ResumeSupport.requireVersion(resumeMapper, document, versionId);
    }

    private ResumeVersionRecord requireDraftForUpdate(ResumeDocumentRecord document) {
        ResumeVersionRecord draft = resumeMapper.findDraftForUpdate(document.getId());
        if (draft == null) {
            throw conflict("没有可编辑的草稿，请从历史版本复制为草稿");
        }
        return draft;
    }

    private List<ExportRecordResponse> exportRecords(Long versionId, Long userId) {
        return exportMapper.listByVersion(versionId, userId).stream()
                .map(record -> new ExportRecordResponse(record.getId(), record.getStatus().name(),
                        record.getSizeBytes(), record.getFontName(), record.getFailureReason(),
                        record.getCreatedAt()))
                .toList();
    }

    private Map<Long, ResumeExportSummary> exportSummaries(Long resumeId, Long userId) {
        Map<Long, ResumeExportSummary> result = new LinkedHashMap<>();
        for (ResumeExportSummary summary : exportMapper.summarizeByResume(resumeId, userId)) {
            result.put(summary.getResumeVersionId(), summary);
        }
        return result;
    }

    private VersionSummary summary(ResumeVersionRecord version, ResumeExportSummary export) {
        Model model = safeRead(version.getSectionsJson());
        return new VersionSummary(version.getId(), version.getVersionNumber(),
                version.getStatus().name(), version.getChangeSummary(),
                model == null ? 0 : model.sections().size(),
                model == null ? 0 : model.itemCount(),
                model == null ? 0 : model.totalChars(),
                version.getPdfStorageRef() != null, version.getPdfGeneratedAt(),
                export == null ? 0 : export.getSucceededCount(),
                export == null ? null : export.getLastSucceededAt(),
                version.getCreatedAt(), version.getUpdatedAt());
    }

    private VersionDetail toDetail(ResumeVersionRecord version, List<ExportRecordResponse> exports) {
        Model model = safeRead(version.getSectionsJson());
        List<SectionResponse> sections = model == null ? List.of()
                : model.sections().stream().map(ResumeService::toResponse).toList();
        return new VersionDetail(version.getId(), version.getVersionNumber(),
                version.getStatus().name(), version.getChangeSummary(), sections, exports,
                version.getPdfGeneratedAt(), snapshotTime(version),
                version.getCreatedAt(), version.getUpdatedAt());
    }

    private static SectionResponse toResponse(Section section) {
        return new SectionResponse(section.key().name(), section.items().stream()
                .map(item -> new ItemResponse(item.id(), item.order(), item.kind().name(),
                        item.label(), item.text(),
                        item.source() == null ? ResumeSourceType.MANUAL.name() : item.source().type().name(),
                        item.source() == null ? null : item.source().refId(),
                        item.source() == null ? null : item.source().label(),
                        item.edited()))
                .toList());
    }

    /** 定稿时间：来源快照只在定稿时写入，因此它的存在即代表该版本已定稿。 */
    private Instant snapshotTime(ResumeVersionRecord version) {
        return version.getSourceSnapshotJson() == null || version.getSourceSnapshotJson().isBlank()
                ? null : version.getUpdatedAt();
    }

    private Model safeRead(String sectionsJson) {
        try {
            return json.read(sectionsJson);
        } catch (ApiException exception) {
            return null;
        }
    }

    private static String trimmed(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ApiException conflict(String message) {
        return ResumeSupport.conflict(message);
    }
}

package com.orbitworkbench.jobapplication.domain;

import java.time.Instant;
import java.time.LocalDate;

public class JobApplicationRecord {

    private Long id;
    private Long userId;
    private String company;
    private String role;
    private String jdSummary;
    private String source;
    private LocalDate applyDate;
    private LocalDate interviewDate;
    private ApplicationStage stage;
    private ApplicationResult result;
    private String salaryNote;
    private String contact;
    private String note;
    private Boolean archived;
    private Instant stageChangedAt;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getJdSummary() { return jdSummary; }
    public void setJdSummary(String jdSummary) { this.jdSummary = jdSummary; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public LocalDate getApplyDate() { return applyDate; }
    public void setApplyDate(LocalDate applyDate) { this.applyDate = applyDate; }
    public LocalDate getInterviewDate() { return interviewDate; }
    public void setInterviewDate(LocalDate interviewDate) { this.interviewDate = interviewDate; }
    public ApplicationStage getStage() { return stage; }
    public void setStage(ApplicationStage stage) { this.stage = stage; }
    public ApplicationResult getResult() { return result; }
    public void setResult(ApplicationResult result) { this.result = result; }
    public String getSalaryNote() { return salaryNote; }
    public void setSalaryNote(String salaryNote) { this.salaryNote = salaryNote; }
    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Boolean getArchived() { return archived; }
    public void setArchived(Boolean archived) { this.archived = archived; }
    public Instant getStageChangedAt() { return stageChangedAt; }
    public void setStageChangedAt(Instant stageChangedAt) { this.stageChangedAt = stageChangedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

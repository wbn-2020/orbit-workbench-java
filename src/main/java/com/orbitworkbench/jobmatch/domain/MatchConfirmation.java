package com.orbitworkbench.jobmatch.domain;

/** 匹配结果的人工确认状态，取值集与 V28 的 {@code chk_job_match_confirmation} 一致。 */
public enum MatchConfirmation {
    PENDING,
    CONFIRMED,
    REJECTED
}

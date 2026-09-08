package com.orbitworkbench.workbench.domain;

/** 最近一次面试评估的只读投影，供工作台首页映射成等级标签。 */
public class WorkbenchLastEvaluation {

    private String hiringRecommendation;
    private Integer totalScore;

    public String getHiringRecommendation() { return hiringRecommendation; }
    public void setHiringRecommendation(String hiringRecommendation) { this.hiringRecommendation = hiringRecommendation; }
    public Integer getTotalScore() { return totalScore; }
    public void setTotalScore(Integer totalScore) { this.totalScore = totalScore; }
}

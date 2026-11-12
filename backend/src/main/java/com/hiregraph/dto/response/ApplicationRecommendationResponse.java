package com.hiregraph.dto.response;

import java.util.List;

/**
 * Structured profile-improvement report for an application, comparing the student's
 * current profile and skills against the target job requirements and historically
 * selected peers in the same domain.
 */
public class ApplicationRecommendationResponse {

    private String readinessLevel;
    private String summaryHeadline;
    private String jobTitle;
    private String jobDomain;
    private double skillMatchPercent;
    private List<String> matchedSkills;
    private List<String> missingSkills;
    private List<RecommendationItem> items;
    private List<PeerBenchmark> peerBenchmarks;

    public ApplicationRecommendationResponse() {}

    public String getReadinessLevel() { return readinessLevel; }
    public void setReadinessLevel(String readinessLevel) { this.readinessLevel = readinessLevel; }

    public String getSummaryHeadline() { return summaryHeadline; }
    public void setSummaryHeadline(String summaryHeadline) { this.summaryHeadline = summaryHeadline; }

    public String getJobTitle() { return jobTitle; }
    public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }

    public String getJobDomain() { return jobDomain; }
    public void setJobDomain(String jobDomain) { this.jobDomain = jobDomain; }

    public double getSkillMatchPercent() { return skillMatchPercent; }
    public void setSkillMatchPercent(double skillMatchPercent) { this.skillMatchPercent = skillMatchPercent; }

    public List<String> getMatchedSkills() { return matchedSkills; }
    public void setMatchedSkills(List<String> matchedSkills) { this.matchedSkills = matchedSkills; }

    public List<String> getMissingSkills() { return missingSkills; }
    public void setMissingSkills(List<String> missingSkills) { this.missingSkills = missingSkills; }

    public List<RecommendationItem> getItems() { return items; }
    public void setItems(List<RecommendationItem> items) { this.items = items; }

    public List<PeerBenchmark> getPeerBenchmarks() { return peerBenchmarks; }
    public void setPeerBenchmarks(List<PeerBenchmark> peerBenchmarks) { this.peerBenchmarks = peerBenchmarks; }

    public static class RecommendationItem {
        private String priority;
        private String category;
        private String title;
        private String action;
        private String currentValue;
        private String targetValue;

        public RecommendationItem() {}

        public RecommendationItem(String priority, String category, String title, String action,
                                  String currentValue, String targetValue) {
            this.priority = priority;
            this.category = category;
            this.title = title;
            this.action = action;
            this.currentValue = currentValue;
            this.targetValue = targetValue;
        }

        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority; }

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }

        public String getCurrentValue() { return currentValue; }
        public void setCurrentValue(String currentValue) { this.currentValue = currentValue; }

        public String getTargetValue() { return targetValue; }
        public void setTargetValue(String targetValue) { this.targetValue = targetValue; }
    }

    public static class PeerBenchmark {
        private String metric;
        private double studentValue;
        private double selectedPeerAvg;
        private String status;

        public PeerBenchmark() {}

        public PeerBenchmark(String metric, double studentValue, double selectedPeerAvg, String status) {
            this.metric = metric;
            this.studentValue = studentValue;
            this.selectedPeerAvg = selectedPeerAvg;
            this.status = status;
        }

        public String getMetric() { return metric; }
        public void setMetric(String metric) { this.metric = metric; }

        public double getStudentValue() { return studentValue; }
        public void setStudentValue(double studentValue) { this.studentValue = studentValue; }

        public double getSelectedPeerAvg() { return selectedPeerAvg; }
        public void setSelectedPeerAvg(double selectedPeerAvg) { this.selectedPeerAvg = selectedPeerAvg; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}

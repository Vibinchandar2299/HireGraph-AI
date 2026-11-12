package com.hiregraph.service;

import com.hiregraph.dto.response.ApplicationRecommendationResponse;
import com.hiregraph.dto.response.ApplicationRecommendationResponse.PeerBenchmark;
import com.hiregraph.dto.response.ApplicationRecommendationResponse.RecommendationItem;
import com.hiregraph.entity.*;
import com.hiregraph.repository.*;
import com.hiregraph.util.SkillCatalog;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates actionable, data-backed profile-improvement recommendations for an application
 * by comparing student skills, academic standing, pre-interview assessments, and experience
 * against target job requirements and historically selected candidates in the same domain.
 */
@Service
@Transactional(readOnly = true)
public class ApplicationRecommendationService {

    private final StudentRepository studentRepository;
    private final JobRepository jobRepository;
    private final CompanyRepository companyRepository;
    private final SkillRepository skillRepository;
    private final StudentSkillRepository studentSkillRepository;
    private final JobSkillRepository jobSkillRepository;
    private final ApplicationRepository applicationRepository;

    public ApplicationRecommendationService(StudentRepository studentRepository,
                                            JobRepository jobRepository,
                                            CompanyRepository companyRepository,
                                            SkillRepository skillRepository,
                                            StudentSkillRepository studentSkillRepository,
                                            JobSkillRepository jobSkillRepository,
                                            ApplicationRepository applicationRepository) {
        this.studentRepository = studentRepository;
        this.jobRepository = jobRepository;
        this.companyRepository = companyRepository;
        this.skillRepository = skillRepository;
        this.studentSkillRepository = studentSkillRepository;
        this.jobSkillRepository = jobSkillRepository;
        this.applicationRepository = applicationRepository;
    }

    public ApplicationRecommendationResponse buildRecommendations(Application app) {
        Student student = studentRepository.findByStudentId(app.getStudentId()).orElse(null);
        Job job = jobRepository.findByJobId(app.getJobId()).orElse(null);
        Company company = companyRepository.findByCompanyId(app.getCompanyId()).orElse(null);

        if (student == null || job == null) {
            return null;
        }

        Map<String, String> skillNames = skillRepository.findAll().stream()
                .collect(Collectors.toMap(Skill::getSkillId, Skill::getSkillName, (a, b) -> a));

        Set<String> studentSkillIds = studentSkillRepository.findByStudentId(student.getStudentId()).stream()
                .map(StudentSkill::getSkillId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<String> jobSkillIds = jobSkillRepository.findByJobId(job.getJobId()).stream()
                .map(JobSkill::getSkillId)
                .distinct()
                .toList();

        List<String> matchedSkills = new ArrayList<>();
        List<String> missingSkills = new ArrayList<>();
        for (String skillId : jobSkillIds) {
            String displayName = skillNames.getOrDefault(skillId, SkillCatalog.resolveName(skillId));
            if (studentSkillIds.contains(skillId)) {
                matchedSkills.add(displayName);
            } else {
                missingSkills.add(displayName);
            }
        }

        List<RecommendationItem> items = new ArrayList<>();
        evaluateSkillGaps(app, job, jobSkillIds.size(), matchedSkills, missingSkills, items);
        evaluateAcademicEligibility(student, job, company, items);

        List<PeerBenchmark> benchmarks = new ArrayList<>();
        evaluatePeerScoreGaps(app, student, job, benchmarks, items);
        evaluateExperienceAndPortfolio(app, student, job, items);

        // Sort HIGH -> MEDIUM -> LOW priority
        items.sort(Comparator.comparingInt(item -> priorityRank(item.getPriority())));

        double skillMatchPct = jobSkillIds.isEmpty()
                ? round2((app.getSkillMatchRatio() != null ? app.getSkillMatchRatio() : 0.0) * 100.0)
                : round2((matchedSkills.size() * 100.0) / jobSkillIds.size());

        long highCount = items.stream().filter(i -> "HIGH".equals(i.getPriority())).count();
        String readinessLevel = highCount >= 2 ? "Needs Attention" : (highCount == 1 || items.size() >= 3 ? "Borderline" : "Strong Match");

        ApplicationRecommendationResponse response = new ApplicationRecommendationResponse();
        response.setReadinessLevel(readinessLevel);
        response.setJobTitle(job.getJobTitle());
        response.setJobDomain(job.getJobDomain());
        response.setSkillMatchPercent(skillMatchPct);
        response.setMatchedSkills(matchedSkills);
        response.setMissingSkills(missingSkills);
        response.setItems(items);
        response.setPeerBenchmarks(benchmarks);
        response.setSummaryHeadline(buildSummaryHeadline(items, missingSkills, job));
        return response;
    }

    private void evaluateSkillGaps(Application app, Job job, int requiredCount,
                                   List<String> matchedSkills, List<String> missingSkills,
                                   List<RecommendationItem> items) {
        if (!missingSkills.isEmpty()) {
            String priority = missingSkills.size() >= 2 ? "HIGH" : "MEDIUM";
            String missingList = String.join(", ", missingSkills);
            items.add(new RecommendationItem(
                    priority,
                    "Skills",
                    "Acquire Missing Role Skills",
                    "Learn and demonstrate " + missingList + " through projects or coursework to satisfy "
                            + job.getJobTitle() + " requirements.",
                    matchedSkills.size() + " of " + requiredCount + " required skills",
                    requiredCount + " of " + requiredCount + " required skills"
            ));
        }

        if (app.getRequiredSkillLevelGap() != null && app.getRequiredSkillLevelGap() > 0.05) {
            String priority = app.getRequiredSkillLevelGap() >= 0.25 ? "HIGH" : "MEDIUM";
            items.add(new RecommendationItem(
                    priority,
                    "Skill Depth",
                    "Close Skill Proficiency Gap",
                    "Deepen practical proficiency in your core stack (current gap is "
                            + round2(app.getRequiredSkillLevelGap()) + ") with hands-on labs and coding challenges.",
                    "Gap: " + round2(app.getRequiredSkillLevelGap()),
                    "Gap <= 0.05"
            ));
        }
    }

    private void evaluateAcademicEligibility(Student student, Job job, Company company,
                                             List<RecommendationItem> items) {
        if (student.getBacklogs() != null && student.getBacklogs() > 0) {
            items.add(new RecommendationItem(
                    "HIGH",
                    "Academics",
                    "Clear Active Backlogs",
                    "Clear your " + student.getBacklogs() + " active backlog(s) prior to drive shortlisting.",
                    student.getBacklogs() + " active",
                    "0 backlogs"
            ));
        }

        if (student.getCgpa() != null && job.getMinimumCgpa() != null && student.getCgpa() < job.getMinimumCgpa()) {
            items.add(new RecommendationItem(
                    "HIGH",
                    "Academics",
                    "Meet Minimum CGPA Cutoff",
                    "Your CGPA (" + round2(student.getCgpa()) + ") is below this role's minimum cutoff ("
                            + round2(job.getMinimumCgpa()) + "). Focus on upcoming semester grades or target roles with lower cutoffs.",
                    "CGPA " + round2(student.getCgpa()),
                    "CGPA >= " + round2(job.getMinimumCgpa())
            ));
        } else if (student.getCgpa() != null && company != null
                && company.getHistoricalAverageSelectedCgpa() != null
                && student.getCgpa() < company.getHistoricalAverageSelectedCgpa() - 0.25) {
            items.add(new RecommendationItem(
                    "MEDIUM",
                    "Academics",
                    "Offset Below-Average Company CGPA",
                    "You meet the minimum cutoff, but selected hires at " + company.getCompanyName()
                            + " average " + round2(company.getHistoricalAverageSelectedCgpa())
                            + " CGPA. Compensate with stronger coding and project scores.",
                    "CGPA " + round2(student.getCgpa()),
                    "Peer avg " + round2(company.getHistoricalAverageSelectedCgpa())
            ));
        }
    }

    private void evaluatePeerScoreGaps(Application app, Student student, Job job,
                                       List<PeerBenchmark> benchmarks, List<RecommendationItem> items) {
        List<Object[]> rows = applicationRepository.findSelectedPeerAveragesByJobDomain(job.getJobDomain());
        if (rows == null || rows.isEmpty() || rows.get(0) == null || rows.get(0)[0] == null) {
            return;
        }
        Object[] avg = rows.get(0);
        double peerCgpa = toDouble(avg[0]);
        double peerAptitude = toDouble(avg[1]);
        double peerCoding = toDouble(avg[2]);
        double peerComm = toDouble(avg[3]);
        double peerResume = toDouble(avg[4]);

        addBenchmarkAndGap("CGPA", student.getCgpa(), peerCgpa, 0.3, null, null, benchmarks, items);
        addBenchmarkAndGap("Coding Score", student.getCodingScorePre(), peerCoding, 5.0,
                "Boost Coding & DSA Score",
                "Practice timed DSA and domain coding problems; selected peers in " + job.getJobDomain()
                        + " average " + round1(peerCoding) + ".",
                benchmarks, items);
        addBenchmarkAndGap("Aptitude Score", student.getAptitudeScorePre(), peerAptitude, 5.0,
                "Improve Pre-Interview Aptitude",
                "Practice quantitative, logical, and analytical aptitude tests to reach the selected peer average of "
                        + round1(peerAptitude) + ".",
                benchmarks, items);
        addBenchmarkAndGap("Communication Score", student.getCommunicationScorePre(), peerComm, 5.0,
                "Strengthen Communication & Interviewing",
                "Do mock HR/technical interviews and structured explanations to lift your communication score toward "
                        + round1(peerComm) + ".",
                benchmarks, items);
        addBenchmarkAndGap("Resume Score", student.getResumeScore(), peerResume, 5.0,
                "Upgrade Resume Impact",
                "Highlight measurable project outcomes and role keywords to raise your resume score toward "
                        + round1(peerResume) + ".",
                benchmarks, items);

        if (app.getSkillMatchRatio() != null) {
            double peerSkillMatch = toDouble(avg[7]) * 100.0;
            benchmarks.add(new PeerBenchmark(
                    "Skill Match %",
                    round1(app.getSkillMatchRatio() * 100.0),
                    round1(peerSkillMatch),
                    app.getSkillMatchRatio() * 100.0 >= peerSkillMatch - 5.0 ? "ON_TRACK" : "BELOW_PEER"
            ));
        }
    }

    private void addBenchmarkAndGap(String metric, Double studentVal, double peerAvg, double threshold,
                                    String recTitle, String recAction,
                                    List<PeerBenchmark> benchmarks, List<RecommendationItem> items) {
        if (studentVal == null || peerAvg <= 0.0) {
            return;
        }
        double gap = peerAvg - studentVal;
        String status = gap <= threshold ? "ON_TRACK" : "BELOW_PEER";
        benchmarks.add(new PeerBenchmark(metric, round2(studentVal), round2(peerAvg), status));

        if (recTitle != null && gap > threshold) {
            String priority = gap >= threshold * 2.4 ? "HIGH" : "MEDIUM";
            items.add(new RecommendationItem(
                    priority,
                    "Assessments",
                    recTitle,
                    recAction,
                    String.valueOf(round1(studentVal)),
                    "Peer avg " + round1(peerAvg)
            ));
        }
    }

    private void evaluateExperienceAndPortfolio(Application app, Student student, Job job,
                                                List<RecommendationItem> items) {
        int requiredMonths = job.getExperienceRequiredMonths() != null ? job.getExperienceRequiredMonths() : 0;
        double actualMonths = app.getRelevantExperienceMonths() != null ? app.getRelevantExperienceMonths() : 0.0;
        if (requiredMonths > 0 && actualMonths < requiredMonths) {
            String priority = actualMonths == 0 ? "HIGH" : "MEDIUM";
            items.add(new RecommendationItem(
                    priority,
                    "Experience",
                    "Build Relevant Domain Experience",
                    "This role asks for " + requiredMonths + " months of relevant experience. Complete an internship, "
                            + "freelance build, or industry-aligned capstone in " + job.getJobDomain() + ".",
                    round1(actualMonths) + " months",
                    requiredMonths + " months"
            ));
        }

        if (student.getProjectsCount() != null && student.getProjectsCount() < 2) {
            items.add(new RecommendationItem(
                    "MEDIUM",
                    "Portfolio",
                    "Add Practical Domain Projects",
                    "Build and deploy at least 2 projects demonstrating " + job.getJobDomain()
                            + " skills with clear GitHub documentation.",
                    student.getProjectsCount() + " project(s)",
                    "2+ projects"
            ));
        }

        if (student.getCertificationsCount() != null && student.getCertificationsCount() == 0) {
            items.add(new RecommendationItem(
                    "LOW",
                    "Credentials",
                    "Earn a Role-Relevant Certification",
                    "Complete a verified certification aligned with " + job.getJobDomain()
                            + " to strengthen pre-screening credibility.",
                    "0 certifications",
                    "1+ certification"
            ));
        }
    }

    private String buildSummaryHeadline(List<RecommendationItem> items, List<String> missingSkills, Job job) {
        if (items.isEmpty()) {
            return "Strong overall alignment with " + job.getJobTitle()
                    + " requirements and selected peer benchmarks in " + job.getJobDomain() + ".";
        }
        List<String> focusAreas = new ArrayList<>();
        if (!missingSkills.isEmpty()) {
            String preview = missingSkills.stream().limit(3).collect(Collectors.joining(", "));
            focusAreas.add("learn " + preview + (missingSkills.size() > 3 ? " (+" + (missingSkills.size() - 3) + " more)" : ""));
        }
        items.stream()
                .filter(i -> !"Skills".equals(i.getCategory()) && "HIGH".equals(i.getPriority()))
                .limit(2)
                .forEach(i -> focusAreas.add(i.getTitle().toLowerCase()));

        if (focusAreas.isEmpty()) {
            items.stream().limit(2).forEach(i -> focusAreas.add(i.getTitle().toLowerCase()));
        }
        return "To improve selection chances for " + job.getJobTitle() + ", prioritize: "
                + String.join("; ", focusAreas) + ".";
    }

    private int priorityRank(String priority) {
        if ("HIGH".equals(priority)) return 0;
        if ("MEDIUM".equals(priority)) return 1;
        return 2;
    }

    private double toDouble(Object val) {
        return val instanceof Number num ? num.doubleValue() : 0.0;
    }

    private double round1(double val) {
        return Math.round(val * 10.0) / 10.0;
    }

    private double round2(double val) {
        return Math.round(val * 100.0) / 100.0;
    }
}


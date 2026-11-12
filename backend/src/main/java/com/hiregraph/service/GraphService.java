package com.hiregraph.service;

import com.hiregraph.client.AiServiceClient;
import com.hiregraph.dto.response.GraphOverviewResponse;
import com.hiregraph.dto.response.SubgraphResponse;
import com.hiregraph.entity.*;
import com.hiregraph.repository.*;
import com.hiregraph.util.SkillCatalog;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class GraphService {

    private final AiServiceClient aiServiceClient;
    private final StudentRepository studentRepository;
    private final CompanyRepository companyRepository;
    private final JobRepository jobRepository;
    private final SkillRepository skillRepository;
    private final ApplicationRepository applicationRepository;
    private final StudentSkillRepository studentSkillRepository;
    private final JobSkillRepository jobSkillRepository;

    public GraphService(AiServiceClient aiServiceClient,
                        StudentRepository studentRepository,
                        CompanyRepository companyRepository,
                        JobRepository jobRepository,
                        SkillRepository skillRepository,
                        ApplicationRepository applicationRepository,
                        StudentSkillRepository studentSkillRepository,
                        JobSkillRepository jobSkillRepository) {
        this.aiServiceClient = aiServiceClient;
        this.studentRepository = studentRepository;
        this.companyRepository = companyRepository;
        this.jobRepository = jobRepository;
        this.skillRepository = skillRepository;
        this.applicationRepository = applicationRepository;
        this.studentSkillRepository = studentSkillRepository;
        this.jobSkillRepository = jobSkillRepository;
    }

    public GraphOverviewResponse getOverview() {
        Map<String, Integer> nodeCounts = new LinkedHashMap<>();
        nodeCounts.put("application", (int) applicationRepository.count());
        nodeCounts.put("student", (int) studentRepository.count());
        nodeCounts.put("company", (int) companyRepository.count());
        nodeCounts.put("job", (int) jobRepository.count());
        nodeCounts.put("skill", (int) skillRepository.count());

        List<String> relations = List.of(
                "APPLICATION_STUDENT", "STUDENT_APPLICATION",
                "APPLICATION_COMPANY", "COMPANY_APPLICATION",
                "APPLICATION_JOB", "JOB_APPLICATION",
                "HAS_SKILL", "REV_HAS_SKILL",
                "REQUIRES_SKILL", "REV_REQUIRES_SKILL",
                "OFFERED_BY", "REV_OFFERED_BY"
        );

        int totalNodes = nodeCounts.values().stream().mapToInt(Integer::intValue).sum();
        int totalEdges = 43044;

        return new GraphOverviewResponse(
                totalNodes,
                totalEdges,
                relations.size(),
                82,
                nodeCounts,
                relations
        );
    }

    @SuppressWarnings("unchecked")
    public SubgraphResponse getSubgraph(String type, String id, int maxNeighbors) {
        String cleanType = type != null ? type.toLowerCase().strip() : "";
        String resolvedId = resolveEntityId(cleanType, id);

        Map<String, Object> aiResult = aiServiceClient.getNeighborhood(cleanType, resolvedId, maxNeighbors);

        Map<String, Object> centerNode = new LinkedHashMap<>((Map<String, Object>) aiResult.get("center_node"));
        List<Map<String, Object>> rawNodes = (List<Map<String, Object>>) aiResult.getOrDefault("nodes", Collections.emptyList());
        List<Map<String, Object>> rawLinks = (List<Map<String, Object>>) aiResult.getOrDefault("links", Collections.emptyList());

        List<Map<String, Object>> nodes = new ArrayList<>();
        for (Map<String, Object> n : rawNodes) {
            nodes.add(new LinkedHashMap<>(n));
        }

        // Normalize and deduplicate bidirectional edges into clean, readable relationships
        List<Map<String, Object>> links = normalizeAndDeduplicateLinks(rawLinks);

        // For application-centered graphs, include 2-hop student & job skills so the user can see skill fit
        Set<String> matchedSkillIds = new LinkedHashSet<>();
        Set<String> missingSkillIds = new LinkedHashSet<>();
        if ("application".equals(cleanType)) {
            enrichApplicationSkillContext(resolvedId, nodes, links, matchedSkillIds, missingSkillIds);
        }

        // Attach human-readable labels and domain subtitles to all nodes
        enrichNodeMetadata(nodes, centerNode, matchedSkillIds, missingSkillIds);

        Map<String, Object> summary = buildSummary(cleanType, resolvedId, centerNode, nodes, links, matchedSkillIds, missingSkillIds);

        return new SubgraphResponse(centerNode, nodes.size(), links.size(), nodes, links, summary);
    }

    private String resolveEntityId(String type, String rawId) {
        String trimmed = rawId != null ? rawId.strip() : "";
        if (!"skill".equals(type) || trimmed.isEmpty()) {
            return trimmed;
        }
        // Allow querying by readable skill name (e.g., "Python") as well as ID (e.g., "SK_16")
        if (trimmed.toUpperCase().startsWith("SK_")) {
            return trimmed.toUpperCase();
        }
        Optional<Skill> dbMatch = skillRepository.findBySkillNameIgnoreCase(trimmed);
        if (dbMatch.isPresent()) {
            return dbMatch.get().getSkillId();
        }
        return SkillCatalog.allSkillNames().entrySet().stream()
                .filter(e -> e.getValue().equalsIgnoreCase(trimmed))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(trimmed);
    }

    private List<Map<String, Object>> normalizeAndDeduplicateLinks(List<Map<String, Object>> rawLinks) {
        Map<String, Map<String, Object>> unique = new LinkedHashMap<>();

        for (Map<String, Object> link : rawLinks) {
            String src = String.valueOf(link.get("source"));
            String srcType = String.valueOf(link.getOrDefault("source_type", ""));
            String dst = String.valueOf(link.get("target"));
            String dstType = String.valueOf(link.getOrDefault("target_type", ""));
            String rawRel = String.valueOf(link.getOrDefault("relation", ""));

            CanonicalEdge canonical = toCanonicalEdge(src, srcType, dst, dstType, rawRel);
            String key = canonical.source + "->" + canonical.target + ":" + canonical.label;

            if (!unique.containsKey(key)) {
                Map<String, Object> normalized = new LinkedHashMap<>();
                normalized.put("source", canonical.source);
                normalized.put("source_type", canonical.sourceType);
                normalized.put("target", canonical.target);
                normalized.put("target_type", canonical.targetType);
                normalized.put("relation", canonical.label);
                normalized.put("raw_relation", rawRel);
                normalized.put("relation_id", link.get("relation_id"));
                unique.put(key, normalized);
            }
        }
        return new ArrayList<>(unique.values());
    }

    private CanonicalEdge toCanonicalEdge(String src, String srcType, String dst, String dstType, String rel) {
        return switch (rel) {
            case "APPLICATION_STUDENT" -> new CanonicalEdge(src, srcType, dst, dstType, "Applied By");
            case "STUDENT_APPLICATION" -> new CanonicalEdge(dst, dstType, src, srcType, "Applied By");
            case "APPLICATION_COMPANY" -> new CanonicalEdge(src, srcType, dst, dstType, "Applied To");
            case "COMPANY_APPLICATION" -> new CanonicalEdge(dst, dstType, src, srcType, "Applied To");
            case "APPLICATION_JOB" -> new CanonicalEdge(src, srcType, dst, dstType, "For Role");
            case "JOB_APPLICATION" -> new CanonicalEdge(dst, dstType, src, srcType, "For Role");
            case "HAS_SKILL" -> new CanonicalEdge(src, srcType, dst, dstType, "Has Skill");
            case "REV_HAS_SKILL" -> new CanonicalEdge(dst, dstType, src, srcType, "Has Skill");
            case "REQUIRES_SKILL" -> new CanonicalEdge(src, srcType, dst, dstType, "Requires Skill");
            case "REV_REQUIRES_SKILL" -> new CanonicalEdge(dst, dstType, src, srcType, "Requires Skill");
            case "OFFERED_BY" -> new CanonicalEdge(src, srcType, dst, dstType, "Offered By");
            case "REV_OFFERED_BY" -> new CanonicalEdge(dst, dstType, src, srcType, "Offered By");
            default -> new CanonicalEdge(src, srcType, dst, dstType, rel);
        };
    }

    private void enrichApplicationSkillContext(String applicationId,
                                               List<Map<String, Object>> nodes,
                                               List<Map<String, Object>> links,
                                               Set<String> matchedSkillIds,
                                               Set<String> missingSkillIds) {
        Optional<Application> appOpt = applicationRepository.findByApplicationId(applicationId);
        if (appOpt.isEmpty()) {
            return;
        }
        Application app = appOpt.get();

        Set<String> existingNodeKeys = nodes.stream()
                .map(n -> n.get("type") + ":" + n.get("id"))
                .collect(Collectors.toSet());
        Set<String> existingLinkKeys = links.stream()
                .map(l -> l.get("source") + "->" + l.get("target") + ":" + l.get("relation"))
                .collect(Collectors.toSet());

        List<String> studentSkillIds = studentSkillRepository.findByStudentId(app.getStudentId()).stream()
                .map(StudentSkill::getSkillId)
                .distinct()
                .toList();
        List<String> jobSkillIds = jobSkillRepository.findByJobId(app.getJobId()).stream()
                .map(JobSkill::getSkillId)
                .distinct()
                .toList();

        Set<String> studentSkillSet = new HashSet<>(studentSkillIds);
        for (String skillId : jobSkillIds) {
            if (studentSkillSet.contains(skillId)) {
                matchedSkillIds.add(skillId);
            } else {
                missingSkillIds.add(skillId);
            }
        }

        // Add Job -> Skill ("Requires Skill") edges and skill nodes
        for (String skillId : jobSkillIds) {
            addNodeIfAbsent(nodes, existingNodeKeys, "skill", skillId);
            addLinkIfAbsent(links, existingLinkKeys, app.getJobId(), "job", skillId, "skill", "Requires Skill", "REQUIRES_SKILL");
        }

        // Add Student -> Skill ("Has Skill") edges and skill nodes (capped to keep canvas readable)
        int addedStudentOnly = 0;
        for (String skillId : studentSkillIds) {
            boolean isRequired = jobSkillIds.contains(skillId);
            if (!isRequired && addedStudentOnly >= 6) {
                continue;
            }
            if (!isRequired) {
                addedStudentOnly++;
            }
            addNodeIfAbsent(nodes, existingNodeKeys, "skill", skillId);
            addLinkIfAbsent(links, existingLinkKeys, app.getStudentId(), "student", skillId, "skill", "Has Skill", "HAS_SKILL");
        }

        // Also show Job -> Company ("Offered By") if both exist
        addLinkIfAbsent(links, existingLinkKeys, app.getJobId(), "job", app.getCompanyId(), "company", "Offered By", "OFFERED_BY");
    }

    private void addNodeIfAbsent(List<Map<String, Object>> nodes, Set<String> existingNodeKeys,
                                 String type, String id) {
        String key = type + ":" + id;
        if (existingNodeKeys.add(key)) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", id);
            node.put("type", type);
            node.put("is_center", false);
            nodes.add(node);
        }
    }

    private void addLinkIfAbsent(List<Map<String, Object>> links, Set<String> existingLinkKeys,
                                 String src, String srcType, String dst, String dstType,
                                 String label, String rawRel) {
        String key = src + "->" + dst + ":" + label;
        if (existingLinkKeys.add(key)) {
            Map<String, Object> link = new LinkedHashMap<>();
            link.put("source", src);
            link.put("source_type", srcType);
            link.put("target", dst);
            link.put("target_type", dstType);
            link.put("relation", label);
            link.put("raw_relation", rawRel);
            links.add(link);
        }
    }

    private void enrichNodeMetadata(List<Map<String, Object>> nodes,
                                    Map<String, Object> centerNode,
                                    Set<String> matchedSkillIds,
                                    Set<String> missingSkillIds) {
        Set<String> studentIds = collectIds(nodes, "student");
        Set<String> companyIds = collectIds(nodes, "company");
        Set<String> jobIds = collectIds(nodes, "job");
        Set<String> appIds = collectIds(nodes, "application");

        Map<String, Student> students = studentIds.isEmpty() ? Map.of()
                : studentRepository.findByStudentIdIn(studentIds).stream()
                .collect(Collectors.toMap(Student::getStudentId, s -> s, (a, b) -> a));
        Map<String, Company> companies = companyIds.isEmpty() ? Map.of()
                : companyRepository.findByCompanyIdIn(companyIds).stream()
                .collect(Collectors.toMap(Company::getCompanyId, c -> c, (a, b) -> a));
        Map<String, Job> jobs = jobIds.isEmpty() ? Map.of()
                : jobRepository.findByJobIdIn(jobIds).stream()
                .collect(Collectors.toMap(Job::getJobId, j -> j, (a, b) -> a));
        Map<String, Application> applications = appIds.isEmpty() ? Map.of()
                : applicationRepository.findByApplicationIdIn(appIds).stream()
                .collect(Collectors.toMap(Application::getApplicationId, a -> a, (a, b) -> a));
        Map<String, String> skillNames = skillRepository.findAll().stream()
                .collect(Collectors.toMap(Skill::getSkillId, Skill::getSkillName, (a, b) -> a));

        for (Map<String, Object> node : nodes) {
            String type = String.valueOf(node.get("type")).toLowerCase();
            String id = String.valueOf(node.get("id"));
            applyMetadata(node, type, id, students, companies, jobs, applications, skillNames, matchedSkillIds, missingSkillIds);
        }

        if (centerNode != null && centerNode.get("type") != null && centerNode.get("id") != null) {
            applyMetadata(centerNode,
                    String.valueOf(centerNode.get("type")).toLowerCase(),
                    String.valueOf(centerNode.get("id")),
                    students, companies, jobs, applications, skillNames, matchedSkillIds, missingSkillIds);
        }
    }

    private void applyMetadata(Map<String, Object> node, String type, String id,
                               Map<String, Student> students, Map<String, Company> companies,
                               Map<String, Job> jobs, Map<String, Application> applications,
                               Map<String, String> skillNames,
                               Set<String> matchedSkillIds, Set<String> missingSkillIds) {
        switch (type) {
            case "skill" -> {
                String name = skillNames.getOrDefault(id, SkillCatalog.resolveName(id));
                node.put("label", name);
                node.put("displayName", name + " (" + id + ")");
                if (matchedSkillIds.contains(id)) {
                    node.put("subtitle", "Matched Skill (Student has & Job requires)");
                    node.put("skillMatchStatus", "MATCHED");
                } else if (missingSkillIds.contains(id)) {
                    node.put("subtitle", "Missing Skill (Job requires)");
                    node.put("skillMatchStatus", "MISSING");
                } else {
                    node.put("subtitle", "Skill Entity");
                }
            }
            case "job" -> {
                Job j = jobs.get(id);
                if (j != null) {
                    node.put("label", j.getJobTitle());
                    node.put("displayName", j.getJobTitle() + " (" + id + ")");
                    node.put("subtitle", j.getJobDomain() + " • Min CGPA " + j.getMinimumCgpa() + " • " + j.getSalaryLpa() + " LPA");
                } else {
                    node.put("label", id);
                    node.put("displayName", "Job " + id);
                }
            }
            case "student" -> {
                Student s = students.get(id);
                if (s != null) {
                    node.put("label", id + " (" + s.getDepartment() + ")");
                    node.put("displayName", "Student " + id);
                    node.put("subtitle", s.getDepartment() + " • CGPA " + s.getCgpa() + " • " + s.getProjectsCount() + " projects");
                } else {
                    node.put("label", id);
                    node.put("displayName", "Student " + id);
                }
            }
            case "company" -> {
                Company c = companies.get(id);
                if (c != null) {
                    node.put("label", id + " (" + c.getIndustry() + ")");
                    node.put("displayName", c.getCompanyName());
                    node.put("subtitle", c.getIndustry() + " • " + c.getCompanySize()
                            + " • " + Math.round(c.getHistoricalSelectionRate() * 100) + "% sel. rate");
                } else {
                    node.put("label", id);
                    node.put("displayName", "Company " + id);
                }
            }
            case "application" -> {
                Application a = applications.get(id);
                if (a != null) {
                    String statusLabel = a.getFinalStatus() == 1 ? "Selected" : "Rejected";
                    node.put("label", id + " (" + statusLabel + ")");
                    node.put("displayName", "Application " + id);
                    node.put("subtitle", "Cycle " + a.getCycle() + " • " + statusLabel
                            + " • " + Math.round(a.getSkillMatchRatio() * 100) + "% skill match");
                } else {
                    node.put("label", id);
                    node.put("displayName", "Application " + id);
                }
            }
            default -> {
                node.put("label", id);
                node.put("displayName", id);
            }
        }
    }

    private Map<String, Object> buildSummary(String type, String id,
                                             Map<String, Object> centerNode,
                                             List<Map<String, Object>> nodes,
                                             List<Map<String, Object>> links,
                                             Set<String> matchedSkillIds,
                                             Set<String> missingSkillIds) {
        Map<String, Object> summary = new LinkedHashMap<>();
        String centerName = String.valueOf(centerNode.getOrDefault("displayName", id));
        String centerSub = String.valueOf(centerNode.getOrDefault("subtitle", ""));

        Map<String, String> skillNames = skillRepository.findAll().stream()
                .collect(Collectors.toMap(Skill::getSkillId, Skill::getSkillName, (a, b) -> a));

        List<String> highlights = new ArrayList<>();
        if ("application".equals(type)) {
            summary.put("title", centerName + (centerSub.isBlank() ? "" : " — " + centerSub));
            summary.put("description", "Visualizes how this application connects the candidate, target job, hiring company, "
                    + "and both the candidate's skills and the job's required skills.");
            if (!matchedSkillIds.isEmpty()) {
                highlights.add("Matched Skills (" + matchedSkillIds.size() + "): " + joinSkillNames(matchedSkillIds, skillNames));
            }
            if (!missingSkillIds.isEmpty()) {
                highlights.add("Missing Required Skills (" + missingSkillIds.size() + "): " + joinSkillNames(missingSkillIds, skillNames));
            }
        } else if ("skill".equals(type)) {
            long studentNeighbors = nodes.stream().filter(n -> "student".equals(n.get("type"))).count();
            long jobNeighbors = nodes.stream().filter(n -> "job".equals(n.get("type"))).count();
            summary.put("title", centerName);
            summary.put("description", "Shows students who possess " + centerNode.getOrDefault("label", id)
                    + " and job roles that require it (up to the current neighbor limit).");
            highlights.add("Sampled connected students: " + studentNeighbors + " • jobs requiring it: " + jobNeighbors);
        } else {
            summary.put("title", centerName + (centerSub.isBlank() ? "" : " — " + centerSub));
            summary.put("description", "Showing " + nodes.size() + " connected entities and " + links.size()
                    + " unique relationships around this " + type + ".");
            Map<String, Long> typeCounts = nodes.stream()
                    .filter(n -> !Boolean.TRUE.equals(n.get("is_center")))
                    .collect(Collectors.groupingBy(n -> String.valueOf(n.get("type")), Collectors.counting()));
            typeCounts.forEach((t, count) -> highlights.add("Connected " + t + " nodes: " + count));
        }

        summary.put("highlights", highlights);
        return summary;
    }

    private String joinSkillNames(Set<String> skillIds, Map<String, String> skillNames) {
        return skillIds.stream()
                .map(sid -> skillNames.getOrDefault(sid, SkillCatalog.resolveName(sid)))
                .collect(Collectors.joining(", "));
    }

    private Set<String> collectIds(List<Map<String, Object>> nodes, String type) {
        return nodes.stream()
                .filter(n -> type.equalsIgnoreCase(String.valueOf(n.get("type"))))
                .map(n -> String.valueOf(n.get("id")))
                .collect(Collectors.toSet());
    }

    private record CanonicalEdge(String source, String sourceType, String target, String targetType, String label) {}
}

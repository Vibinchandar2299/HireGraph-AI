package com.hiregraph.util;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Canonical mapping from anonymized dataset skill IDs (SK_01..SK_18)
 * to human-understandable skill names aligned with each skill's job-domain usage.
 */
public final class SkillCatalog {

    private static final Map<String, String> SKILL_NAMES;

    static {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("SK_01", "C / C++");
        map.put("SK_02", "AWS Cloud");
        map.put("SK_03", "Network Security");
        map.put("SK_04", "Data Visualization");
        map.put("SK_05", "Machine Learning");
        map.put("SK_06", "Docker & Kubernetes");
        map.put("SK_07", "Data Structures & Algorithms");
        map.put("SK_08", "Java & Spring Boot");
        map.put("SK_09", "CI/CD & DevOps");
        map.put("SK_10", "Linux & Networking");
        map.put("SK_11", "Deep Learning");
        map.put("SK_12", "Power BI & Tableau");
        map.put("SK_13", "Git & Version Control");
        map.put("SK_14", "SQL & Databases");
        map.put("SK_15", "React & Web Development");
        map.put("SK_16", "Python");
        map.put("SK_17", "System Design & APIs");
        map.put("SK_18", "Statistics & Excel");
        SKILL_NAMES = Collections.unmodifiableMap(map);
    }

    private SkillCatalog() {
    }

    public static String resolveName(String skillId) {
        if (skillId == null) {
            return "Unknown Skill";
        }
        String normalized = skillId.trim().toUpperCase();
        return SKILL_NAMES.getOrDefault(normalized, "Skill " + normalized);
    }

    public static Map<String, String> allSkillNames() {
        return SKILL_NAMES;
    }
}

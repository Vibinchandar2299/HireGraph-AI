-- ====================================================================
-- HireGraph AI - Flyway Database Migration V2
-- Maps anonymized skill IDs (SK_01..SK_18) to human-understandable names
-- aligned with their domain distribution in the placement dataset.
-- ====================================================================

UPDATE skills SET skill_name = 'C / C++' WHERE skill_id = 'SK_01';
UPDATE skills SET skill_name = 'AWS Cloud' WHERE skill_id = 'SK_02';
UPDATE skills SET skill_name = 'Network Security' WHERE skill_id = 'SK_03';
UPDATE skills SET skill_name = 'Data Visualization' WHERE skill_id = 'SK_04';
UPDATE skills SET skill_name = 'Machine Learning' WHERE skill_id = 'SK_05';
UPDATE skills SET skill_name = 'Docker & Kubernetes' WHERE skill_id = 'SK_06';
UPDATE skills SET skill_name = 'Data Structures & Algorithms' WHERE skill_id = 'SK_07';
UPDATE skills SET skill_name = 'Java & Spring Boot' WHERE skill_id = 'SK_08';
UPDATE skills SET skill_name = 'CI/CD & DevOps' WHERE skill_id = 'SK_09';
UPDATE skills SET skill_name = 'Linux & Networking' WHERE skill_id = 'SK_10';
UPDATE skills SET skill_name = 'Deep Learning' WHERE skill_id = 'SK_11';
UPDATE skills SET skill_name = 'Power BI & Tableau' WHERE skill_id = 'SK_12';
UPDATE skills SET skill_name = 'Git & Version Control' WHERE skill_id = 'SK_13';
UPDATE skills SET skill_name = 'SQL & Databases' WHERE skill_id = 'SK_14';
UPDATE skills SET skill_name = 'React & Web Development' WHERE skill_id = 'SK_15';
UPDATE skills SET skill_name = 'Python' WHERE skill_id = 'SK_16';
UPDATE skills SET skill_name = 'System Design & APIs' WHERE skill_id = 'SK_17';
UPDATE skills SET skill_name = 'Statistics & Excel' WHERE skill_id = 'SK_18';

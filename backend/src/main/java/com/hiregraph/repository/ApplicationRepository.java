package com.hiregraph.repository;

import com.hiregraph.entity.Application;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ApplicationRepository extends JpaRepository<Application, Long> {
    Optional<Application> findByApplicationId(String applicationId);
    List<Application> findByApplicationIdIn(Collection<String> applicationIds);
    boolean existsByApplicationId(String applicationId);

    List<Application> findByStudentId(String studentId);
    List<Application> findByCompanyId(String companyId);
    List<Application> findByJobId(String jobId);

    Page<Application> findByCycle(Integer cycle, Pageable pageable);
    Page<Application> findByFinalStatus(Integer finalStatus, Pageable pageable);
    Page<Application> findByCycleAndFinalStatus(Integer cycle, Integer finalStatus, Pageable pageable);

    long countByFinalStatus(Integer finalStatus);
    long countByCycle(Integer cycle);
    long countByCycleAndFinalStatus(Integer cycle, Integer finalStatus);

    @Query("SELECT DISTINCT a.cycle FROM Application a ORDER BY a.cycle ASC")
    List<Integer> findDistinctCycles();

    @Query("SELECT a.cycle, a.finalStatus, COUNT(a) FROM Application a GROUP BY a.cycle, a.finalStatus ORDER BY a.cycle ASC, a.finalStatus ASC")
    List<Object[]> countGroupedByCycleAndFinalStatus();

    @Query("""
            SELECT AVG(s.cgpa), AVG(s.aptitudeScorePre), AVG(s.codingScorePre),
                   AVG(s.communicationScorePre), AVG(s.resumeScore),
                   AVG(s.projectsCount), AVG(s.internshipsCount), AVG(a.skillMatchRatio)
            FROM Application a
            JOIN Student s ON a.studentId = s.studentId
            JOIN Job j ON a.jobId = j.jobId
            WHERE a.finalStatus = 1 AND j.jobDomain = :jobDomain
            """)
    List<Object[]> findSelectedPeerAveragesByJobDomain(@Param("jobDomain") String jobDomain);
}

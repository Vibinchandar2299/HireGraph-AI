package com.hiregraph.repository;

import com.hiregraph.entity.Student;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {
    Optional<Student> findByStudentId(String studentId);
    List<Student> findByStudentIdIn(Collection<String> studentIds);
    boolean existsByStudentId(String studentId);
    Page<Student> findByDepartmentIgnoreCase(String department, Pageable pageable);
}

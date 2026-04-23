package com.trident.placement.repository;

import com.trident.placement.entity.StudentCareer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for STUDENT_CAREER table.
 * Used to fetch a student's academic marks for drive eligibility checks.
 */
@Repository
public interface StudentCareerRepository extends JpaRepository<StudentCareer, String> {

    Optional<StudentCareer> findByRegdno(String regdno);
}
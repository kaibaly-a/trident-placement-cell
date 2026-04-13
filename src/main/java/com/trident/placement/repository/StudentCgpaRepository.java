package com.trident.placement.repository;

import com.trident.placement.entity.StudentCgpa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentCgpaRepository extends JpaRepository<StudentCgpa, Long> {

    /**
     * Find cached CGPA for a student.
     * Returns empty if CGPA has never been fetched from BPUT.
     */
    Optional<StudentCgpa> findByRegdno(String regdno);

    boolean existsByRegdno(String regdno);
    
    @Query("SELECT s FROM StudentCgpa s WHERE s.regdno IN :regdnos")
    List<StudentCgpa> findByRegdnoIn(@Param("regdnos") List<String> regdnos);
}
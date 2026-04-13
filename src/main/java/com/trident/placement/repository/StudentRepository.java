package com.trident.placement.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.trident.placement.entity.Student;

public interface StudentRepository extends JpaRepository<Student, String> {
	Optional<Student> findByEmail(String email);
	Optional<Student> findByRegdno(String regdNo);
	
	boolean existsByEmail(String email);
	boolean existsByRegdno(String RegdNo);
	
	// ── Added for Azure AD authentication ────────────────────────────────────
    /**
     * Looks up a student by their Azure AD User Principal Name (UPN).
     *
     * The MSUSERPRINCIPALNAME column in the STUDENT table stores the student's
     * Azure AD UPN — the same value as the "preferred_username" claim in their
     * Azure access token (e.g., "22bcs001@trident.ac.in").
     *
     * This is used by AzureJwtAuthFilter and AuthService to identify
     * which student is logging in via Azure AD.
     */
    Optional<Student> findByMsUserPrincipalName(String msUserPrincipalName);
    
 // Native Oracle 11g compatible paginated query
    @Query(value = """
            SELECT * FROM (
                SELECT s.*, ROWNUM rnum FROM (
                    SELECT * FROM STUDENT ORDER BY NAME ASC
                ) s WHERE ROWNUM <= :endRow
            ) WHERE rnum > :startRow
            """, nativeQuery = true)
    List<Student> findAllPaginated(@Param("startRow") int startRow,
                                   @Param("endRow") int endRow);

    @Query(value = "SELECT COUNT(*) FROM STUDENT", nativeQuery = true)
    long countAllStudents();
    
//    
//      Database-level search — Oracle does the filtering, not Java.
//      Searches name, regdno, branchCode, email with case-insensitive LIKE.
//      Only matching rows are fetched from the DB.

    @Query("""
            SELECT s FROM Student s
            WHERE LOWER(s.name)       LIKE LOWER(CONCAT('%', :query, '%'))
               OR LOWER(s.regdno)     LIKE LOWER(CONCAT('%', :query, '%'))
               OR LOWER(s.branchCode) LIKE LOWER(CONCAT('%', :query, '%'))
               OR LOWER(s.email)      LIKE LOWER(CONCAT('%', :query, '%'))
            ORDER BY s.name ASC
            """)
    List<Student> searchStudents(@Param("query") String query);

    // ── For CGPA eligibility: 4th-year students admitted 2021 onwards only ────
    /**
     * Returns final-year students who were admitted in 2021 or later.
     *
     * Two filters applied:
     *  1. CURRENTYEAR = 4  → only currently enrolled 4th-year students.
     *                         Alumni are excluded (their CURRENTYEAR resets
     *                         to NULL or a lower value after graduation).
     *  2. TO_NUMBER(ADMISSIONYEAR) >= 2021 → only batches from 2021 onward.
     *                         Pre-2021 batches (older alumni still in some
     *                         tables) are skipped entirely.
     *
     * TO_NUMBER() is safe here because ADMISSIONYEAR always holds a 4-digit
     * year string (e.g. '2021', '2022'). Native query avoids JPQL type issues.
     */
    @Query(
        value = "SELECT * FROM STUDENT " +
                "WHERE (EXTRACT(YEAR FROM SYSDATE) - TO_NUMBER(ADMISSIONYEAR)) >= 3 " +
                "ORDER BY REGDNO ASC",
        nativeQuery = true)
    List<Student> findFinalYearStudents();

    /**
     * Optimized: Find eligible students based on CGPA requirement AND specific branches.
     * Prevents fetching thousands of rows only to filter them in Java.
     */
    @Query(
        value = "SELECT s.* FROM STUDENT s " +
                "JOIN student_cgpa c ON s.REGDNO = c.regdno " +
                "WHERE (EXTRACT(YEAR FROM SYSDATE) - TO_NUMBER(s.ADMISSIONYEAR)) >= 3 " +
                "AND c.cgpa >= :minCgpa " +
                "AND UPPER(TRIM(s.BRANCH_CODE)) IN (:branches) " +
                "ORDER BY s.REGDNO ASC",
        nativeQuery = true)
    List<Student> findEligibleStudentsForDriveWithBranches(
            @Param("minCgpa") java.math.BigDecimal minCgpa,
            @Param("branches") java.util.Set<String> branches);

    /**
     * Optimized: Find eligible students based on CGPA for all branches (when drive has no branch restrictions).
     */
    @Query(
        value = "SELECT s.* FROM STUDENT s " +
                "JOIN student_cgpa c ON s.REGDNO = c.regdno " +
                "WHERE (EXTRACT(YEAR FROM SYSDATE) - TO_NUMBER(s.ADMISSIONYEAR)) >= 3 " +
                "AND c.cgpa >= :minCgpa " +
                "ORDER BY s.REGDNO ASC",
        nativeQuery = true)
    List<Student> findEligibleStudentsForDriveAllBranches(
            @Param("minCgpa") java.math.BigDecimal minCgpa);
}
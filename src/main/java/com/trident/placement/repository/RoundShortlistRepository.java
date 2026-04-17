package com.trident.placement.repository;

import com.trident.placement.entity.RoundShortlist;
import com.trident.placement.enums.ShortlistStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoundShortlistRepository extends JpaRepository<RoundShortlist, Long> {

    // ── Find by application + round (unique) ──────────────────────────────────

    @Query("""
            SELECT rs FROM RoundShortlist rs
            WHERE rs.application.id = :applicationId
              AND rs.round = :round
            """)
    Optional<RoundShortlist> findByApplicationIdAndRound(
            @Param("applicationId") Long applicationId,
            @Param("round") String round);

    // ── All entries for a drive ────────────────────────────────────────────────

    @Query("""
            SELECT rs FROM RoundShortlist rs
            JOIN FETCH rs.application a
            WHERE rs.drive.id = :driveId
            ORDER BY a.id
            """)
    List<RoundShortlist> findByDriveId(@Param("driveId") Long driveId);

    // ── All entries for a drive + specific round ──────────────────────────────

    @Query("""
            SELECT rs FROM RoundShortlist rs
            JOIN FETCH rs.application a
            WHERE rs.drive.id = :driveId
              AND rs.round = :round
            ORDER BY a.id
            """)
    List<RoundShortlist> findByDriveIdAndRound(
            @Param("driveId") Long driveId,
            @Param("round") String round);

    // ── All entries for a drive + round + status ───────────────────────────────

    @Query("""
            SELECT rs FROM RoundShortlist rs
            JOIN FETCH rs.application a
            WHERE rs.drive.id = :driveId
              AND rs.round = :round
              AND rs.status = :status
            ORDER BY a.id
            """)
    List<RoundShortlist> findByDriveIdAndRoundAndStatus(
            @Param("driveId") Long driveId,
            @Param("round") String round,
            @Param("status") ShortlistStatus status);

    // ── All entries for a drive + status (any round) ──────────────────────────

    @Query("""
            SELECT rs FROM RoundShortlist rs
            JOIN FETCH rs.application a
            WHERE rs.drive.id = :driveId
              AND rs.status = :status
            ORDER BY a.id
            """)
    List<RoundShortlist> findByDriveIdAndStatus(
            @Param("driveId") Long driveId,
            @Param("status") ShortlistStatus status);

    // ── Counts per round (for summary) ────────────────────────────────────────

    /**
     * Returns Object[] { round, status, COUNT } for all rows of a drive.
     * Used to build the round-by-round summary in one query.
     */
    @Query("""
            SELECT rs.round, rs.status, COUNT(rs.id)
            FROM RoundShortlist rs
            WHERE rs.drive.id = :driveId
            GROUP BY rs.round, rs.status
            """)
    List<Object[]> countByDriveIdGroupByRoundAndStatus(@Param("driveId") Long driveId);

    // ── Total applications in a drive (denominator for pass rate) ─────────────

    @Query("""
            SELECT COUNT(DISTINCT a.id) FROM Application a
            WHERE a.drive.id = :driveId
            """)
    long countTotalApplicationsByDriveId(@Param("driveId") Long driveId);

    // ── Check existence ───────────────────────────────────────────────────────

    @Query("""
            SELECT COUNT(rs) > 0 FROM RoundShortlist rs
            WHERE rs.application.id = :applicationId
              AND rs.round = :round
            """)
    boolean existsByApplicationIdAndRound(
            @Param("applicationId") Long applicationId,
            @Param("round") String round);

    // ── All entries for a specific application (student view) ─────────────────

    @Query("""
            SELECT rs FROM RoundShortlist rs
            WHERE rs.application.id = :applicationId
            ORDER BY rs.createdAt
            """)
    List<RoundShortlist> findByApplicationId(@Param("applicationId") Long applicationId);

    // ── Bulk delete for a drive (used on drive delete) ────────────────────────

    @Modifying
    @Query("DELETE FROM RoundShortlist rs WHERE rs.drive.id = :driveId")
    void deleteByDriveId(@Param("driveId") Long driveId);

    // ── Distinct round names configured for a drive ───────────────────────────

    @Query("""
            SELECT DISTINCT rs.round FROM RoundShortlist rs
            WHERE rs.drive.id = :driveId
            ORDER BY rs.round
            """)
    List<String> findDistinctRoundsByDriveId(@Param("driveId") Long driveId);
}

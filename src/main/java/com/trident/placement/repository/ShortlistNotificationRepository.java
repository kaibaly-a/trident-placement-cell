package com.trident.placement.repository;

import com.trident.placement.entity.ShortlistNotification;
import com.trident.placement.enums.ShortlistStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShortlistNotificationRepository extends JpaRepository<ShortlistNotification, Long> {

    @Query("""
            SELECT sn FROM ShortlistNotification sn
            WHERE sn.application.id = :applicationId
            ORDER BY sn.createdAt DESC
            """)
    List<ShortlistNotification> findByApplicationId(@Param("applicationId") Long applicationId);

    @Query("""
            SELECT sn FROM ShortlistNotification sn
            WHERE sn.application.id = :applicationId
              AND sn.round = :round
              AND sn.statusNotified = :status
            """)
    List<ShortlistNotification> findByApplicationIdAndRoundAndStatus(
            @Param("applicationId") Long applicationId,
            @Param("round") String round,
            @Param("status") ShortlistStatus status);

    @Query("""
            SELECT COUNT(sn) > 0 FROM ShortlistNotification sn
            WHERE sn.application.id = :applicationId
              AND sn.round = :round
              AND sn.statusNotified = :status
            """)
    boolean existsByApplicationIdAndRoundAndStatus(
            @Param("applicationId") Long applicationId,
            @Param("round") String round,
            @Param("status") ShortlistStatus status);

    @Query("""
            SELECT sn FROM ShortlistNotification sn
            JOIN FETCH sn.application a
            JOIN FETCH a.drive d
            WHERE a.regdno = :regdno
            ORDER BY sn.sentAt DESC
            """)
    List<ShortlistNotification> findByApplication_Regdno(@Param("regdno") String regdno);
}

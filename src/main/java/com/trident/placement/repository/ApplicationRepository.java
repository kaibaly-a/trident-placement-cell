package com.trident.placement.repository;

import com.trident.placement.entity.Application;
import com.trident.placement.enums.ApplicationStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApplicationRepository extends JpaRepository<Application, Long> {

    List<Application> findByRegdnoOrderByAppliedDateDesc(String regdno);

    List<Application> findByRegdnoAndStatus(String regdno, ApplicationStatus status);

    Optional<Application> findByRegdnoAndDrive_Id(String regdno, Long driveId);

//    boolean existsByRegdnoAndDrive_Id(String regdno, Long driveId);
    
    @Query("""
    		SELECT COUNT(a) > 0 
    		FROM Application a 
    		WHERE a.regdno = :regdno 
    		AND a.drive.id = :driveId
    		""")
    		boolean existsApplication(@Param("regdno") String regdno,
    		                          @Param("driveId") Long driveId);

    @Query("SELECT COUNT(a) FROM Application a WHERE a.regdno = :regdno")
    long countByRegdno(@Param("regdno") String regdno);

    @Query("SELECT COUNT(a) FROM Application a WHERE a.regdno = :regdno AND a.status = :status")
    long countByRegdnoAndStatus(@Param("regdno") String regdno,
                                @Param("status") ApplicationStatus status);
}

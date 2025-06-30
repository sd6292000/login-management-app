package com.wilsonkeh.loginmanagement.repository;

import com.wilsonkeh.loginmanagement.entity.UserSecurityAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserSecurityAnalysisRepository extends JpaRepository<UserSecurityAnalysis, Long> {

    @Query("SELECT a FROM UserSecurityAnalysis a WHERE a.uid = :uid ORDER BY a.analysisDate DESC")
    List<UserSecurityAnalysis> findByUidOrderByAnalysisDateDesc(@Param("uid") String uid);

    @Query("SELECT a FROM UserSecurityAnalysis a WHERE a.uid = :uid AND a.analysisDate >= :startDate ORDER BY a.analysisDate DESC")
    List<UserSecurityAnalysis> findByUidAndAnalysisDateAfterOrderByAnalysisDateDesc(@Param("uid") String uid, @Param("startDate") LocalDateTime startDate);

    @Query("SELECT a FROM UserSecurityAnalysis a WHERE a.uid = :uid ORDER BY a.analysisDate DESC LIMIT 1")
    Optional<UserSecurityAnalysis> findLatestAnalysisByUid(@Param("uid") String uid);

    @Query("SELECT a FROM UserSecurityAnalysis a WHERE a.riskLevel = :riskLevel ORDER BY a.analysisDate DESC")
    List<UserSecurityAnalysis> findByRiskLevelOrderByAnalysisDateDesc(@Param("riskLevel") UserSecurityAnalysis.RiskLevel riskLevel);

    @Query("SELECT a FROM UserSecurityAnalysis a WHERE a.uid IN :uids ORDER BY a.analysisDate DESC")
    List<UserSecurityAnalysis> findByUidsOrderByAnalysisDateDesc(@Param("uids") List<String> uids);

    @Query("SELECT a FROM UserSecurityAnalysis a WHERE a.suspiciousActivities > 0 ORDER BY a.suspiciousActivities DESC, a.analysisDate DESC")
    List<UserSecurityAnalysis> findUsersWithSuspiciousActivities();

    @Query("SELECT a FROM UserSecurityAnalysis a WHERE a.avgRiskScore > :threshold ORDER BY a.avgRiskScore DESC, a.analysisDate DESC")
    List<UserSecurityAnalysis> findUsersWithHighRiskScore(@Param("threshold") Double threshold);
} 
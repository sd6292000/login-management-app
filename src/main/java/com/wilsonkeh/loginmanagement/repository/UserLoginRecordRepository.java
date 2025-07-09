package com.wilsonkeh.loginmanagement.repository;

import com.wilsonkeh.loginmanagement.entity.UserLoginRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserLoginRecordRepository extends JpaRepository<UserLoginRecord, Long> {

    @Query("SELECT l FROM UserLoginRecord l WHERE l.uid = :uid ORDER BY l.loginTime DESC")
    Page<UserLoginRecord> findByUidOrderByLoginTimeDesc(@Param("uid") String uid, Pageable pageable);

    @Query("SELECT l FROM UserLoginRecord l WHERE l.uid IN :uids ORDER BY l.loginTime DESC")
    Page<UserLoginRecord> findByUidsOrderByLoginTimeDesc(@Param("uids") List<String> uids, Pageable pageable);

    @Query("SELECT l FROM UserLoginRecord l WHERE l.uid = :uid AND l.loginTime >= :startTime ORDER BY l.loginTime DESC")
    List<UserLoginRecord> findByUidAndLoginTimeAfterOrderByLoginTimeDesc(@Param("uid") String uid, @Param("startTime") LocalDateTime startTime);

    @Query("SELECT l FROM UserLoginRecord l WHERE l.traceId = :traceId")
    Optional<UserLoginRecord> findByTraceId(@Param("traceId") String traceId);

    @Query("SELECT l FROM UserLoginRecord l WHERE l.traceId IN :traceIds")
    List<UserLoginRecord> findByTraceIds(@Param("traceIds") List<String> traceIds);

    @Query("SELECT COUNT(l) FROM UserLoginRecord l WHERE l.uid = :uid AND l.loginTime >= :startTime")
    Long countByUidAndLoginTimeAfter(@Param("uid") String uid, @Param("startTime") LocalDateTime startTime);

    @Query("SELECT COUNT(DISTINCT l.ipAddress) FROM UserLoginRecord l WHERE l.uid = :uid AND l.loginTime >= :startTime")
    Long countUniqueIpAddressesByUidAndLoginTimeAfter(@Param("uid") String uid, @Param("startTime") LocalDateTime startTime);

    @Query("SELECT COUNT(DISTINCT l.fingerprint) FROM UserLoginRecord l WHERE l.uid = :uid AND l.loginTime >= :startTime")
    Long countUniqueDevicesByUidAndLoginTimeAfter(@Param("uid") String uid, @Param("startTime") LocalDateTime startTime);

    @Query("SELECT COUNT(l) FROM UserLoginRecord l WHERE l.uid = :uid AND l.isSuspicious = true AND l.loginTime >= :startTime")
    Long countSuspiciousActivitiesByUidAndLoginTimeAfter(@Param("uid") String uid, @Param("startTime") LocalDateTime startTime);

    @Query("SELECT AVG(l.riskScore) FROM UserLoginRecord l WHERE l.uid = :uid AND l.loginTime >= :startTime")
    Double getAverageRiskScoreByUidAndLoginTimeAfter(@Param("uid") String uid, @Param("startTime") LocalDateTime startTime);

    @Query("SELECT MAX(l.riskScore) FROM UserLoginRecord l WHERE l.uid = :uid AND l.loginTime >= :startTime")
    Integer getMaxRiskScoreByUidAndLoginTimeAfter(@Param("uid") String uid, @Param("startTime") LocalDateTime startTime);

    @Query("SELECT l.loginMethod, COUNT(l) FROM UserLoginRecord l WHERE l.uid = :uid AND l.loginTime >= :startTime GROUP BY l.loginMethod")
    List<Object[]> getLoginMethodsByUidAndLoginTimeAfter(@Param("uid") String uid, @Param("startTime") LocalDateTime startTime);

    @Query("SELECT l.ipAddress, COUNT(l) FROM UserLoginRecord l WHERE l.uid = :uid AND l.loginTime >= :startTime GROUP BY l.ipAddress ORDER BY COUNT(l) DESC")
    List<Object[]> getMostCommonIpByUidAndLoginTimeAfter(@Param("uid") String uid, @Param("startTime") LocalDateTime startTime);

    @Query("SELECT l.deviceType, COUNT(l) FROM UserLoginRecord l WHERE l.uid = :uid AND l.loginTime >= :startTime GROUP BY l.deviceType ORDER BY COUNT(l) DESC")
    List<Object[]> getMostCommonDeviceByUidAndLoginTimeAfter(@Param("uid") String uid, @Param("startTime") LocalDateTime startTime);

    @Query("SELECT l FROM UserLoginRecord l WHERE l.uid = :uid ORDER BY l.loginTime DESC LIMIT 1")
    Optional<UserLoginRecord> findLatestLoginByUid(@Param("uid") String uid);

    @Query("SELECT l FROM UserLoginRecord l WHERE l.uid = :uid ORDER BY l.loginTime ASC LIMIT 1")
    Optional<UserLoginRecord> findFirstLoginByUid(@Param("uid") String uid);
} 
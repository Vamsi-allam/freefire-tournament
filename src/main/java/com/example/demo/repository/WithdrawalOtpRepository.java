package com.example.demo.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.demo.entity.WithdrawalOtp;

@Repository
public interface WithdrawalOtpRepository extends JpaRepository<WithdrawalOtp, Long> {

    /**
     * Find active (non-verified and non-expired) OTP by user ID
     */
    @Query("SELECT w FROM WithdrawalOtp w WHERE w.user.id = :userId "
            + "AND w.isVerified = false AND w.expiresAt > :currentTime "
            + "ORDER BY w.createdAt DESC")
    Optional<WithdrawalOtp> findActiveOtpByUserId(@Param("userId") Long userId,
            @Param("currentTime") LocalDateTime currentTime);

    /**
     * Find OTP by user ID and OTP code that is not verified and not expired
     */
    @Query("SELECT w FROM WithdrawalOtp w WHERE w.user.id = :userId "
            + "AND w.otpCode = :otpCode AND w.isVerified = false "
            + "AND w.expiresAt > :currentTime")
    Optional<WithdrawalOtp> findValidOtpByUserIdAndCode(@Param("userId") Long userId,
            @Param("otpCode") String otpCode,
            @Param("currentTime") LocalDateTime currentTime);

    /**
     * Find all expired OTPs that are not yet cleaned up
     */
    @Query("SELECT w FROM WithdrawalOtp w WHERE w.expiresAt < :currentTime")
    List<WithdrawalOtp> findExpiredOtps(@Param("currentTime") LocalDateTime currentTime);

    /**
     * Delete expired OTPs
     */
    @Modifying
    @Query("DELETE FROM WithdrawalOtp w WHERE w.expiresAt < :currentTime")
    void deleteExpiredOtps(@Param("currentTime") LocalDateTime currentTime);

    /**
     * Delete verified OTPs older than a certain time
     */
    @Modifying
    @Query("DELETE FROM WithdrawalOtp w WHERE w.isVerified = true "
            + "AND w.verifiedAt < :cutoffTime")
    void deleteOldVerifiedOtps(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Count active OTPs for a user (to prevent spam)
     */
    @Query("SELECT COUNT(w) FROM WithdrawalOtp w WHERE w.user.id = :userId "
            + "AND w.isVerified = false AND w.expiresAt > :currentTime")
    long countActiveOtpsByUserId(@Param("userId") Long userId,
            @Param("currentTime") LocalDateTime currentTime);
}

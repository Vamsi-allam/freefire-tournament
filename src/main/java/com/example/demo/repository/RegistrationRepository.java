package com.example.demo.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.demo.entity.Registration;
import com.example.demo.entity.RegistrationStatus;

@Repository
public interface RegistrationRepository extends JpaRepository<Registration, Long> {

    List<Registration> findByUserIdAndStatus(Long userId, RegistrationStatus status);

    List<Registration> findByUserId(Long userId);

    @Query("SELECT r FROM Registration r JOIN FETCH r.match WHERE r.user.id = :userId")
    List<Registration> findByUserIdWithMatch(@Param("userId") Long userId);

    List<Registration> findByMatchId(Long matchId);

    List<Registration> findByMatchIdAndStatus(Long matchId, RegistrationStatus status);

    @Query("SELECT r FROM Registration r JOIN FETCH r.match WHERE r.match.id = :matchId AND r.status = :status")
    List<Registration> findByMatchIdAndStatusWithMatch(@Param("matchId") Long matchId, @Param("status") RegistrationStatus status);

    @Query("SELECT r FROM Registration r JOIN FETCH r.match JOIN FETCH r.user WHERE r.match.id = :matchId AND r.status = :status")
    List<Registration> findByMatchIdAndStatusWithMatchAndUser(@Param("matchId") Long matchId, @Param("status") RegistrationStatus status);

    Optional<Registration> findByUserIdAndMatchId(Long userId, Long matchId);

    @Query("SELECT COUNT(r) FROM Registration r WHERE r.match.id = :matchId AND r.status = 'CONFIRMED'")
    int countConfirmedRegistrationsByMatchId(@Param("matchId") Long matchId);

    @Query("SELECT r.slotNumber FROM Registration r WHERE r.match.id = :matchId AND r.status = 'CONFIRMED'")
    List<Integer> findAllocatedSlotsByMatchId(@Param("matchId") Long matchId);

    boolean existsByUserIdAndMatchIdAndStatus(Long userId, Long matchId, RegistrationStatus status);

    // Batch helpers to avoid N+1 in controllers/services
    @Query("SELECT r.match.id FROM Registration r WHERE r.user.id = :userId AND r.status = 'CONFIRMED'")
    List<Long> findRegisteredMatchIdsForUser(@Param("userId") Long userId);

    @Query("SELECT r.match.id AS matchId, COUNT(r) AS cnt FROM Registration r WHERE r.status = 'CONFIRMED' AND r.match.id IN :matchIds GROUP BY r.match.id")
    List<Object[]> countConfirmedByMatchIds(@Param("matchIds") List<Long> matchIds);
}

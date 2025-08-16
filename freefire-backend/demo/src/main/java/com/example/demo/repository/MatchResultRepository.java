package com.example.demo.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.demo.entity.MatchResult;

@Repository
public interface MatchResultRepository extends JpaRepository<MatchResult, Long> {

    List<MatchResult> findByMatchId(Long matchId);

    List<MatchResult> findByMatchIdOrderByPositionAsc(Long matchId);

    List<MatchResult> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<MatchResult> findByRegistrationId(Long registrationId);

    @Query("SELECT mr FROM MatchResult mr WHERE mr.match.id = :matchId AND mr.prizeCredited = false")
    List<MatchResult> findUncreditedResultsByMatchId(@Param("matchId") Long matchId);

    @Query("SELECT mr FROM MatchResult mr WHERE mr.match.id = :matchId AND mr.position <= 3")
    List<MatchResult> findTopThreeByMatchId(@Param("matchId") Long matchId);

    boolean existsByMatchIdAndPosition(Long matchId, Integer position);
}

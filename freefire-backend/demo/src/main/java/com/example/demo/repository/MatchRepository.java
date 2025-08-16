package com.example.demo.repository;

import com.example.demo.entity.Match;
import com.example.demo.entity.MatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface MatchRepository extends JpaRepository<Match, Long> {

    List<Match> findByStatusOrderByScheduledAtAsc(MatchStatus status);

    List<Match> findByScheduledAtAfterOrderByScheduledAtAsc(LocalDateTime time);
}

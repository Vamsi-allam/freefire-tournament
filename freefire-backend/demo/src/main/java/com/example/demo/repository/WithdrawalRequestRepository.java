package com.example.demo.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.demo.entity.WithdrawalRequest;
import com.example.demo.entity.WithdrawalStatus;

@Repository
public interface WithdrawalRequestRepository extends JpaRepository<WithdrawalRequest, Long> {

    List<WithdrawalRequest> findByStatusOrderByCreatedAtAsc(WithdrawalStatus status);

    // List all withdrawal requests for a given user ordered by newest first
    List<WithdrawalRequest> findByUserIdOrderByCreatedAtDesc(Long userId);
}

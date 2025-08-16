package com.example.demo.repository;

import com.example.demo.entity.UpiPayment;
import com.example.demo.entity.UpiPaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UpiPaymentRepository extends JpaRepository<UpiPayment, Long> {

    List<UpiPayment> findByStatus(UpiPaymentStatus status);

    List<UpiPayment> findByUserIdAndStatusNotOrderByCreatedAtDesc(Long userId, UpiPaymentStatus status);
}

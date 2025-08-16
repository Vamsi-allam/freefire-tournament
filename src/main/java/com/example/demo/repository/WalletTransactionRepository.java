package com.example.demo.repository;

import com.example.demo.entity.WalletTransaction;
import com.example.demo.entity.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {

    List<WalletTransaction> findByWalletIdOrderByCreatedAtDesc(Long walletId);

    List<WalletTransaction> findByWalletIdAndTypeOrderByCreatedAtDesc(Long walletId, TransactionType type);

    @Query("SELECT SUM(wt.amount) FROM WalletTransaction wt WHERE wt.wallet.id = :walletId AND wt.type = :type")
    BigDecimal sumAmountByWalletIdAndType(@Param("walletId") Long walletId, @Param("type") TransactionType type);

    @Query("SELECT COUNT(wt) FROM WalletTransaction wt WHERE wt.wallet.id = :walletId")
    Long countByWalletId(@Param("walletId") Long walletId);
}

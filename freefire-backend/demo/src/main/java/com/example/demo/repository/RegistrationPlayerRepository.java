package com.example.demo.repository;

import com.example.demo.entity.RegistrationPlayer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RegistrationPlayerRepository extends JpaRepository<RegistrationPlayer, Long> {

    List<RegistrationPlayer> findByRegistrationIdOrderByPlayerPosition(Long registrationId);

    void deleteByRegistrationId(Long registrationId);
}

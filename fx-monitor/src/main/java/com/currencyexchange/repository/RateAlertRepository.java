package com.currencyexchange.repository;

import com.currencyexchange.entity.RateAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RateAlertRepository extends JpaRepository<RateAlert, Long> {

    List<RateAlert> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<RateAlert> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status);

    List<RateAlert> findByStatus(String status);
}

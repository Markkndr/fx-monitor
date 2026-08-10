package com.currencyexchange.repository;

import com.currencyexchange.entity.Exposure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExposureRepository extends JpaRepository<Exposure, Long> {

    List<Exposure> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Exposure> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status);

    List<Exposure> findByUserIdAndTypeOrderByCreatedAtDesc(Long userId, String type);

    List<Exposure> findByUserIdAndCurrencyOrderByCreatedAtDesc(Long userId, String currency);
}

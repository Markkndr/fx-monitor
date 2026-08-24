package com.currencyexchange.repository;

import com.currencyexchange.entity.Hedge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HedgeRepository extends JpaRepository<Hedge, Long> {

    List<Hedge> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Hedge> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status);

    List<Hedge> findByExposureId(Long exposureId);
}

package com.currencyexchange.repository;

import com.currencyexchange.entity.RateSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface RateSnapshotRepository extends JpaRepository<RateSnapshot, Long> {

    List<RateSnapshot> findByBaseAndQuoteOrderByCapturedAtAsc(String base, String quote);

    List<RateSnapshot> findByBaseAndQuoteAndCapturedAtAfterOrderByCapturedAtAsc(
            String base, String quote, LocalDateTime after);

    List<RateSnapshot> findByBaseAndQuoteInAndCapturedAtAfterOrderByCapturedAtAsc(
            String base, Collection<String> quotes, LocalDateTime after);
}

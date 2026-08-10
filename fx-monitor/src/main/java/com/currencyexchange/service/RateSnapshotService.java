package com.currencyexchange.service;

import com.currencyexchange.dto.exchange.ExchangeRateDTO;
import com.currencyexchange.dto.exchange.RateHistoryDTO;
import com.currencyexchange.entity.RateSnapshot;
import com.currencyexchange.repository.RateSnapshotRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists periodic snapshots of live FX rates so the platform builds a rate
 * history over time. {@link ExchangeRateService} only ever exposes the latest
 * quote; this service is what turns those quotes into a queryable time series.
 */
@Service
@Slf4j
public class RateSnapshotService {

    @Autowired
    private RateSnapshotRepository rateSnapshotRepository;

    @Autowired
    private ExchangeRateService exchangeRateService;

    @Value("${forex.snapshot.base:USD}")
    private String base;

    @Value("${forex.snapshot.currencies:EUR,GBP,JPY,CNY,CHF,CAD,AUD}")
    private List<String> quoteCurrencies;

    /**
     * Captures a snapshot of the configured currency pairs. Runs on startup and
     * then on the configured interval. Failures (e.g. a provider outage) are
     * logged and swallowed so the scheduler keeps running.
     */
    @Scheduled(fixedRateString = "#{${forex.snapshot.interval:3600} * 1000}")
    @Transactional
    public void captureSnapshots() {
        try {
            String baseCurrency = base.toUpperCase();
            ExchangeRateDTO rates = exchangeRateService.getRates(baseCurrency);
            LocalDateTime capturedAt = LocalDateTime.now();

            List<RateSnapshot> batch = new ArrayList<>();
            for (String quote : quoteCurrencies) {
                String quoteCurrency = quote.trim().toUpperCase();
                BigDecimal rate = rates.getRates().get(quoteCurrency);
                if (rate != null) {
                    RateSnapshot snapshot = new RateSnapshot();
                    snapshot.setBase(baseCurrency);
                    snapshot.setQuote(quoteCurrency);
                    snapshot.setRate(rate);
                    snapshot.setCapturedAt(capturedAt);
                    batch.add(snapshot);
                }
            }

            rateSnapshotRepository.saveAll(batch);
            log.info("Captured {} rate snapshots for base {}", batch.size(), baseCurrency);
        } catch (Exception e) {
            log.warn("Rate snapshot capture skipped: {}", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public RateHistoryDTO getHistory(String base, String quote) {
        String baseCurrency = base.toUpperCase();
        String quoteCurrency = quote.toUpperCase();

        List<RateHistoryDTO.Point> points =
                rateSnapshotRepository.findByBaseAndQuoteOrderByCapturedAtAsc(baseCurrency, quoteCurrency)
                        .stream()
                        .map(s -> RateHistoryDTO.Point.builder()
                                .capturedAt(s.getCapturedAt())
                                .rate(s.getRate())
                                .build())
                        .toList();

        return RateHistoryDTO.builder()
                .base(baseCurrency)
                .quote(quoteCurrency)
                .points(points)
                .build();
    }
}

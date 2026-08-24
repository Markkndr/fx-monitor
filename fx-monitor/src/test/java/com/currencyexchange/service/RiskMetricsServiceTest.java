package com.currencyexchange.service;

import com.currencyexchange.dto.analytics.AttributionResultDTO;
import com.currencyexchange.dto.analytics.VarResultDTO;
import com.currencyexchange.entity.RateSnapshot;
import com.currencyexchange.repository.RateSnapshotRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RiskMetricsService")
class RiskMetricsServiceTest {

    private static final Long USER_ID = 7L;

    @Mock
    private PortfolioStatisticsService portfolioStatisticsService;
    @Mock
    private RateSnapshotRepository rateSnapshotRepository;
    @InjectMocks
    private RiskMetricsService riskMetricsService;

    private RateSnapshot snapshot(String quote, String rate, LocalDateTime at) {
        RateSnapshot s = new RateSnapshot();
        s.setBase("USD");
        s.setQuote(quote);
        s.setRate(new BigDecimal(rate));
        s.setCapturedAt(at);
        return s;
    }

    @Test
    @DisplayName("pnlAttribution values the exposure at start and end rates and totals the P&L")
    void attributesPnl() {
        when(portfolioStatisticsService.netExposureByCurrency(USER_ID))
                .thenReturn(Map.of("EUR", new BigDecimal("10000")));
        LocalDateTime t1 = LocalDateTime.now().minusDays(10);
        LocalDateTime t2 = LocalDateTime.now().minusDays(1);
        when(rateSnapshotRepository.findByBaseAndQuoteAndCapturedAtAfterOrderByCapturedAtAsc(
                eq("USD"), eq("EUR"), any()))
                .thenReturn(List.of(snapshot("EUR", "0.90", t1), snapshot("EUR", "1.00", t2)));

        AttributionResultDTO result = riskMetricsService.pnlAttribution(USER_ID, "USD", 30);

        // 10,000/0.90 = 11,111.11 → 10,000/1.00 = 10,000.00, a loss of ~1,111.11.
        assertThat(result.getBreakdown()).hasSize(1);
        assertThat(result.getTotalPnl()).isEqualByComparingTo("-1111.11");
        assertThat(result.getBreakdown().get(0).getCurrency()).isEqualTo("EUR");
    }

    @Test
    @DisplayName("valueAtRisk reports insufficient data when there is too little history")
    void varInsufficientData() {
        when(portfolioStatisticsService.netExposureByCurrency(USER_ID))
                .thenReturn(Map.of("EUR", new BigDecimal("10000")));
        when(rateSnapshotRepository.findByBaseAndQuoteInAndCapturedAtAfterOrderByCapturedAtAsc(
                eq("USD"), anyCollection(), any()))
                .thenReturn(List.of());

        VarResultDTO result = riskMetricsService.valueAtRisk(USER_ID, "USD", new BigDecimal("0.95"), 365);

        assertThat(result.getObservations()).isZero();
        assertThat(result.getMessage()).isNotBlank();
        assertThat(result.getValueAtRisk()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("valueAtRisk computes a positive VaR from a spread of historical revaluations")
    void varComputed() {
        when(portfolioStatisticsService.netExposureByCurrency(USER_ID))
                .thenReturn(Map.of("EUR", new BigDecimal("10000")));
        LocalDateTime base = LocalDateTime.now().minusDays(20);
        // A run of rates that swings the EUR book up and down over time.
        List<RateSnapshot> series = List.of(
                snapshot("EUR", "0.90", base.plusDays(1)),
                snapshot("EUR", "0.92", base.plusDays(2)),
                snapshot("EUR", "0.88", base.plusDays(3)),
                snapshot("EUR", "0.95", base.plusDays(4)),
                snapshot("EUR", "0.85", base.plusDays(5)),
                snapshot("EUR", "0.91", base.plusDays(6)));
        when(rateSnapshotRepository.findByBaseAndQuoteInAndCapturedAtAfterOrderByCapturedAtAsc(
                eq("USD"), anyCollection(), any()))
                .thenReturn(series);

        VarResultDTO result = riskMetricsService.valueAtRisk(USER_ID, "USD", new BigDecimal("0.95"), 365);

        assertThat(result.getObservations()).isEqualTo(5);
        assertThat(result.getValueAtRisk()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(result.getWorstLoss()).isGreaterThan(BigDecimal.ZERO);
    }
}

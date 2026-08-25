package com.currencyexchange.service;

import com.currencyexchange.dto.analytics.AttributionResultDTO;
import com.currencyexchange.dto.analytics.CurrencyAttributionDTO;
import com.currencyexchange.dto.analytics.ScenarioResultDTO;
import com.currencyexchange.dto.analytics.StressTestResultDTO;
import com.currencyexchange.dto.analytics.VarResultDTO;
import com.currencyexchange.dto.hedges.HedgeDTO;
import com.currencyexchange.dto.statistics.CurrencyExposureDTO;
import com.currencyexchange.dto.statistics.PortfolioStatisticsDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PdfReportService")
class PdfReportServiceTest {

    private static final Long USER_ID = 7L;

    @Mock
    private PortfolioStatisticsService portfolioStatisticsService;
    @Mock
    private HedgeService hedgeService;
    @Mock
    private RiskMetricsService riskMetricsService;
    @Mock
    private ScenarioAnalysisService scenarioAnalysisService;
    @InjectMocks
    private PdfReportService pdfReportService;

    private void stubAll() {
        when(portfolioStatisticsService.getPortfolioStatistics(eq(USER_ID), anyString()))
                .thenReturn(PortfolioStatisticsDTO.builder()
                        .homeCurrency("USD")
                        .totalValueInHome(new BigDecimal("12345.67"))
                        .currencyCount(2)
                        .exposures(List.of(CurrencyExposureDTO.builder()
                                .currency("EUR")
                                .netExposure(new BigDecimal("1000"))
                                .valueInHome(new BigDecimal("1080"))
                                .percentOfPortfolio(new BigDecimal("8.75"))
                                .build()))
                        .unvaluedCurrencies(List.of())
                        .build());

        when(hedgeService.getUserHedges(USER_ID)).thenReturn(List.of(HedgeDTO.builder()
                .id(5L)
                .instrumentType("FORWARD")
                .direction("SELL")
                .baseCurrency("EUR")
                .quoteCurrency("USD")
                .notional(new BigDecimal("1000"))
                .unrealizedPnl(new BigDecimal("25.00"))
                .effective(Boolean.TRUE)
                .build()));

        when(riskMetricsService.valueAtRisk(eq(USER_ID), anyString(), any(), anyInt()))
                .thenReturn(VarResultDTO.builder()
                        .home("USD")
                        .confidence(new BigDecimal("0.95"))
                        .observations(120)
                        .valueAtRisk(new BigDecimal("340.12"))
                        .expectedShortfall(new BigDecimal("410.00"))
                        .worstLoss(new BigDecimal("512.00"))
                        .build());

        when(riskMetricsService.pnlAttribution(eq(USER_ID), anyString(), anyInt()))
                .thenReturn(AttributionResultDTO.builder()
                        .home("USD")
                        .totalPnl(new BigDecimal("-42.50"))
                        .breakdown(List.of(CurrencyAttributionDTO.builder()
                                .currency("EUR")
                                .netExposure(new BigDecimal("1000"))
                                .rateChangePercent(new BigDecimal("-0.42"))
                                .pnl(new BigDecimal("-42.50"))
                                .build()))
                        .build());

        when(scenarioAnalysisService.runStressTests(eq(USER_ID), anyString()))
                .thenReturn(StressTestResultDTO.builder()
                        .home("USD")
                        .baselineValue(new BigDecimal("12345.67"))
                        .scenarios(List.of(ScenarioResultDTO.builder()
                                .name("EUR -5%")
                                .shockedValue(new BigDecimal("12291.67"))
                                .pnl(new BigDecimal("-54.00"))
                                .build()))
                        .build());
    }

    @Test
    @DisplayName("produces a non-empty PDF document")
    void producesPdf() {
        stubAll();

        byte[] pdf = pdfReportService.generateExecutiveSummary(USER_ID, "USD");

        assertThat(pdf).isNotEmpty();
        // A well-formed PDF starts with the "%PDF-" magic header and ends with EOF.
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        assertThat(new String(pdf)).contains("%%EOF");
    }
}

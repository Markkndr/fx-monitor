package com.currencyexchange.service;

import com.currencyexchange.dto.analytics.RateShockDTO;
import com.currencyexchange.dto.analytics.ScenarioResultDTO;
import com.currencyexchange.dto.analytics.StressTestResultDTO;
import com.currencyexchange.dto.exchange.ExchangeRateDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScenarioAnalysisService")
class ScenarioAnalysisServiceTest {

    private static final Long USER_ID = 7L;

    @Mock
    private PortfolioStatisticsService portfolioStatisticsService;
    @Mock
    private ExchangeRateService exchangeRateService;
    @InjectMocks
    private ScenarioAnalysisService scenarioAnalysisService;

    private void stubExposureAndRates() {
        when(portfolioStatisticsService.netExposureByCurrency(USER_ID))
                .thenReturn(Map.of("EUR", new BigDecimal("10000")));
        when(exchangeRateService.getRates(anyString())).thenReturn(ExchangeRateDTO.builder()
                .base("USD")
                .rates(Map.of("EUR", new BigDecimal("0.90")))
                .build());
    }

    @Test
    @DisplayName("runScenario values the book and applies the shock as a P&L loss")
    void runScenarioAppliesShock() {
        stubExposureAndRates();

        ScenarioResultDTO result = scenarioAnalysisService.runScenario(
                USER_ID, "USD", List.of(new RateShockDTO("EUR", new BigDecimal("-10"))));

        // 10,000 EUR / 0.90 = 11,111.11 USD baseline; a −10% shock loses ~1,111.11.
        assertThat(result.getBaselineValue()).isEqualByComparingTo("11111.11");
        assertThat(result.getShockedValue()).isEqualByComparingTo("10000.00");
        assertThat(result.getPnl()).isEqualByComparingTo("-1111.11");
        assertThat(result.getImpacts()).hasSize(1);
        assertThat(result.getImpacts().get(0).getAppliedShockPercent()).isEqualByComparingTo("-10");
    }

    @Test
    @DisplayName("runStressTests returns the full battery of named scenarios")
    void runStressTestsReturnsBattery() {
        stubExposureAndRates();

        StressTestResultDTO result = scenarioAnalysisService.runStressTests(USER_ID, "USD");

        assertThat(result.getScenarios()).isNotEmpty();
        assertThat(result.getScenarios()).allSatisfy(s -> assertThat(s.getName()).isNotBlank());
        // The "all foreign FX −10%" scenario must produce a loss on a long EUR book.
        assertThat(result.getScenarios())
                .anySatisfy(s -> assertThat(s.getPnl()).isNegative());
    }
}

package com.currencyexchange.service;

import com.currencyexchange.dto.analytics.CurrencyImpactDTO;
import com.currencyexchange.dto.analytics.RateShockDTO;
import com.currencyexchange.dto.analytics.ScenarioResultDTO;
import com.currencyexchange.dto.analytics.StressTestResultDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Scenario and stress-test engine.
 *
 * <p>Takes the user's net exposure per currency, values it in a home currency at
 * the latest spot, then re-values it under one or more <em>rate shocks</em> to show
 * the P&amp;L impact of hypothetical currency moves. {@link #runScenario} answers an
 * ad-hoc "what if EUR drops 5%?"; {@link #runStressTests} runs a standard battery of
 * adverse scenarios. A shock is a percentage change in a currency's value against the
 * home currency, so the P&amp;L on a position is simply {@code homeValue * shock%}.
 */
@Service
@Slf4j
public class ScenarioAnalysisService {

    private static final String DEFAULT_HOME = "USD";
    private static final int MONEY_SCALE = 2;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    @Autowired
    private PortfolioStatisticsService portfolioStatisticsService;

    @Autowired
    private ExchangeRateService exchangeRateService;

    @Transactional(readOnly = true)
    public ScenarioResultDTO runScenario(Long userId, String home, List<RateShockDTO> shocks) {
        String homeCurrency = normaliseHome(home);
        Map<String, BigDecimal> shockByCurrency = new LinkedHashMap<>();
        for (RateShockDTO shock : shocks) {
            shockByCurrency.put(shock.getCurrency().trim().toUpperCase(), shock.getChangePercent());
        }

        Map<String, BigDecimal> net = portfolioStatisticsService.netExposureByCurrency(userId);
        Map<String, BigDecimal> rates = exchangeRateService.getRates(homeCurrency).getRates();
        return computeScenario(null, homeCurrency, net, rates, shockByCurrency);
    }

    @Transactional(readOnly = true)
    public StressTestResultDTO runStressTests(Long userId, String home) {
        String homeCurrency = normaliseHome(home);
        Map<String, BigDecimal> net = portfolioStatisticsService.netExposureByCurrency(userId);
        Map<String, BigDecimal> rates = exchangeRateService.getRates(homeCurrency).getRates();

        List<ScenarioResultDTO> results = new ArrayList<>();
        BigDecimal baseline = null;
        for (StressScenario scenario : standardScenarios(homeCurrency, net.keySet())) {
            ScenarioResultDTO result = computeScenario(
                    scenario.name(), homeCurrency, net, rates, scenario.shocks());
            results.add(result);
            baseline = result.getBaselineValue();
        }

        return StressTestResultDTO.builder()
                .home(homeCurrency)
                .baselineValue(baseline)
                .scenarios(results)
                .build();
    }

    /**
     * Values every net position in home currency, applies each currency's shock, and
     * rolls the per-currency P&amp;L up into a portfolio total. Currencies whose spot
     * is unavailable are skipped (they can't be valued).
     */
    private ScenarioResultDTO computeScenario(String name, String home, Map<String, BigDecimal> net,
                                              Map<String, BigDecimal> rates,
                                              Map<String, BigDecimal> shockByCurrency) {
        List<CurrencyImpactDTO> impacts = new ArrayList<>();
        BigDecimal baselineTotal = BigDecimal.ZERO;
        BigDecimal shockedTotal = BigDecimal.ZERO;

        for (Map.Entry<String, BigDecimal> entry : net.entrySet()) {
            String currency = entry.getKey();
            BigDecimal amount = entry.getValue();
            BigDecimal rate = rateToHome(home, currency, rates);
            if (rate == null || rate.signum() <= 0) {
                continue;
            }
            BigDecimal baseline = amount.divide(rate, MONEY_SCALE, RoundingMode.HALF_UP);
            // A shock on the home currency itself is meaningless — its home value is fixed.
            BigDecimal shockPct = home.equals(currency)
                    ? BigDecimal.ZERO
                    : shockByCurrency.getOrDefault(currency, BigDecimal.ZERO);
            BigDecimal factor = BigDecimal.ONE.add(shockPct.divide(HUNDRED, 10, RoundingMode.HALF_UP));
            BigDecimal shocked = baseline.multiply(factor).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal pnl = shocked.subtract(baseline);

            baselineTotal = baselineTotal.add(baseline);
            shockedTotal = shockedTotal.add(shocked);

            impacts.add(CurrencyImpactDTO.builder()
                    .currency(currency)
                    .netExposure(amount)
                    .appliedShockPercent(shockPct)
                    .baselineValue(baseline)
                    .shockedValue(shocked)
                    .pnl(pnl)
                    .build());
        }

        baselineTotal = baselineTotal.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        shockedTotal = shockedTotal.setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        return ScenarioResultDTO.builder()
                .name(name)
                .home(home)
                .baselineValue(baselineTotal)
                .shockedValue(shockedTotal)
                .pnl(shockedTotal.subtract(baselineTotal))
                .impacts(impacts)
                .build();
    }

    /**
     * The standard adverse scenarios. Shocks apply only to the currencies actually
     * held (the home currency is never shocked against itself).
     */
    private List<StressScenario> standardScenarios(String home, java.util.Set<String> currencies) {
        List<StressScenario> scenarios = new ArrayList<>();

        scenarios.add(new StressScenario("Home currency +10% (all foreign FX −10%)",
                uniformShocks(home, currencies, new BigDecimal("-10"))));
        scenarios.add(new StressScenario("Home currency −10% (all foreign FX +10%)",
                uniformShocks(home, currencies, new BigDecimal("10"))));
        scenarios.add(new StressScenario("EUR −5%", singleShock("EUR", new BigDecimal("-5"))));
        scenarios.add(new StressScenario("EUR −10%", singleShock("EUR", new BigDecimal("-10"))));
        scenarios.add(new StressScenario("GBP −10%", singleShock("GBP", new BigDecimal("-10"))));
        scenarios.add(new StressScenario("JPY −15%", singleShock("JPY", new BigDecimal("-15"))));

        // Risk-off: developed majors sell off moderately, everything else harder.
        java.util.Set<String> majors = java.util.Set.of("EUR", "GBP", "CHF", "CAD", "AUD", "JPY", "NZD");
        Map<String, BigDecimal> riskOff = new LinkedHashMap<>();
        for (String currency : currencies) {
            if (currency.equals(home)) {
                continue;
            }
            riskOff.put(currency, majors.contains(currency) ? new BigDecimal("-8") : new BigDecimal("-15"));
        }
        scenarios.add(new StressScenario("Risk-off (majors −8%, others −15%)", riskOff));

        return scenarios;
    }

    private Map<String, BigDecimal> uniformShocks(String home, java.util.Set<String> currencies, BigDecimal pct) {
        Map<String, BigDecimal> shocks = new LinkedHashMap<>();
        for (String currency : currencies) {
            if (!currency.equals(home)) {
                shocks.put(currency, pct);
            }
        }
        return shocks;
    }

    private Map<String, BigDecimal> singleShock(String currency, BigDecimal pct) {
        Map<String, BigDecimal> shocks = new LinkedHashMap<>();
        shocks.put(currency, pct);
        return shocks;
    }

    private BigDecimal rateToHome(String home, String currency, Map<String, BigDecimal> rates) {
        if (home.equals(currency)) {
            return BigDecimal.ONE;
        }
        return rates != null ? rates.get(currency) : null;
    }

    private String normaliseHome(String home) {
        return (home == null || home.isBlank()) ? DEFAULT_HOME : home.trim().toUpperCase();
    }

    /** A named set of per-currency shocks. */
    private record StressScenario(String name, Map<String, BigDecimal> shocks) {
    }
}

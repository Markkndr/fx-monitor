package com.currencyexchange.service;

import com.currencyexchange.dto.analytics.AttributionResultDTO;
import com.currencyexchange.dto.analytics.CurrencyAttributionDTO;
import com.currencyexchange.dto.analytics.VarResultDTO;
import com.currencyexchange.entity.RateSnapshot;
import com.currencyexchange.repository.RateSnapshotRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Portfolio risk analytics computed from stored {@code RateSnapshot} history:
 *
 * <ul>
 *   <li><b>P&amp;L attribution</b> — over a lookback window, how much home-currency
 *       P&amp;L each currency's rate move produced on the current net exposure.</li>
 *   <li><b>Value at Risk</b> — a historical-simulation VaR (and expected shortfall)
 *       from the distribution of period-over-period portfolio value changes.</li>
 * </ul>
 *
 * <p>Both revalue the current net exposure at historical rates, so they only cover
 * currency pairs the snapshot job has been capturing (base = the snapshot base,
 * usually USD). Rates are quoted as units of the foreign currency per 1 unit of home.
 */
@Service
@Slf4j
public class RiskMetricsService {

    private static final String DEFAULT_HOME = "USD";
    private static final int MONEY_SCALE = 2;
    private static final int PERCENT_SCALE = 2;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal DEFAULT_CONFIDENCE = new BigDecimal("0.95");

    @Autowired
    private PortfolioStatisticsService portfolioStatisticsService;

    @Autowired
    private RateSnapshotRepository rateSnapshotRepository;

    // --- P&L attribution ------------------------------------------------------

    @Transactional(readOnly = true)
    public AttributionResultDTO pnlAttribution(Long userId, String home, int lookbackDays) {
        String homeCurrency = normaliseHome(home);
        LocalDateTime since = LocalDateTime.now().minusDays(Math.max(1, lookbackDays));

        Map<String, BigDecimal> net = portfolioStatisticsService.netExposureByCurrency(userId);

        List<CurrencyAttributionDTO> breakdown = new ArrayList<>();
        List<String> unattributed = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        LocalDateTime from = null;
        LocalDateTime to = null;

        for (Map.Entry<String, BigDecimal> entry : net.entrySet()) {
            String currency = entry.getKey();
            BigDecimal amount = entry.getValue();
            if (currency.equals(homeCurrency)) {
                continue; // the home leg carries no FX P&L
            }

            List<RateSnapshot> points = rateSnapshotRepository
                    .findByBaseAndQuoteAndCapturedAtAfterOrderByCapturedAtAsc(homeCurrency, currency, since);
            if (points.size() < 2) {
                unattributed.add(currency);
                continue;
            }

            RateSnapshot start = points.get(0);
            RateSnapshot end = points.get(points.size() - 1);
            BigDecimal startRate = start.getRate();
            BigDecimal endRate = end.getRate();
            if (startRate.signum() <= 0 || endRate.signum() <= 0) {
                unattributed.add(currency);
                continue;
            }

            // Value in home = amount / rate. P&L is the change in that value.
            BigDecimal valueStart = amount.divide(startRate, MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal valueEnd = amount.divide(endRate, MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal pnl = valueEnd.subtract(valueStart);
            BigDecimal rateChangePercent = endRate.subtract(startRate)
                    .multiply(HUNDRED)
                    .divide(startRate, PERCENT_SCALE, RoundingMode.HALF_UP);

            total = total.add(pnl);
            from = earliest(from, start.getCapturedAt());
            to = latest(to, end.getCapturedAt());

            breakdown.add(CurrencyAttributionDTO.builder()
                    .currency(currency)
                    .netExposure(amount)
                    .startRate(startRate)
                    .endRate(endRate)
                    .rateChangePercent(rateChangePercent)
                    .pnl(pnl)
                    .build());
        }

        breakdown.sort(Comparator.comparing(CurrencyAttributionDTO::getPnl));

        return AttributionResultDTO.builder()
                .home(homeCurrency)
                .from(from)
                .to(to)
                .totalPnl(total.setScale(MONEY_SCALE, RoundingMode.HALF_UP))
                .breakdown(breakdown)
                .unattributedCurrencies(unattributed)
                .build();
    }

    // --- Value at Risk --------------------------------------------------------

    @Transactional(readOnly = true)
    public VarResultDTO valueAtRisk(Long userId, String home, BigDecimal confidence, int lookbackDays) {
        String homeCurrency = normaliseHome(home);
        BigDecimal conf = normaliseConfidence(confidence);
        LocalDateTime since = LocalDateTime.now().minusDays(Math.max(1, lookbackDays));

        Map<String, BigDecimal> net = portfolioStatisticsService.netExposureByCurrency(userId);
        BigDecimal homeNet = net.getOrDefault(homeCurrency, BigDecimal.ZERO);

        // Foreign currencies whose value moves with FX; the home leg is constant.
        Set<String> foreign = new LinkedHashSet<>(net.keySet());
        foreign.remove(homeCurrency);
        if (foreign.isEmpty()) {
            return insufficient(homeCurrency, conf, homeNet, "No foreign-currency exposure to model.");
        }

        List<RateSnapshot> snapshots = rateSnapshotRepository
                .findByBaseAndQuoteInAndCapturedAtAfterOrderByCapturedAtAsc(homeCurrency, foreign, since);

        // Group rates by capture time so each time gives a full portfolio revaluation.
        TreeMap<LocalDateTime, Map<String, BigDecimal>> byTime = new TreeMap<>();
        for (RateSnapshot s : snapshots) {
            byTime.computeIfAbsent(s.getCapturedAt(), t -> new TreeMap<>()).put(s.getQuote(), s.getRate());
        }

        // Revalue the foreign book at each complete snapshot.
        List<BigDecimal> values = new ArrayList<>();
        for (Map<String, BigDecimal> rates : byTime.values()) {
            BigDecimal value = BigDecimal.ZERO;
            boolean complete = true;
            for (String currency : foreign) {
                BigDecimal rate = rates.get(currency);
                if (rate == null || rate.signum() <= 0) {
                    complete = false;
                    break;
                }
                value = value.add(net.get(currency).divide(rate, MONEY_SCALE, RoundingMode.HALF_UP));
            }
            if (complete) {
                values.add(value);
            }
        }

        if (values.size() < 2) {
            return insufficient(homeCurrency, conf, homeNet.add(values.isEmpty() ? BigDecimal.ZERO : values.get(values.size() - 1)),
                    "Not enough rate-snapshot history to model VaR (need at least two complete observations).");
        }

        // Period-over-period P&L changes form the empirical loss distribution.
        List<BigDecimal> pnlChanges = new ArrayList<>();
        for (int i = 1; i < values.size(); i++) {
            pnlChanges.add(values.get(i).subtract(values.get(i - 1)));
        }
        pnlChanges.sort(Comparator.naturalOrder());

        int n = pnlChanges.size();
        int index = (int) Math.floor(BigDecimal.ONE.subtract(conf).doubleValue() * n);
        index = Math.max(0, Math.min(index, n - 1));
        BigDecimal quantilePnl = pnlChanges.get(index);
        BigDecimal var = quantilePnl.negate().max(BigDecimal.ZERO);

        // Expected shortfall: mean loss in the tail at or beyond the VaR quantile.
        BigDecimal tailSum = BigDecimal.ZERO;
        int tailCount = 0;
        for (int i = 0; i <= index; i++) {
            tailSum = tailSum.add(pnlChanges.get(i));
            tailCount++;
        }
        BigDecimal expectedShortfall = tailCount == 0
                ? BigDecimal.ZERO
                : tailSum.divide(BigDecimal.valueOf(tailCount), MONEY_SCALE, RoundingMode.HALF_UP)
                        .negate().max(BigDecimal.ZERO);
        BigDecimal worstLoss = pnlChanges.get(0).negate().max(BigDecimal.ZERO);

        BigDecimal portfolioValue = homeNet.add(values.get(values.size() - 1))
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        return VarResultDTO.builder()
                .home(homeCurrency)
                .confidence(conf)
                .observations(n)
                .portfolioValue(portfolioValue)
                .valueAtRisk(var.setScale(MONEY_SCALE, RoundingMode.HALF_UP))
                .expectedShortfall(expectedShortfall.setScale(MONEY_SCALE, RoundingMode.HALF_UP))
                .worstLoss(worstLoss.setScale(MONEY_SCALE, RoundingMode.HALF_UP))
                .build();
    }

    private VarResultDTO insufficient(String home, BigDecimal conf, BigDecimal portfolioValue, String message) {
        return VarResultDTO.builder()
                .home(home)
                .confidence(conf)
                .observations(0)
                .portfolioValue(portfolioValue.setScale(MONEY_SCALE, RoundingMode.HALF_UP))
                .valueAtRisk(BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP))
                .expectedShortfall(BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP))
                .worstLoss(BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP))
                .message(message)
                .build();
    }

    private BigDecimal normaliseConfidence(BigDecimal confidence) {
        if (confidence == null) {
            return DEFAULT_CONFIDENCE;
        }
        // Accept either a fraction (0.95) or a percentage (95).
        BigDecimal c = confidence.compareTo(BigDecimal.ONE) > 0
                ? confidence.divide(HUNDRED, 6, RoundingMode.HALF_UP)
                : confidence;
        if (c.compareTo(BigDecimal.ZERO) <= 0 || c.compareTo(BigDecimal.ONE) >= 0) {
            return DEFAULT_CONFIDENCE;
        }
        return c;
    }

    private String normaliseHome(String home) {
        return (home == null || home.isBlank()) ? DEFAULT_HOME : home.trim().toUpperCase();
    }

    private LocalDateTime earliest(LocalDateTime current, LocalDateTime candidate) {
        return (current == null || candidate.isBefore(current)) ? candidate : current;
    }

    private LocalDateTime latest(LocalDateTime current, LocalDateTime candidate) {
        return (current == null || candidate.isAfter(current)) ? candidate : current;
    }
}

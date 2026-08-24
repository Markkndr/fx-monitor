package com.currencyexchange.service;

import com.currencyexchange.dto.exchange.ExchangeRateDTO;
import com.currencyexchange.dto.statistics.CurrencyExposureDTO;
import com.currencyexchange.dto.statistics.PortfolioStatisticsDTO;
import com.currencyexchange.entity.Exposure;
import com.currencyexchange.entity.Wallet;
import com.currencyexchange.repository.ExposureRepository;
import com.currencyexchange.repository.WalletRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Computes portfolio-level statistics from a user's wallets.
 *
 * <p>Two of the calculations from the statistics roadmap are covered here:
 * <ul>
 *   <li><b>Netting &amp; aggregation</b> — wallet cash balances and open
 *       {@link Exposure} positions are summed per currency to produce a single net
 *       position for each currency. Wallets and asset-side exposures (receivables,
 *       cash, forecasts, …) add to the position; payables subtract from it.</li>
 *   <li><b>Valuation in home currency</b> — each net position is converted to the
 *       chosen home currency using the latest spot rates from
 *       {@link ExchangeRateService}, and the per-currency values are summed into a
 *       total portfolio value.</li>
 * </ul>
 *
 * <p>Rates from the provider are quoted as "units of currency X per 1 unit of the
 * home currency", so a balance is valued in home currency by dividing by its rate.
 */
@Service
@Slf4j
public class PortfolioStatisticsService {

    private static final String DEFAULT_HOME_CURRENCY = "USD";
    private static final int MONEY_SCALE = 2;
    private static final int PERCENT_SCALE = 2;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private ExposureRepository exposureRepository;

    @Autowired
    private ExchangeRateService exchangeRateService;

    /**
     * The user's net signed position per currency: wallet cash and asset-side
     * exposures add, payables subtract. This is the raw input the analytics services
     * (scenario, stress, attribution, VaR) revalue under different rate assumptions.
     * Amounts are in each position's own currency, not yet converted to a home currency.
     */
    @Transactional(readOnly = true)
    public Map<String, BigDecimal> netExposureByCurrency(Long userId) {
        Map<String, BigDecimal> net = new TreeMap<>();
        for (Wallet wallet : walletRepository.findByUserId(userId)) {
            net.merge(wallet.getCurrency().toUpperCase(), nullToZero(wallet.getBalance()), BigDecimal::add);
        }
        for (Exposure exposure : exposureRepository.findByUserIdAndStatusOrderByCreatedAtDesc(
                userId, Exposure.STATUS_OPEN)) {
            net.merge(exposure.getCurrency().toUpperCase(), exposure.getSignedAmount(), BigDecimal::add);
        }
        return net;
    }

    @Transactional(readOnly = true)
    public PortfolioStatisticsDTO getPortfolioStatistics(Long userId, String homeCurrency) {
        String home = normaliseCurrency(homeCurrency);

        // Netting & aggregation: collapse wallets and open exposures into one net
        // position per currency. Wallet cash and asset-side exposures add; payables
        // subtract (via Exposure#getSignedAmount).
        Map<String, BigDecimal> balanceByCurrency = new TreeMap<>();
        Map<String, BigDecimal> reservedByCurrency = new TreeMap<>();
        for (Wallet wallet : walletRepository.findByUserId(userId)) {
            String currency = wallet.getCurrency().toUpperCase();
            balanceByCurrency.merge(currency, nullToZero(wallet.getBalance()), BigDecimal::add);
            reservedByCurrency.merge(currency, nullToZero(wallet.getReservedAmount()), BigDecimal::add);
        }
        for (Exposure exposure : exposureRepository.findByUserIdAndStatusOrderByCreatedAtDesc(
                userId, Exposure.STATUS_OPEN)) {
            String currency = exposure.getCurrency().toUpperCase();
            balanceByCurrency.merge(currency, exposure.getSignedAmount(), BigDecimal::add);
        }

        if (balanceByCurrency.isEmpty()) {
            return PortfolioStatisticsDTO.builder()
                    .homeCurrency(home)
                    .totalValueInHome(BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP))
                    .currencyCount(0)
                    .exposures(List.of())
                    .unvaluedCurrencies(List.of())
                    .build();
        }

        Map<String, BigDecimal> rates = exchangeRateService.getRates(home).getRates();

        List<CurrencyExposureDTO> exposures = new ArrayList<>();
        List<String> unvalued = new ArrayList<>();
        BigDecimal totalValue = BigDecimal.ZERO;

        for (Map.Entry<String, BigDecimal> entry : balanceByCurrency.entrySet()) {
            String currency = entry.getKey();
            BigDecimal netExposure = entry.getValue();
            BigDecimal reserved = reservedByCurrency.getOrDefault(currency, BigDecimal.ZERO);
            BigDecimal rate = rateToHome(home, currency, rates);

            BigDecimal valueInHome = null;
            if (rate != null && rate.signum() > 0) {
                valueInHome = netExposure.divide(rate, MONEY_SCALE, RoundingMode.HALF_UP);
                totalValue = totalValue.add(valueInHome);
            } else {
                unvalued.add(currency);
            }

            exposures.add(CurrencyExposureDTO.builder()
                    .currency(currency)
                    .netExposure(netExposure)
                    .reservedAmount(reserved)
                    .rateToHome(rate)
                    .valueInHome(valueInHome)
                    .build());
        }

        totalValue = totalValue.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        applyPortfolioShare(exposures, totalValue);

        return PortfolioStatisticsDTO.builder()
                .homeCurrency(home)
                .totalValueInHome(totalValue)
                .currencyCount(exposures.size())
                .exposures(exposures)
                .unvaluedCurrencies(unvalued)
                .build();
    }

    /**
     * Fills in each exposure's share of the total portfolio value. Done in a second
     * pass because the denominator isn't known until every position has been valued.
     */
    private void applyPortfolioShare(List<CurrencyExposureDTO> exposures, BigDecimal totalValue) {
        if (totalValue.signum() <= 0) {
            return;
        }
        for (CurrencyExposureDTO exposure : exposures) {
            if (exposure.getValueInHome() != null) {
                BigDecimal percent = exposure.getValueInHome()
                        .multiply(BigDecimal.valueOf(100))
                        .divide(totalValue, PERCENT_SCALE, RoundingMode.HALF_UP);
                exposure.setPercentOfPortfolio(percent);
            }
        }
    }

    private BigDecimal rateToHome(String home, String currency, Map<String, BigDecimal> rates) {
        if (home.equals(currency)) {
            return BigDecimal.ONE;
        }
        return rates != null ? rates.get(currency) : null;
    }

    private String normaliseCurrency(String currency) {
        return (currency == null || currency.isBlank())
                ? DEFAULT_HOME_CURRENCY
                : currency.trim().toUpperCase();
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}

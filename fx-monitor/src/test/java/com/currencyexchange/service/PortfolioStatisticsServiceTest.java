package com.currencyexchange.service;

import com.currencyexchange.dto.exchange.ExchangeRateDTO;
import com.currencyexchange.dto.statistics.CurrencyExposureDTO;
import com.currencyexchange.dto.statistics.PortfolioStatisticsDTO;
import com.currencyexchange.entity.Exposure;
import com.currencyexchange.entity.Wallet;
import com.currencyexchange.repository.ExposureRepository;
import com.currencyexchange.repository.WalletRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PortfolioStatisticsService")
class PortfolioStatisticsServiceTest {

    private static final Long USER_ID = 7L;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private ExposureRepository exposureRepository;

    @Mock
    private ExchangeRateService exchangeRateService;

    @InjectMocks
    private PortfolioStatisticsService portfolioStatisticsService;

    private Wallet wallet(String currency, String balance, String reserved) {
        Wallet w = new Wallet();
        w.setCurrency(currency);
        w.setBalance(new BigDecimal(balance));
        w.setReservedAmount(new BigDecimal(reserved));
        return w;
    }

    private Exposure exposure(String type, String currency, String amount) {
        Exposure e = new Exposure();
        e.setType(type);
        e.setCurrency(currency);
        e.setAmount(new BigDecimal(amount));
        e.setStatus(Exposure.STATUS_OPEN);
        return e;
    }

    private ExchangeRateDTO rates(Map<String, BigDecimal> rates) {
        return ExchangeRateDTO.builder().base("USD").rates(rates).build();
    }

    @Test
    @DisplayName("nets wallets of the same currency and values each position in the home currency")
    void aggregatesAndValues() {
        when(walletRepository.findByUserId(USER_ID)).thenReturn(List.of(
                wallet("USD", "1000.00", "0.00"),
                wallet("USD", "500.00", "100.00"),
                wallet("EUR", "920.00", "20.00")));
        when(exchangeRateService.getRates("USD")).thenReturn(
                rates(Map.of("EUR", new BigDecimal("0.92"))));

        PortfolioStatisticsDTO stats = portfolioStatisticsService.getPortfolioStatistics(USER_ID, "USD");

        assertThat(stats.getHomeCurrency()).isEqualTo("USD");
        assertThat(stats.getCurrencyCount()).isEqualTo(2);
        // USD: 1500 valued at 1.0 -> 1500; EUR: 920 / 0.92 -> 1000; total 2500
        assertThat(stats.getTotalValueInHome()).isEqualByComparingTo("2500.00");
        assertThat(stats.getUnvaluedCurrencies()).isEmpty();

        CurrencyExposureDTO usd = exposureFor(stats, "USD");
        assertThat(usd.getNetExposure()).isEqualByComparingTo("1500.00");
        assertThat(usd.getReservedAmount()).isEqualByComparingTo("100.00");
        assertThat(usd.getRateToHome()).isEqualByComparingTo("1");
        assertThat(usd.getValueInHome()).isEqualByComparingTo("1500.00");
        assertThat(usd.getPercentOfPortfolio()).isEqualByComparingTo("60.00");

        CurrencyExposureDTO eur = exposureFor(stats, "EUR");
        assertThat(eur.getValueInHome()).isEqualByComparingTo("1000.00");
        assertThat(eur.getPercentOfPortfolio()).isEqualByComparingTo("40.00");
    }

    @Test
    @DisplayName("nets open exposures into the currency position: receivables add, payables subtract")
    void netsExposuresWithWallets() {
        when(walletRepository.findByUserId(USER_ID)).thenReturn(List.of(
                wallet("USD", "1000.00", "0.00"),
                wallet("EUR", "920.00", "0.00")));
        when(exposureRepository.findByUserIdAndStatusOrderByCreatedAtDesc(USER_ID, "OPEN"))
                .thenReturn(List.of(
                        exposure(Exposure.TYPE_RECEIVABLE, "EUR", "460.00"),
                        exposure(Exposure.TYPE_PAYABLE, "EUR", "230.00")));
        when(exchangeRateService.getRates("USD")).thenReturn(
                rates(Map.of("EUR", new BigDecimal("0.92"))));

        PortfolioStatisticsDTO stats = portfolioStatisticsService.getPortfolioStatistics(USER_ID, "USD");

        // EUR net = 920 cash + 460 receivable - 230 payable = 1150; valued at 0.92 -> 1250
        CurrencyExposureDTO eur = exposureFor(stats, "EUR");
        assertThat(eur.getNetExposure()).isEqualByComparingTo("1150.00");
        assertThat(eur.getValueInHome()).isEqualByComparingTo("1250.00");
        // USD 1000 + EUR 1250 = 2250
        assertThat(stats.getTotalValueInHome()).isEqualByComparingTo("2250.00");
    }

    @Test
    @DisplayName("returns a zero portfolio and skips the rate lookup when the user has no wallets")
    void emptyPortfolio() {
        when(walletRepository.findByUserId(USER_ID)).thenReturn(List.of());

        PortfolioStatisticsDTO stats = portfolioStatisticsService.getPortfolioStatistics(USER_ID, "USD");

        assertThat(stats.getCurrencyCount()).isZero();
        assertThat(stats.getExposures()).isEmpty();
        assertThat(stats.getTotalValueInHome()).isEqualByComparingTo("0.00");
        verify(exchangeRateService, never()).getRates(anyString());
    }

    @Test
    @DisplayName("lists currencies with no available rate as unvalued and excludes them from the total")
    void handlesMissingRate() {
        when(walletRepository.findByUserId(USER_ID)).thenReturn(List.of(
                wallet("USD", "200.00", "0.00"),
                wallet("XYZ", "999.00", "0.00")));
        when(exchangeRateService.getRates("USD")).thenReturn(rates(Map.of()));

        PortfolioStatisticsDTO stats = portfolioStatisticsService.getPortfolioStatistics(USER_ID, "USD");

        assertThat(stats.getUnvaluedCurrencies()).containsExactly("XYZ");
        assertThat(stats.getTotalValueInHome()).isEqualByComparingTo("200.00");

        CurrencyExposureDTO xyz = exposureFor(stats, "XYZ");
        assertThat(xyz.getValueInHome()).isNull();
        assertThat(xyz.getPercentOfPortfolio()).isNull();
    }

    @Test
    @DisplayName("defaults the home currency to USD when none is supplied")
    void defaultsHomeCurrency() {
        when(walletRepository.findByUserId(USER_ID)).thenReturn(List.of(
                wallet("USD", "100.00", "0.00")));
        when(exchangeRateService.getRates("USD")).thenReturn(rates(Map.of()));

        PortfolioStatisticsDTO stats = portfolioStatisticsService.getPortfolioStatistics(USER_ID, null);

        assertThat(stats.getHomeCurrency()).isEqualTo("USD");
        verify(exchangeRateService).getRates("USD");
    }

    private CurrencyExposureDTO exposureFor(PortfolioStatisticsDTO stats, String currency) {
        return stats.getExposures().stream()
                .filter(e -> e.getCurrency().equals(currency))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No exposure for " + currency));
    }
}

package com.currencyexchange.service;

import com.currencyexchange.dto.exchange.ExchangeRateDTO;
import com.currencyexchange.dto.exchange.RateHistoryDTO;
import com.currencyexchange.entity.RateSnapshot;
import com.currencyexchange.repository.RateSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RateSnapshotService")
class RateSnapshotServiceTest {

    @Mock
    private RateSnapshotRepository rateSnapshotRepository;

    @Mock
    private ExchangeRateService exchangeRateService;

    @InjectMocks
    private RateSnapshotService rateSnapshotService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(rateSnapshotService, "base", "USD");
        ReflectionTestUtils.setField(rateSnapshotService, "quoteCurrencies", List.of("EUR", "GBP", "XYZ"));
    }

    private ExchangeRateDTO rates(Map<String, BigDecimal> rates) {
        return ExchangeRateDTO.builder().base("USD").rates(rates).build();
    }

    @Test
    @DisplayName("captureSnapshots stores one row per configured pair that has a rate")
    void capturesConfiguredPairs() {
        when(exchangeRateService.getRates("USD")).thenReturn(rates(Map.of(
                "EUR", new BigDecimal("0.92"),
                "GBP", new BigDecimal("0.79"))));

        rateSnapshotService.captureSnapshots();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RateSnapshot>> captor = ArgumentCaptor.forClass(List.class);
        verify(rateSnapshotRepository).saveAll(captor.capture());
        List<RateSnapshot> saved = captor.getValue();

        // XYZ has no rate from the provider, so it is skipped.
        assertThat(saved).hasSize(2);
        assertThat(saved).allSatisfy(s -> {
            assertThat(s.getBase()).isEqualTo("USD");
            assertThat(s.getCapturedAt()).isNotNull();
        });
        assertThat(saved).extracting(RateSnapshot::getQuote).containsExactlyInAnyOrder("EUR", "GBP");
    }

    @Test
    @DisplayName("captureSnapshots swallows provider failures so the scheduler survives")
    void swallowsProviderFailure() {
        when(exchangeRateService.getRates("USD")).thenThrow(new RuntimeException("provider down"));

        rateSnapshotService.captureSnapshots();

        verify(rateSnapshotRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("getHistory returns the stored series ordered for a pair")
    void returnsHistory() {
        RateSnapshot older = snapshot("EUR", "0.90", LocalDateTime.now().minusDays(1));
        RateSnapshot newer = snapshot("EUR", "0.92", LocalDateTime.now());
        when(rateSnapshotRepository.findByBaseAndQuoteOrderByCapturedAtAsc("USD", "EUR"))
                .thenReturn(List.of(older, newer));

        RateHistoryDTO history = rateSnapshotService.getHistory("usd", "eur");

        assertThat(history.getBase()).isEqualTo("USD");
        assertThat(history.getQuote()).isEqualTo("EUR");
        assertThat(history.getPoints()).hasSize(2);
        assertThat(history.getPoints().get(0).getRate()).isEqualByComparingTo("0.90");
        assertThat(history.getPoints().get(1).getRate()).isEqualByComparingTo("0.92");
    }

    private RateSnapshot snapshot(String quote, String rate, LocalDateTime at) {
        RateSnapshot s = new RateSnapshot();
        s.setBase("USD");
        s.setQuote(quote);
        s.setRate(new BigDecimal(rate));
        s.setCapturedAt(at);
        return s;
    }
}

package com.currencyexchange.service;

import com.currencyexchange.dto.exchange.ExchangeRateDTO;
import com.currencyexchange.dto.hedges.CreateHedgeRequestDTO;
import com.currencyexchange.dto.hedges.HedgeDTO;
import com.currencyexchange.entity.Exposure;
import com.currencyexchange.entity.Hedge;
import com.currencyexchange.entity.User;
import com.currencyexchange.exception.InvalidHedgeException;
import com.currencyexchange.repository.ExposureRepository;
import com.currencyexchange.repository.HedgeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("HedgeService")
class HedgeServiceTest {

    private static final Long USER_ID = 7L;

    @Mock
    private HedgeRepository hedgeRepository;
    @Mock
    private ExposureRepository exposureRepository;
    @Mock
    private UserService userService;
    @Mock
    private ExchangeRateService exchangeRateService;
    @InjectMocks
    private HedgeService hedgeService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(USER_ID);
    }

    private void stubSpot(String base, String quote, String rate) {
        lenient().when(exchangeRateService.getRates(anyString())).thenReturn(ExchangeRateDTO.builder()
                .base(base)
                .rates(Map.of(quote, new BigDecimal(rate)))
                .build());
    }

    private Hedge forward(String direction, String notional, String contractRate) {
        Hedge h = new Hedge();
        h.setUser(user);
        h.setInstrumentType(Hedge.INSTRUMENT_FORWARD);
        h.setDirection(direction);
        h.setBaseCurrency("EUR");
        h.setQuoteCurrency("USD");
        h.setNotional(new BigDecimal(notional));
        h.setContractRate(new BigDecimal(contractRate));
        return h;
    }

    @Test
    @DisplayName("a SELL forward gains when the base currency weakens below the contracted rate")
    void sellForwardMarkToMarket() {
        BigDecimal mtm = hedgeService.markToMarket(
                forward(Hedge.DIRECTION_SELL, "100000", "1.10"), new BigDecimal("1.05"));
        // Sold EUR at 1.10, now worth 1.05 → gain of 0.05 * 100k = 5000 USD.
        assertThat(mtm).isEqualByComparingTo("5000.00");
    }

    @Test
    @DisplayName("a BUY forward loses when the base currency weakens below the contracted rate")
    void buyForwardMarkToMarket() {
        BigDecimal mtm = hedgeService.markToMarket(
                forward(Hedge.DIRECTION_BUY, "100000", "1.10"), new BigDecimal("1.05"));
        assertThat(mtm).isEqualByComparingTo("-5000.00");
    }

    @Test
    @DisplayName("a call option marks to its intrinsic value and never goes negative")
    void callOptionIntrinsic() {
        Hedge call = forward(Hedge.DIRECTION_BUY, "100000", "1.10");
        call.setInstrumentType(Hedge.INSTRUMENT_OPTION);
        call.setOptionType(Hedge.OPTION_CALL);

        assertThat(hedgeService.markToMarket(call, new BigDecimal("1.15"))).isEqualByComparingTo("5000.00");
        assertThat(hedgeService.markToMarket(call, new BigDecimal("1.05"))).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("createHedge requires an option type for an option instrument")
    void optionRequiresType() {
        CreateHedgeRequestDTO request = new CreateHedgeRequestDTO();
        request.setInstrumentType("OPTION");
        request.setDirection("BUY");
        request.setBaseCurrency("EUR");
        request.setQuoteCurrency("USD");
        request.setNotional(new BigDecimal("1000"));
        request.setContractRate(new BigDecimal("1.10"));

        when(userService.getUserById(USER_ID)).thenReturn(user);

        assertThatThrownBy(() -> hedgeService.createHedge(USER_ID, request))
                .isInstanceOf(InvalidHedgeException.class)
                .hasMessageContaining("Option type");
    }

    @Test
    @DisplayName("a SELL forward perfectly hedging a receivable scores 100% effectiveness")
    void perfectHedgeEffectiveness() {
        Exposure receivable = new Exposure();
        receivable.setId(1L);
        receivable.setUser(user);
        receivable.setType(Exposure.TYPE_RECEIVABLE);
        receivable.setCurrency("EUR");
        receivable.setAmount(new BigDecimal("100000"));
        receivable.setStatus(Exposure.STATUS_OPEN);

        Hedge hedge = forward(Hedge.DIRECTION_SELL, "100000", "1.10");
        hedge.setExposure(receivable);

        when(hedgeRepository.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(java.util.List.of(hedge));
        stubSpot("EUR", "USD", "1.05");

        HedgeDTO dto = hedgeService.getUserHedges(USER_ID).get(0);

        assertThat(dto.getHedgeRatioPercent()).isEqualByComparingTo("100.00");
        assertThat(dto.getEffectivenessPercent()).isEqualByComparingTo("100.00");
        assertThat(dto.getEffective()).isTrue();
        assertThat(dto.getMarkToMarket()).isEqualByComparingTo("5000.00");
    }

    @Test
    @DisplayName("createHedge links an owned exposure and derives the hedge ratio")
    void linksExposure() {
        Exposure exposure = new Exposure();
        exposure.setId(2L);
        exposure.setUser(user);
        exposure.setType(Exposure.TYPE_PAYABLE);
        exposure.setCurrency("EUR");
        exposure.setAmount(new BigDecimal("50000"));

        CreateHedgeRequestDTO request = new CreateHedgeRequestDTO();
        request.setInstrumentType("FORWARD");
        request.setDirection("BUY");
        request.setBaseCurrency("EUR");
        request.setQuoteCurrency("USD");
        request.setNotional(new BigDecimal("25000"));
        request.setContractRate(new BigDecimal("1.10"));
        request.setExposureId(2L);

        when(userService.getUserById(USER_ID)).thenReturn(user);
        when(exposureRepository.findById(2L)).thenReturn(Optional.of(exposure));
        when(hedgeRepository.save(any(Hedge.class))).thenAnswer(inv -> inv.getArgument(0));
        stubSpot("EUR", "USD", "1.10");

        HedgeDTO dto = hedgeService.createHedge(USER_ID, request);

        assertThat(dto.getExposureId()).isEqualTo(2L);
        assertThat(dto.getHedgeRatioPercent()).isEqualByComparingTo("50.00");
    }
}

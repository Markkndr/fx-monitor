package com.currencyexchange.service;

import com.currencyexchange.dto.alerts.AlertDTO;
import com.currencyexchange.dto.alerts.CreateAlertRequestDTO;
import com.currencyexchange.dto.exchange.ExchangeRateDTO;
import com.currencyexchange.entity.RateAlert;
import com.currencyexchange.entity.User;
import com.currencyexchange.exception.InvalidAlertException;
import com.currencyexchange.repository.RateAlertRepository;
import org.junit.jupiter.api.BeforeEach;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RateAlertService")
class RateAlertServiceTest {

    private static final Long USER_ID = 7L;

    @Mock
    private RateAlertRepository rateAlertRepository;
    @Mock
    private UserService userService;
    @Mock
    private ExchangeRateService exchangeRateService;
    @InjectMocks
    private RateAlertService rateAlertService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(USER_ID);
    }

    private CreateAlertRequestDTO request(String base, String quote, String direction, String threshold) {
        return new CreateAlertRequestDTO(base, quote, direction, new BigDecimal(threshold), null);
    }

    @Test
    @DisplayName("createAlert normalises fields and persists an ACTIVE alert")
    void createsAlert() {
        when(userService.getUserById(USER_ID)).thenReturn(user);
        when(rateAlertRepository.save(any(RateAlert.class))).thenAnswer(inv -> inv.getArgument(0));

        AlertDTO dto = rateAlertService.createAlert(USER_ID, request("usd", "eur", "above", "0.95"));

        assertThat(dto.getBase()).isEqualTo("USD");
        assertThat(dto.getQuote()).isEqualTo("EUR");
        assertThat(dto.getDirection()).isEqualTo("ABOVE");
        assertThat(dto.getStatus()).isEqualTo(RateAlert.STATUS_ACTIVE);
    }

    @Test
    @DisplayName("createAlert rejects an identical base and quote")
    void rejectsSameCurrency() {
        when(userService.getUserById(USER_ID)).thenReturn(user);

        assertThatThrownBy(() -> rateAlertService.createAlert(USER_ID, request("USD", "USD", "ABOVE", "1")))
                .isInstanceOf(InvalidAlertException.class);
    }

    @Test
    @DisplayName("createAlert rejects an unknown direction")
    void rejectsUnknownDirection() {
        when(userService.getUserById(USER_ID)).thenReturn(user);

        assertThatThrownBy(() -> rateAlertService.createAlert(USER_ID, request("USD", "EUR", "SIDEWAYS", "1")))
                .isInstanceOf(InvalidAlertException.class);
    }

    @Test
    @DisplayName("evaluateActiveAlerts fires an ABOVE alert once the rate crosses the threshold")
    void firesBreachedAlert() {
        RateAlert alert = new RateAlert();
        alert.setUser(user);
        alert.setBase("USD");
        alert.setQuote("EUR");
        alert.setDirection(RateAlert.DIRECTION_ABOVE);
        alert.setThreshold(new BigDecimal("0.90"));
        alert.setStatus(RateAlert.STATUS_ACTIVE);

        when(rateAlertRepository.findByStatus(RateAlert.STATUS_ACTIVE)).thenReturn(List.of(alert));
        when(exchangeRateService.getRates(anyString())).thenReturn(ExchangeRateDTO.builder()
                .base("USD")
                .rates(Map.of("EUR", new BigDecimal("0.95")))
                .build());
        when(rateAlertRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        rateAlertService.evaluateActiveAlerts();

        assertThat(alert.getStatus()).isEqualTo(RateAlert.STATUS_TRIGGERED);
        assertThat(alert.getTriggeredRate()).isEqualByComparingTo("0.95");
        assertThat(alert.getTriggeredAt()).isNotNull();
    }

    @Test
    @DisplayName("evaluateActiveAlerts leaves an un-breached alert ACTIVE but records the rate")
    void leavesUnbreachedAlertActive() {
        RateAlert alert = new RateAlert();
        alert.setUser(user);
        alert.setBase("USD");
        alert.setQuote("EUR");
        alert.setDirection(RateAlert.DIRECTION_BELOW);
        alert.setThreshold(new BigDecimal("0.80"));
        alert.setStatus(RateAlert.STATUS_ACTIVE);

        when(rateAlertRepository.findByStatus(RateAlert.STATUS_ACTIVE)).thenReturn(List.of(alert));
        when(exchangeRateService.getRates(anyString())).thenReturn(ExchangeRateDTO.builder()
                .base("USD")
                .rates(Map.of("EUR", new BigDecimal("0.95")))
                .build());
        when(rateAlertRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        rateAlertService.evaluateActiveAlerts();

        assertThat(alert.getStatus()).isEqualTo(RateAlert.STATUS_ACTIVE);
        assertThat(alert.getLastCheckedRate()).isEqualByComparingTo("0.95");
    }
}

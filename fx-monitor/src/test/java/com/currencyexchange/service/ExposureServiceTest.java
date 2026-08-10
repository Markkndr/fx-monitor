package com.currencyexchange.service;

import com.currencyexchange.dto.exposures.CreateExposureRequestDTO;
import com.currencyexchange.dto.exposures.ExposureDTO;
import com.currencyexchange.entity.Exposure;
import com.currencyexchange.entity.User;
import com.currencyexchange.exception.ExposureNotFoundException;
import com.currencyexchange.exception.InvalidExposureException;
import com.currencyexchange.repository.ExposureRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExposureService")
class ExposureServiceTest {

    @Mock
    private ExposureRepository exposureRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private ExposureService exposureService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(7L);
        user.setEmail("bob@example.com");
    }

    private Exposure exposureOwnedBy(Long id, User owner) {
        Exposure e = new Exposure();
        e.setId(id);
        e.setUser(owner);
        e.setType(Exposure.TYPE_RECEIVABLE);
        e.setCurrency("EUR");
        e.setAmount(new BigDecimal("1000.00"));
        e.setStatus(Exposure.STATUS_OPEN);
        return e;
    }

    @Test
    @DisplayName("createExposure normalises type/currency and persists an OPEN position")
    void createsExposure() {
        CreateExposureRequestDTO request = new CreateExposureRequestDTO();
        request.setType("receivable");
        request.setCurrency("eur");
        request.setAmount(new BigDecimal("1500.00"));
        request.setCounterparty("  German customer  ");

        when(userService.getUserById(7L)).thenReturn(user);
        when(exposureRepository.save(any(Exposure.class))).thenAnswer(inv -> inv.getArgument(0));

        ExposureDTO dto = exposureService.createExposure(7L, request);

        ArgumentCaptor<Exposure> captor = ArgumentCaptor.forClass(Exposure.class);
        verify(exposureRepository).save(captor.capture());
        Exposure saved = captor.getValue();

        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getType()).isEqualTo("RECEIVABLE");
        assertThat(saved.getCurrency()).isEqualTo("EUR");
        assertThat(saved.getStatus()).isEqualTo("OPEN");
        assertThat(saved.getCounterparty()).isEqualTo("German customer");
        assertThat(dto.getSignedAmount()).isEqualByComparingTo("1500.00");
    }

    @Test
    @DisplayName("createExposure signs a payable as a negative position")
    void payableIsNegative() {
        CreateExposureRequestDTO request = new CreateExposureRequestDTO();
        request.setType("PAYABLE");
        request.setCurrency("GBP");
        request.setAmount(new BigDecimal("300.00"));

        when(userService.getUserById(7L)).thenReturn(user);
        when(exposureRepository.save(any(Exposure.class))).thenAnswer(inv -> inv.getArgument(0));

        ExposureDTO dto = exposureService.createExposure(7L, request);

        assertThat(dto.getSignedAmount()).isEqualByComparingTo("-300.00");
    }

    @Test
    @DisplayName("createExposure rejects an unknown type")
    void rejectsUnknownType() {
        CreateExposureRequestDTO request = new CreateExposureRequestDTO();
        request.setType("SWAP");
        request.setCurrency("EUR");
        request.setAmount(new BigDecimal("100.00"));

        when(userService.getUserById(7L)).thenReturn(user);

        assertThatThrownBy(() -> exposureService.createExposure(7L, request))
                .isInstanceOf(InvalidExposureException.class)
                .hasMessageContaining("SWAP");

        verify(exposureRepository, never()).save(any());
    }

    @Test
    @DisplayName("getUserExposure hides an exposure owned by another user")
    void getForeignExposureThrowsNotFound() {
        User other = new User();
        other.setId(99L);
        when(exposureRepository.findById(1L)).thenReturn(Optional.of(exposureOwnedBy(1L, other)));

        assertThatThrownBy(() -> exposureService.getUserExposure(7L, 1L))
                .isInstanceOf(ExposureNotFoundException.class)
                .hasMessageContaining("1");
    }

    @Test
    @DisplayName("updateStatus settles an owned exposure")
    void settlesOwnedExposure() {
        when(exposureRepository.findById(1L)).thenReturn(Optional.of(exposureOwnedBy(1L, user)));
        when(exposureRepository.save(any(Exposure.class))).thenAnswer(inv -> inv.getArgument(0));

        ExposureDTO dto = exposureService.updateStatus(7L, 1L, "settled");

        assertThat(dto.getStatus()).isEqualTo("SETTLED");
    }

    @Test
    @DisplayName("updateStatus rejects an unknown status")
    void rejectsUnknownStatus() {
        when(exposureRepository.findById(1L)).thenReturn(Optional.of(exposureOwnedBy(1L, user)));

        assertThatThrownBy(() -> exposureService.updateStatus(7L, 1L, "FROZEN"))
                .isInstanceOf(InvalidExposureException.class);

        verify(exposureRepository, never()).save(any());
    }
}

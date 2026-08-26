package com.currencyexchange.service;

import com.currencyexchange.dto.hedges.HedgeDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ComplianceReportService")
class ComplianceReportServiceTest {

    private static final Long USER_ID = 7L;

    @Mock
    private HedgeService hedgeService;
    @InjectMocks
    private ComplianceReportService complianceReportService;

    private HedgeDTO.HedgeDTOBuilder baseHedge() {
        return HedgeDTO.builder()
                .instrumentType("FORWARD")
                .direction("SELL")
                .baseCurrency("EUR")
                .quoteCurrency("USD")
                .notional(new BigDecimal("1000"))
                .status("OPEN");
    }

    @Test
    @DisplayName("produces a well-formed PDF")
    void producesPdf() {
        when(hedgeService.getUserHedges(USER_ID)).thenReturn(List.of(
                // designated + passing (effectiveness in band)
                baseHedge().id(1L).exposureId(10L)
                        .hedgeRatioPercent(new BigDecimal("100"))
                        .effectivenessPercent(new BigDecimal("98.50")).effective(Boolean.TRUE).build(),
                // designated + failing (effectiveness outside band)
                baseHedge().id(2L).exposureId(11L)
                        .hedgeRatioPercent(new BigDecimal("50"))
                        .effectivenessPercent(new BigDecimal("60.00")).effective(Boolean.FALSE).build(),
                // designated but no rate move — not assessable
                baseHedge().id(3L).exposureId(12L)
                        .hedgeRatioPercent(new BigDecimal("100")).build(),
                // undesignated economic hedge
                baseHedge().id(4L).build()));

        byte[] pdf = complianceReportService.generateComplianceReport(USER_ID, "USD");

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        assertThat(new String(pdf)).contains("%%EOF");
    }

    @Test
    @DisplayName("handles a user with no hedges")
    void handlesNoHedges() {
        when(hedgeService.getUserHedges(USER_ID)).thenReturn(List.of());

        byte[] pdf = complianceReportService.generateComplianceReport(USER_ID, "USD");

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    @DisplayName("defaults a blank home currency to USD without error")
    void defaultsBlankHome() {
        when(hedgeService.getUserHedges(USER_ID)).thenReturn(List.of());

        byte[] pdf = complianceReportService.generateComplianceReport(USER_ID, "  ");

        assertThat(pdf).isNotEmpty();
    }
}

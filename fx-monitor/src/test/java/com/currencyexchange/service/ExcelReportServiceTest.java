package com.currencyexchange.service;

import com.currencyexchange.dto.exchange.RateHistoryDTO;
import com.currencyexchange.dto.exposures.ExposureDTO;
import com.currencyexchange.dto.hedges.HedgeDTO;
import com.currencyexchange.dto.statistics.CurrencyExposureDTO;
import com.currencyexchange.dto.statistics.PortfolioStatisticsDTO;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExcelReportService")
class ExcelReportServiceTest {

    private static final Long USER_ID = 7L;

    @Mock
    private PortfolioStatisticsService portfolioStatisticsService;
    @Mock
    private ExposureService exposureService;
    @Mock
    private HedgeService hedgeService;
    @Mock
    private RateSnapshotService rateSnapshotService;
    @InjectMocks
    private ExcelReportService excelReportService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(excelReportService, "snapshotBase", "USD");
        ReflectionTestUtils.setField(excelReportService, "snapshotCurrencies", List.of("EUR"));
    }

    @Test
    @DisplayName("builds a workbook with one sheet per section and the expected data")
    void buildsWorkbook() throws Exception {
        when(portfolioStatisticsService.getPortfolioStatistics(eq(USER_ID), anyString()))
                .thenReturn(PortfolioStatisticsDTO.builder()
                        .homeCurrency("USD")
                        .totalValueInHome(new BigDecimal("12345.67"))
                        .currencyCount(2)
                        .exposures(List.of(CurrencyExposureDTO.builder()
                                .currency("EUR")
                                .netExposure(new BigDecimal("1000"))
                                .valueInHome(new BigDecimal("1080"))
                                .percentOfPortfolio(new BigDecimal("8.75"))
                                .build()))
                        .unvaluedCurrencies(List.of())
                        .build());

        when(exposureService.getUserExposures(USER_ID)).thenReturn(List.of(ExposureDTO.builder()
                .id(1L)
                .type("RECEIVABLE")
                .currency("EUR")
                .amount(new BigDecimal("1000"))
                .signedAmount(new BigDecimal("1000"))
                .counterparty("ACME GmbH")
                .status("OPEN")
                .maturityDate(LocalDate.of(2026, 12, 31))
                .build()));

        when(hedgeService.getUserHedges(USER_ID)).thenReturn(List.of(HedgeDTO.builder()
                .id(5L)
                .exposureId(1L)
                .instrumentType("FORWARD")
                .direction("SELL")
                .baseCurrency("EUR")
                .quoteCurrency("USD")
                .notional(new BigDecimal("1000"))
                .contractRate(new BigDecimal("1.08"))
                .markToMarket(new BigDecimal("25.00"))
                .unrealizedPnl(new BigDecimal("25.00"))
                .effectivenessPercent(new BigDecimal("96.2"))
                .effective(Boolean.TRUE)
                .build()));

        when(rateSnapshotService.getHistory("USD", "EUR")).thenReturn(RateHistoryDTO.builder()
                .base("USD")
                .quote("EUR")
                .points(List.of(RateHistoryDTO.Point.builder()
                        .capturedAt(LocalDateTime.of(2026, 8, 25, 10, 0))
                        .rate(new BigDecimal("0.92"))
                        .build()))
                .build());

        byte[] bytes = excelReportService.generateWorkbook(USER_ID, "USD");

        assertThat(bytes).isNotEmpty();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(wb.getNumberOfSheets()).isEqualTo(4);
            assertThat(wb.getSheetName(0)).isEqualTo("Portfolio Summary");
            assertThat(wb.getSheetName(1)).isEqualTo("Exposures");
            assertThat(wb.getSheetName(2)).isEqualTo("Hedges");
            assertThat(wb.getSheetName(3)).isEqualTo("Rate History");

            // Portfolio summary carries the total value somewhere in the sheet.
            Sheet summary = wb.getSheetAt(0);
            assertThat(cellText(summary, 1, 1)).isEqualTo("12345.67");

            // Exposures sheet: header + one data row with the counterparty.
            Sheet exposures = wb.getSheetAt(1);
            assertThat(exposures.getRow(1).getCell(5).getStringCellValue()).isEqualTo("ACME GmbH");

            // Hedges sheet: the pair is rendered as EUR/USD and effectiveness is present.
            Sheet hedges = wb.getSheetAt(2);
            assertThat(hedges.getRow(1).getCell(4).getStringCellValue()).isEqualTo("EUR/USD");
            assertThat(hedges.getRow(1).getCell(12).getStringCellValue()).isEqualTo("YES");

            // Rate history sheet: one point for USD/EUR.
            Sheet rates = wb.getSheetAt(3);
            assertThat(rates.getRow(1).getCell(0).getStringCellValue()).isEqualTo("USD/EUR");
            assertThat(rates.getRow(1).getCell(2).getNumericCellValue()).isEqualTo(0.92);
        }
    }

    private static String cellText(Sheet sheet, int row, int col) {
        var cell = sheet.getRow(row).getCell(col);
        return cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC
                ? new BigDecimal(String.valueOf(cell.getNumericCellValue())).stripTrailingZeros().toPlainString()
                : cell.getStringCellValue();
    }
}

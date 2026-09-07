package com.currencyexchange.service;

import com.currencyexchange.dto.exchange.RateHistoryDTO;
import com.currencyexchange.dto.exposures.ExposureDTO;
import com.currencyexchange.dto.hedges.HedgeDTO;
import com.currencyexchange.dto.statistics.CurrencyExposureDTO;
import com.currencyexchange.dto.statistics.PortfolioStatisticsDTO;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Builds a multi-sheet Excel (.xlsx) workbook summarising a single user's FX
 * position: a portfolio summary, the underlying exposures, the hedges (with
 * their mark-to-market valuation), and the stored rate history for the
 * configured pairs. Everything is scoped to the supplied {@code userId}.
 */
@Service
@Slf4j
public class ExcelReportService {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Autowired
    private PortfolioStatisticsService portfolioStatisticsService;

    @Autowired
    private ExposureService exposureService;

    @Autowired
    private HedgeService hedgeService;

    @Autowired
    private RateSnapshotService rateSnapshotService;

    @Value("${forex.snapshot.base:USD}")
    private String snapshotBase;

    @Value("${forex.snapshot.currencies:EUR,GBP,JPY,CNY,CHF,CAD,AUD}")
    private List<String> snapshotCurrencies;

    /**
     * Generates the workbook for {@code userId}, valuing the portfolio summary in
     * {@code homeCurrency}, and returns it as raw {@code .xlsx} bytes.
     */
    public byte[] generateWorkbook(Long userId, String homeCurrency) {
        String home = (homeCurrency == null || homeCurrency.isBlank())
                ? "USD" : homeCurrency.toUpperCase();

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            CellStyle headerStyle = headerStyle(workbook);

            writePortfolioSummary(workbook, headerStyle, userId, home);
            writeExposures(workbook, headerStyle, userId);
            writeHedges(workbook, headerStyle, userId);
            writeRateHistory(workbook, headerStyle);

            workbook.write(out);
            log.info("Generated Excel report for user {} (home {})", userId, home);
            return out.toByteArray();
        } catch (IOException e) {
            // In-memory streams don't really throw, but the API signature forces us to handle it.
            throw new UncheckedIOException("Failed to generate Excel report", e);
        }
    }

    private void writePortfolioSummary(Workbook workbook, CellStyle headerStyle, Long userId, String home) {
        Sheet sheet = workbook.createSheet("Portfolio Summary");
        PortfolioStatisticsDTO stats = portfolioStatisticsService.getPortfolioStatistics(userId, home);

        int r = 0;
        Row title = sheet.createRow(r++);
        cell(title, 0, headerStyle, "Home currency");
        cell(title, 1, null, stats.getHomeCurrency());

        Row totalRow = sheet.createRow(r++);
        cell(totalRow, 0, headerStyle, "Total value in home");
        numeric(totalRow, 1, stats.getTotalValueInHome());

        Row countRow = sheet.createRow(r++);
        cell(countRow, 0, headerStyle, "Currencies held");
        numeric(countRow, 1, BigDecimal.valueOf(stats.getCurrencyCount()));

        if (stats.getUnvaluedCurrencies() != null && !stats.getUnvaluedCurrencies().isEmpty()) {
            Row unvalued = sheet.createRow(r++);
            cell(unvalued, 0, headerStyle, "Unvalued currencies");
            cell(unvalued, 1, null, String.join(", ", stats.getUnvaluedCurrencies()));
        }

        r++; // spacer row

        String[] headers = {"Currency", "Net exposure", "Reserved", "Rate to home", "Value in home", "% of portfolio"};
        writeHeader(sheet.createRow(r++), headerStyle, headers);

        List<CurrencyExposureDTO> exposures = stats.getExposures();
        if (exposures != null) {
            for (CurrencyExposureDTO e : exposures) {
                Row row = sheet.createRow(r++);
                cell(row, 0, null, e.getCurrency());
                numeric(row, 1, e.getNetExposure());
                numeric(row, 2, e.getReservedAmount());
                numeric(row, 3, e.getRateToHome());
                numeric(row, 4, e.getValueInHome());
                numeric(row, 5, e.getPercentOfPortfolio());
            }
        }
        autoSize(sheet, headers.length);
    }

    private void writeExposures(Workbook workbook, CellStyle headerStyle, Long userId) {
        Sheet sheet = workbook.createSheet("Exposures");
        String[] headers = {"ID", "Type", "Currency", "Amount", "Signed amount", "Counterparty",
                "Entity", "Value date", "Maturity date", "Status", "Description"};
        writeHeader(sheet.createRow(0), headerStyle, headers);

        int r = 1;
        for (ExposureDTO e : exposureService.getUserExposures(userId)) {
            Row row = sheet.createRow(r++);
            numeric(row, 0, e.getId() == null ? null : BigDecimal.valueOf(e.getId()));
            cell(row, 1, null, e.getType());
            cell(row, 2, null, e.getCurrency());
            numeric(row, 3, e.getAmount());
            numeric(row, 4, e.getSignedAmount());
            cell(row, 5, null, e.getCounterparty());
            cell(row, 6, null, e.getEntityName());
            cell(row, 7, null, e.getValueDate() == null ? null : e.getValueDate().toString());
            cell(row, 8, null, e.getMaturityDate() == null ? null : e.getMaturityDate().toString());
            cell(row, 9, null, e.getStatus());
            cell(row, 10, null, e.getDescription());
        }
        autoSize(sheet, headers.length);
    }

    private void writeHedges(Workbook workbook, CellStyle headerStyle, Long userId) {
        Sheet sheet = workbook.createSheet("Hedges");
        String[] headers = {"ID", "Exposure ID", "Instrument", "Direction", "Pair", "Notional",
                "Contract rate", "Spot rate", "Mark-to-market", "Unrealized P&L",
                "Hedge ratio %", "Effectiveness %", "Effective", "Maturity date", "Status"};
        writeHeader(sheet.createRow(0), headerStyle, headers);

        int r = 1;
        for (HedgeDTO h : hedgeService.getUserHedges(userId)) {
            Row row = sheet.createRow(r++);
            numeric(row, 0, h.getId() == null ? null : BigDecimal.valueOf(h.getId()));
            numeric(row, 1, h.getExposureId() == null ? null : BigDecimal.valueOf(h.getExposureId()));
            cell(row, 2, null, h.getInstrumentType());
            cell(row, 3, null, h.getDirection());
            cell(row, 4, null, pair(h));
            numeric(row, 5, h.getNotional());
            numeric(row, 6, h.getContractRate());
            numeric(row, 7, h.getSpotRate());
            numeric(row, 8, h.getMarkToMarket());
            numeric(row, 9, h.getUnrealizedPnl());
            numeric(row, 10, h.getHedgeRatioPercent());
            numeric(row, 11, h.getEffectivenessPercent());
            cell(row, 12, null, h.getEffective() == null ? null : (h.getEffective() ? "YES" : "NO"));
            cell(row, 13, null, h.getMaturityDate() == null ? null : h.getMaturityDate().toString());
            cell(row, 14, null, h.getStatus());
        }
        autoSize(sheet, headers.length);
    }

    private void writeRateHistory(Workbook workbook, CellStyle headerStyle) {
        Sheet sheet = workbook.createSheet("Rate History");
        String[] headers = {"Pair", "Captured at", "Rate"};
        writeHeader(sheet.createRow(0), headerStyle, headers);

        String base = snapshotBase.toUpperCase();
        int r = 1;
        for (String quoteRaw : snapshotCurrencies) {
            String quote = quoteRaw.trim().toUpperCase();
            RateHistoryDTO history = rateSnapshotService.getHistory(base, quote);
            if (history.getPoints() == null) {
                continue;
            }
            String label = base + "/" + quote;
            for (RateHistoryDTO.Point p : history.getPoints()) {
                Row row = sheet.createRow(r++);
                cell(row, 0, null, label);
                cell(row, 1, null, p.getCapturedAt() == null ? null : TIMESTAMP.format(p.getCapturedAt()));
                numeric(row, 2, p.getRate());
            }
        }
        autoSize(sheet, headers.length);
    }

    // --- small POI helpers ---------------------------------------------------

    private static String pair(HedgeDTO h) {
        if (h.getBaseCurrency() == null || h.getQuoteCurrency() == null) {
            return null;
        }
        return h.getBaseCurrency() + "/" + h.getQuoteCurrency();
    }

    private static CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private static void writeHeader(Row row, CellStyle style, String[] headers) {
        for (int i = 0; i < headers.length; i++) {
            cell(row, i, style, headers[i]);
        }
    }

    private static void cell(Row row, int col, CellStyle style, String value) {
        Cell cell = row.createCell(col);
        if (value != null) {
            cell.setCellValue(value);
        }
        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    private static void numeric(Row row, int col, BigDecimal value) {
        Cell cell = row.createCell(col);
        if (value != null) {
            cell.setCellValue(value.doubleValue());
        }
    }

    private static void autoSize(Sheet sheet, int columns) {
        for (int i = 0; i < columns; i++) {
            sheet.autoSizeColumn(i);
        }
    }
}

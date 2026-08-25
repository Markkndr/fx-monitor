package com.currencyexchange.service;

import com.currencyexchange.dto.analytics.AttributionResultDTO;
import com.currencyexchange.dto.analytics.CurrencyAttributionDTO;
import com.currencyexchange.dto.analytics.ScenarioResultDTO;
import com.currencyexchange.dto.analytics.StressTestResultDTO;
import com.currencyexchange.dto.analytics.VarResultDTO;
import com.currencyexchange.dto.hedges.HedgeDTO;
import com.currencyexchange.dto.statistics.CurrencyExposureDTO;
import com.currencyexchange.dto.statistics.PortfolioStatisticsDTO;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Renders a one-page (or so) executive summary of a single user's FX risk to a
 * PDF: portfolio value and net exposure by currency, a hedging overview, Value at
 * Risk, FX P&amp;L attribution, and the standard stress-test battery. Everything is
 * scoped to the supplied {@code userId} and valued in a single home currency.
 */
@Service
@Slf4j
public class PdfReportService {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final Color NAVY = new Color(0x0B, 0x3D, 0x59);
    private static final Color LIGHT = new Color(0xE8, 0xEE, 0xF3);
    private static final Color POSITIVE = new Color(0x1B, 0x7A, 0x3D);
    private static final Color NEGATIVE = new Color(0xB0, 0x2A, 0x2A);

    private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, NAVY);
    private static final Font SUBTITLE = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.DARK_GRAY);
    private static final Font SECTION = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, NAVY);
    private static final Font TH = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
    private static final Font TD = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);
    private static final Font LABEL = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.BLACK);
    private static final Font VALUE = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);

    @Autowired
    private PortfolioStatisticsService portfolioStatisticsService;

    @Autowired
    private HedgeService hedgeService;

    @Autowired
    private RiskMetricsService riskMetricsService;

    @Autowired
    private ScenarioAnalysisService scenarioAnalysisService;

    /**
     * Generates the executive-summary PDF for {@code userId}, valued in
     * {@code homeCurrency}, and returns it as raw PDF bytes.
     */
    public byte[] generateExecutiveSummary(Long userId, String homeCurrency) {
        String home = (homeCurrency == null || homeCurrency.isBlank())
                ? "USD" : homeCurrency.toUpperCase();

        PortfolioStatisticsDTO stats = portfolioStatisticsService.getPortfolioStatistics(userId, home);
        List<HedgeDTO> hedges = hedgeService.getUserHedges(userId);
        VarResultDTO var = riskMetricsService.valueAtRisk(userId, home, null, 365);
        AttributionResultDTO attribution = riskMetricsService.pnlAttribution(userId, home, 30);
        StressTestResultDTO stress = scenarioAnalysisService.runStressTests(userId, home);

        Document document = new Document(PageSize.A4, 40, 40, 48, 40);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            writeHeader(document, home);
            writePortfolioSnapshot(document, stats);
            writeExposureTable(document, stats);
            writeHedgingOverview(document, hedges);
            writeValueAtRisk(document, var);
            writeAttribution(document, attribution);
            writeStressTests(document, stress);

            document.close();
            log.info("Generated executive PDF for user {} (home {})", userId, home);
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to generate executive PDF report", e);
        }
    }

    // --- sections ------------------------------------------------------------

    private void writeHeader(Document document, String home) throws DocumentException {
        Paragraph title = new Paragraph("FX Risk Executive Summary", TITLE);
        document.add(title);
        Paragraph subtitle = new Paragraph(
                "Home currency " + home + "  ·  Generated " + STAMP.format(LocalDateTime.now()), SUBTITLE);
        subtitle.setSpacingAfter(6);
        document.add(subtitle);
        document.add(rule());
    }

    private void writePortfolioSnapshot(Document document, PortfolioStatisticsDTO stats) throws DocumentException {
        section(document, "Portfolio Snapshot");
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        keyValue(table, "Total portfolio value", money(stats.getTotalValueInHome()) + " " + stats.getHomeCurrency());
        keyValue(table, "Currencies held", String.valueOf(stats.getCurrencyCount()));
        if (stats.getUnvaluedCurrencies() != null && !stats.getUnvaluedCurrencies().isEmpty()) {
            keyValue(table, "Unvalued currencies", String.join(", ", stats.getUnvaluedCurrencies()));
        }
        table.setSpacingAfter(12);
        document.add(table);
    }

    private void writeExposureTable(Document document, PortfolioStatisticsDTO stats) throws DocumentException {
        section(document, "Net Exposure by Currency");
        List<CurrencyExposureDTO> exposures = stats.getExposures();
        if (exposures == null || exposures.isEmpty()) {
            emptyNote(document, "No open currency exposure.");
            return;
        }
        PdfPTable table = new PdfPTable(new float[]{2, 3, 3, 2});
        table.setWidthPercentage(100);
        headerRow(table, "Currency", "Net exposure", "Value in " + stats.getHomeCurrency(), "% of book");
        for (CurrencyExposureDTO e : exposures) {
            dataCell(table, e.getCurrency(), Element.ALIGN_LEFT, false);
            dataCell(table, money(e.getNetExposure()), Element.ALIGN_RIGHT, false);
            dataCell(table, money(e.getValueInHome()), Element.ALIGN_RIGHT, false);
            dataCell(table, percent(e.getPercentOfPortfolio()), Element.ALIGN_RIGHT, false);
        }
        table.setSpacingAfter(12);
        document.add(table);
    }

    private void writeHedgingOverview(Document document, List<HedgeDTO> hedges) throws DocumentException {
        section(document, "Hedging Overview");
        if (hedges == null || hedges.isEmpty()) {
            emptyNote(document, "No hedges booked.");
            return;
        }
        int effective = 0;
        BigDecimal totalNotional = BigDecimal.ZERO;
        BigDecimal totalPnl = BigDecimal.ZERO;
        for (HedgeDTO h : hedges) {
            if (Boolean.TRUE.equals(h.getEffective())) {
                effective++;
            }
            if (h.getNotional() != null) {
                totalNotional = totalNotional.add(h.getNotional());
            }
            if (h.getUnrealizedPnl() != null) {
                totalPnl = totalPnl.add(h.getUnrealizedPnl());
            }
        }

        PdfPTable summary = new PdfPTable(2);
        summary.setWidthPercentage(100);
        keyValue(summary, "Hedges booked", hedges.size() + " (" + effective + " effective)");
        keyValue(summary, "Total notional", money(totalNotional));
        keyValue(summary, "Aggregate unrealised P&L", money(totalPnl));
        summary.setSpacingAfter(8);
        document.add(summary);

        PdfPTable table = new PdfPTable(new float[]{3, 2, 3, 3, 2});
        table.setWidthPercentage(100);
        headerRow(table, "Instrument", "Pair", "Notional", "Unrealised P&L", "Effective");
        for (HedgeDTO h : hedges) {
            dataCell(table, hedgeLabel(h), Element.ALIGN_LEFT, false);
            dataCell(table, pair(h), Element.ALIGN_LEFT, false);
            dataCell(table, money(h.getNotional()), Element.ALIGN_RIGHT, false);
            signedCell(table, h.getUnrealizedPnl());
            dataCell(table, effectiveLabel(h), Element.ALIGN_CENTER, false);
        }
        table.setSpacingAfter(12);
        document.add(table);
    }

    private void writeValueAtRisk(Document document, VarResultDTO var) throws DocumentException {
        section(document, "Value at Risk");
        if (var == null || var.getValueAtRisk() == null) {
            emptyNote(document, var != null && var.getMessage() != null
                    ? var.getMessage() : "Insufficient rate history to compute VaR.");
            return;
        }
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        keyValue(table, "Confidence", percent(scaleConfidence(var.getConfidence())));
        keyValue(table, "Value at Risk", money(var.getValueAtRisk()) + " " + var.getHome());
        keyValue(table, "Expected shortfall", money(var.getExpectedShortfall()) + " " + var.getHome());
        keyValue(table, "Worst observed loss", money(var.getWorstLoss()) + " " + var.getHome());
        keyValue(table, "Observations", String.valueOf(var.getObservations()));
        table.setSpacingAfter(12);
        document.add(table);
    }

    private void writeAttribution(Document document, AttributionResultDTO attribution) throws DocumentException {
        section(document, "FX P&L Attribution (last 30 days)");
        if (attribution == null || attribution.getBreakdown() == null || attribution.getBreakdown().isEmpty()) {
            emptyNote(document, "No attributable rate history in the window.");
            return;
        }
        Paragraph total = new Paragraph(
                "Total FX P&L: " + money(attribution.getTotalPnl()) + " " + attribution.getHome(), LABEL);
        total.setSpacingAfter(4);
        document.add(total);

        PdfPTable table = new PdfPTable(new float[]{2, 3, 2, 3});
        table.setWidthPercentage(100);
        headerRow(table, "Currency", "Net exposure", "Rate move", "P&L");
        for (CurrencyAttributionDTO c : attribution.getBreakdown()) {
            dataCell(table, c.getCurrency(), Element.ALIGN_LEFT, false);
            dataCell(table, money(c.getNetExposure()), Element.ALIGN_RIGHT, false);
            dataCell(table, percent(c.getRateChangePercent()), Element.ALIGN_RIGHT, false);
            signedCell(table, c.getPnl());
        }
        table.setSpacingAfter(12);
        document.add(table);
    }

    private void writeStressTests(Document document, StressTestResultDTO stress) throws DocumentException {
        section(document, "Stress-Test Battery");
        if (stress == null || stress.getScenarios() == null || stress.getScenarios().isEmpty()) {
            emptyNote(document, "No stress scenarios available.");
            return;
        }
        Paragraph baseline = new Paragraph(
                "Baseline value: " + money(stress.getBaselineValue()) + " " + stress.getHome(), LABEL);
        baseline.setSpacingAfter(4);
        document.add(baseline);

        PdfPTable table = new PdfPTable(new float[]{5, 3, 3});
        table.setWidthPercentage(100);
        headerRow(table, "Scenario", "Shocked value", "P&L impact");
        for (ScenarioResultDTO s : stress.getScenarios()) {
            dataCell(table, s.getName(), Element.ALIGN_LEFT, false);
            dataCell(table, money(s.getShockedValue()), Element.ALIGN_RIGHT, false);
            signedCell(table, s.getPnl());
        }
        document.add(table);
    }

    // --- rendering helpers ---------------------------------------------------

    private static void section(Document document, String title) throws DocumentException {
        Paragraph p = new Paragraph(title, SECTION);
        p.setSpacingBefore(6);
        p.setSpacingAfter(4);
        document.add(p);
    }

    private static Paragraph rule() {
        Paragraph p = new Paragraph(" ");
        p.setSpacingAfter(2);
        return p;
    }

    private static void emptyNote(Document document, String text) throws DocumentException {
        Paragraph p = new Paragraph(text, FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, Color.GRAY));
        p.setSpacingAfter(12);
        document.add(p);
    }

    private static void keyValue(PdfPTable table, String key, String value) {
        PdfPCell k = new PdfPCell(new Phrase(key, LABEL));
        k.setBorder(0);
        k.setPaddingBottom(3);
        PdfPCell v = new PdfPCell(new Phrase(value, VALUE));
        v.setBorder(0);
        v.setPaddingBottom(3);
        v.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(k);
        table.addCell(v);
    }

    private static void headerRow(PdfPTable table, String... headers) {
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, TH));
            cell.setBackgroundColor(NAVY);
            cell.setPadding(5);
            cell.setBorderColor(Color.WHITE);
            table.addCell(cell);
        }
        table.setHeaderRows(1);
    }

    private static void dataCell(PdfPTable table, String text, int align, boolean bold) {
        PdfPCell cell = new PdfPCell(new Phrase(text == null ? "—" : text, bold ? LABEL : TD));
        cell.setPadding(4);
        cell.setHorizontalAlignment(align);
        cell.setBackgroundColor(table.getRows().size() % 2 == 0 ? LIGHT : Color.WHITE);
        cell.setBorderColor(new Color(0xD0, 0xD7, 0xDE));
        table.addCell(cell);
    }

    private static void signedCell(PdfPTable table, BigDecimal amount) {
        Color color = amount == null ? Color.BLACK
                : amount.signum() < 0 ? NEGATIVE : POSITIVE;
        Font font = FontFactory.getFont(FontFactory.HELVETICA, 9, color);
        PdfPCell cell = new PdfPCell(new Phrase(money(amount), font));
        cell.setPadding(4);
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        cell.setBackgroundColor(table.getRows().size() % 2 == 0 ? LIGHT : Color.WHITE);
        cell.setBorderColor(new Color(0xD0, 0xD7, 0xDE));
        table.addCell(cell);
    }

    // --- formatting ----------------------------------------------------------

    private static String money(BigDecimal value) {
        if (value == null) {
            return "—";
        }
        return String.format("%,.2f", value.setScale(2, RoundingMode.HALF_UP));
    }

    private static String percent(BigDecimal value) {
        if (value == null) {
            return "—";
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    /** VaR confidence arrives as a fraction (e.g. 0.95); render it as a percentage. */
    private static BigDecimal scaleConfidence(BigDecimal confidence) {
        if (confidence == null) {
            return null;
        }
        return confidence.compareTo(BigDecimal.ONE) <= 0
                ? confidence.multiply(BigDecimal.valueOf(100)) : confidence;
    }

    private static String pair(HedgeDTO h) {
        if (h.getBaseCurrency() == null || h.getQuoteCurrency() == null) {
            return "—";
        }
        return h.getBaseCurrency() + "/" + h.getQuoteCurrency();
    }

    private static String hedgeLabel(HedgeDTO h) {
        StringBuilder sb = new StringBuilder();
        if (h.getDirection() != null) {
            sb.append(h.getDirection()).append(' ');
        }
        sb.append(h.getInstrumentType() == null ? "HEDGE" : h.getInstrumentType());
        return sb.toString();
    }

    private static String effectiveLabel(HedgeDTO h) {
        if (h.getEffective() == null) {
            return "—";
        }
        return Boolean.TRUE.equals(h.getEffective()) ? "Yes" : "No";
    }
}

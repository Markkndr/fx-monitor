package com.currencyexchange.service;

import com.currencyexchange.dto.hedges.HedgeDTO;
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
 * Renders a hedge-effectiveness compliance report to a PDF for a single user.
 *
 * <p>The report applies the ASC 815 / IAS 39 <em>dollar-offset</em> effectiveness
 * test: a designated hedge qualifies for hedge accounting when the ratio of the
 * change in the hedge's fair value to the change in the hedged item's value falls
 * within the 80–125% "highly effective" band. Each designated hedge is reported
 * with a PASS/FAIL verdict, and the report carries an overall compliance status.
 * Undesignated (economic) hedges are listed separately as out of scope for the test.
 *
 * <p>Effectiveness figures are computed by {@link HedgeService}; this service only
 * classifies and presents them.
 */
@Service
@Slf4j
public class ComplianceReportService {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** ASC 815 / IAS 39 "highly effective" dollar-offset band (mirrors {@link HedgeService}). */
    private static final BigDecimal EFFECTIVE_LOWER = new BigDecimal("80");
    private static final BigDecimal EFFECTIVE_UPPER = new BigDecimal("125");

    private static final Color NAVY = new Color(0x0B, 0x3D, 0x59);
    private static final Color LIGHT = new Color(0xE8, 0xEE, 0xF3);
    private static final Color PASS_COLOR = new Color(0x1B, 0x7A, 0x3D);
    private static final Color FAIL_COLOR = new Color(0xB0, 0x2A, 0x2A);
    private static final Color NEUTRAL = new Color(0x6B, 0x72, 0x80);

    private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, NAVY);
    private static final Font SUBTITLE = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.DARK_GRAY);
    private static final Font SECTION = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, NAVY);
    private static final Font NOTE = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, Color.GRAY);
    private static final Font TH = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
    private static final Font TD = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);
    private static final Font LABEL = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.BLACK);
    private static final Font VALUE = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);

    /** Per-hedge outcome of the dollar-offset effectiveness test. */
    private enum Verdict {
        PASS("PASS", PASS_COLOR),
        FAIL("FAIL", FAIL_COLOR),
        NOT_ASSESSABLE("N/A", NEUTRAL);

        final String label;
        final Color color;

        Verdict(String label, Color color) {
            this.label = label;
            this.color = color;
        }
    }

    @Autowired
    private HedgeService hedgeService;

    /**
     * Generates the hedge-effectiveness compliance PDF for {@code userId}, valued in
     * {@code homeCurrency}, and returns it as raw PDF bytes.
     */
    public byte[] generateComplianceReport(Long userId, String homeCurrency) {
        String home = (homeCurrency == null || homeCurrency.isBlank())
                ? "USD" : homeCurrency.toUpperCase();

        List<HedgeDTO> hedges = hedgeService.getUserHedges(userId);
        List<HedgeDTO> designated = hedges.stream().filter(this::isDesignated).toList();
        List<HedgeDTO> economic = hedges.stream().filter(h -> !isDesignated(h)).toList();

        Document document = new Document(PageSize.A4, 40, 40, 48, 40);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            writeHeader(document, home);
            writeMethodology(document);
            writeSummary(document, designated);
            writeDesignatedTable(document, designated);
            writeEconomicTable(document, economic);

            document.close();
            log.info("Generated compliance PDF for user {} (home {}): {} designated, {} economic",
                    userId, home, designated.size(), economic.size());
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to generate compliance PDF report", e);
        }
    }

    // --- sections ------------------------------------------------------------

    private void writeHeader(Document document, String home) throws DocumentException {
        document.add(new Paragraph("Hedge Effectiveness Compliance Report", TITLE));
        Paragraph subtitle = new Paragraph(
                "Home currency " + home + "  ·  Generated " + STAMP.format(LocalDateTime.now()), SUBTITLE);
        subtitle.setSpacingAfter(8);
        document.add(subtitle);
    }

    private void writeMethodology(Document document) throws DocumentException {
        section(document, "Assessment Basis");
        Paragraph p = new Paragraph(
                "Effectiveness is assessed under the ASC 815 / IAS 39 dollar-offset method. A designated "
                        + "hedge qualifies for hedge accounting when the ratio of the change in the hedge's fair "
                        + "value to the change in the hedged item's value falls within the 80–125% highly-effective "
                        + "band. Hedges with no offsetting rate movement in the period cannot be assessed and are "
                        + "reported as N/A. Undesignated hedges are economic hedges outside the scope of this test.",
                NOTE);
        p.setSpacingAfter(12);
        document.add(p);
    }

    private void writeSummary(Document document, List<HedgeDTO> designated) throws DocumentException {
        section(document, "Compliance Summary");

        int pass = 0;
        int fail = 0;
        int notAssessable = 0;
        for (HedgeDTO h : designated) {
            switch (verdict(h)) {
                case PASS -> pass++;
                case FAIL -> fail++;
                case NOT_ASSESSABLE -> notAssessable++;
            }
        }

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        keyValue(table, "Designated hedges", String.valueOf(designated.size()));
        keyValue(table, "Assessed", String.valueOf(pass + fail));
        keyValue(table, "Passing (80–125%)", String.valueOf(pass));
        keyValue(table, "Failing", String.valueOf(fail));
        keyValue(table, "Not assessable", String.valueOf(notAssessable));
        table.setSpacingAfter(8);
        document.add(table);

        int assessed = pass + fail;
        String status;
        Color color;
        if (assessed == 0) {
            status = "NO ASSESSABLE HEDGES";
            color = NEUTRAL;
        } else if (fail == 0) {
            status = "COMPLIANT — all assessed hedges highly effective";
            color = PASS_COLOR;
        } else {
            status = "REVIEW REQUIRED — " + fail + " hedge(s) outside the effective band";
            color = FAIL_COLOR;
        }
        Paragraph verdict = new Paragraph("Overall status: " + status,
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, color));
        verdict.setSpacingAfter(14);
        document.add(verdict);
    }

    private void writeDesignatedTable(Document document, List<HedgeDTO> designated) throws DocumentException {
        section(document, "Designated Hedges — Effectiveness Test");
        if (designated.isEmpty()) {
            emptyNote(document, "No designated hedges to assess.");
            return;
        }
        PdfPTable table = new PdfPTable(new float[]{3, 2, 3, 2, 3, 2});
        table.setWidthPercentage(100);
        headerRow(table, "Instrument", "Pair", "Notional", "Hedge ratio", "Effectiveness", "Result");
        for (HedgeDTO h : designated) {
            dataCell(table, hedgeLabel(h), Element.ALIGN_LEFT);
            dataCell(table, pair(h), Element.ALIGN_LEFT);
            dataCell(table, money(h.getNotional()), Element.ALIGN_RIGHT);
            dataCell(table, percent(h.getHedgeRatioPercent()), Element.ALIGN_RIGHT);
            dataCell(table, percent(h.getEffectivenessPercent()), Element.ALIGN_RIGHT);
            verdictCell(table, verdict(h));
        }
        table.setSpacingAfter(12);
        document.add(table);
    }

    private void writeEconomicTable(Document document, List<HedgeDTO> economic) throws DocumentException {
        section(document, "Undesignated (Economic) Hedges");
        if (economic.isEmpty()) {
            emptyNote(document, "None — every hedge is designated to an exposure.");
            return;
        }
        Paragraph note = new Paragraph(
                "Not linked to a hedged item; out of scope for the effectiveness test.", NOTE);
        note.setSpacingAfter(4);
        document.add(note);

        PdfPTable table = new PdfPTable(new float[]{3, 2, 3, 3});
        table.setWidthPercentage(100);
        headerRow(table, "Instrument", "Pair", "Notional", "Status");
        for (HedgeDTO h : economic) {
            dataCell(table, hedgeLabel(h), Element.ALIGN_LEFT);
            dataCell(table, pair(h), Element.ALIGN_LEFT);
            dataCell(table, money(h.getNotional()), Element.ALIGN_RIGHT);
            dataCell(table, h.getStatus() == null ? "—" : h.getStatus(), Element.ALIGN_CENTER);
        }
        document.add(table);
    }

    // --- classification ------------------------------------------------------

    /** A hedge is designated when it is linked to an exposure (has a hedge ratio). */
    private boolean isDesignated(HedgeDTO h) {
        return h.getExposureId() != null;
    }

    private Verdict verdict(HedgeDTO h) {
        BigDecimal eff = h.getEffectivenessPercent();
        if (eff == null) {
            return Verdict.NOT_ASSESSABLE;
        }
        BigDecimal magnitude = eff.abs();
        boolean inBand = magnitude.compareTo(EFFECTIVE_LOWER) >= 0
                && magnitude.compareTo(EFFECTIVE_UPPER) <= 0;
        return inBand ? Verdict.PASS : Verdict.FAIL;
    }

    // --- rendering helpers ---------------------------------------------------

    private static void section(Document document, String title) throws DocumentException {
        Paragraph p = new Paragraph(title, SECTION);
        p.setSpacingBefore(6);
        p.setSpacingAfter(4);
        document.add(p);
    }

    private static void emptyNote(Document document, String text) throws DocumentException {
        Paragraph p = new Paragraph(text, NOTE);
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

    private static void dataCell(PdfPTable table, String text, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text == null ? "—" : text, TD));
        cell.setPadding(4);
        cell.setHorizontalAlignment(align);
        cell.setBackgroundColor(table.getRows().size() % 2 == 0 ? LIGHT : Color.WHITE);
        cell.setBorderColor(new Color(0xD0, 0xD7, 0xDE));
        table.addCell(cell);
    }

    private static void verdictCell(PdfPTable table, Verdict verdict) {
        Font font = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, verdict.color);
        PdfPCell cell = new PdfPCell(new Phrase(verdict.label, font));
        cell.setPadding(4);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
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
}

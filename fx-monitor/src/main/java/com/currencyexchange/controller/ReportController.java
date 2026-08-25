package com.currencyexchange.controller;

import com.currencyexchange.entity.User;
import com.currencyexchange.service.ExcelReportService;
import com.currencyexchange.service.PdfReportService;
import com.currencyexchange.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Report generation endpoints. Exposes a multi-sheet Excel export of the caller's
 * portfolio (summary, exposures, hedges, and rate history) and an executive-summary
 * PDF. Scoped to the authenticated user — a user can only export their own data.
 */
@RestController
@RequestMapping("/api/reports")
@Slf4j
public class ReportController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    @Autowired
    private ExcelReportService excelReportService;

    @Autowired
    private PdfReportService pdfReportService;

    @Autowired
    private UserService userService;

    /** Download the caller's portfolio as an {@code .xlsx} workbook, valued in {@code home}. */
    @GetMapping("/export.xlsx")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(required = false, defaultValue = "USD") String home,
            Authentication authentication) {

        Long userId = currentUserId(authentication);
        log.info("Excel export requested for user {} in {}", userId, home);

        byte[] workbook = excelReportService.generateWorkbook(userId, home);
        String filename = "fx-portfolio-" + LocalDate.now() + ".xlsx";

        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(workbook);
    }

    /** Download the caller's FX risk executive summary as a PDF, valued in {@code home}. */
    @GetMapping("/executive.pdf")
    public ResponseEntity<byte[]> exportExecutivePdf(
            @RequestParam(required = false, defaultValue = "USD") String home,
            Authentication authentication) {

        Long userId = currentUserId(authentication);
        log.info("Executive PDF requested for user {} in {}", userId, home);

        byte[] pdf = pdfReportService.generateExecutiveSummary(userId, home);
        String filename = "fx-executive-summary-" + LocalDate.now() + ".pdf";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(pdf);
    }

    private Long currentUserId(Authentication authentication) {
        User user = userService.getUserByEmail(authentication.getName());
        return user.getId();
    }
}

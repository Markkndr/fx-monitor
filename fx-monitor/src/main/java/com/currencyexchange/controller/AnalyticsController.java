package com.currencyexchange.controller;

import com.currencyexchange.dto.analytics.AttributionResultDTO;
import com.currencyexchange.dto.analytics.ScenarioRequestDTO;
import com.currencyexchange.dto.analytics.ScenarioResultDTO;
import com.currencyexchange.dto.analytics.StressTestResultDTO;
import com.currencyexchange.dto.analytics.VarResultDTO;
import com.currencyexchange.entity.User;
import com.currencyexchange.service.RiskMetricsService;
import com.currencyexchange.service.ScenarioAnalysisService;
import com.currencyexchange.service.UserService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * Advanced analytics endpoints: scenario analysis, stress testing, FX P&amp;L
 * attribution, and Value at Risk. All are scoped to the authenticated user's own
 * exposure.
 */
@RestController
@RequestMapping("/api/analytics")
@Slf4j
public class AnalyticsController {

    @Autowired
    private ScenarioAnalysisService scenarioAnalysisService;

    @Autowired
    private RiskMetricsService riskMetricsService;

    @Autowired
    private UserService userService;

    /** Ad-hoc scenario: revalue the portfolio under a caller-supplied set of rate shocks. */
    @PostMapping("/scenario")
    public ResponseEntity<ScenarioResultDTO> runScenario(
            @Valid @RequestBody ScenarioRequestDTO request,
            Authentication authentication) {

        Long userId = currentUserId(authentication);
        log.info("Scenario analysis requested for user {}", userId);
        return ResponseEntity.ok(
                scenarioAnalysisService.runScenario(userId, request.getHome(), request.getShocks()));
    }

    /** Standard battery of adverse stress scenarios. */
    @GetMapping("/stress")
    public ResponseEntity<StressTestResultDTO> runStressTests(
            @RequestParam(required = false, defaultValue = "USD") String home,
            Authentication authentication) {

        return ResponseEntity.ok(scenarioAnalysisService.runStressTests(currentUserId(authentication), home));
    }

    /** FX P&L attribution over the last {@code lookbackDays} days from stored rate history. */
    @GetMapping("/attribution")
    public ResponseEntity<AttributionResultDTO> attribution(
            @RequestParam(required = false, defaultValue = "USD") String home,
            @RequestParam(required = false, defaultValue = "30") int lookbackDays,
            Authentication authentication) {

        return ResponseEntity.ok(
                riskMetricsService.pnlAttribution(currentUserId(authentication), home, lookbackDays));
    }

    /** Historical-simulation Value at Risk for the portfolio. */
    @GetMapping("/var")
    public ResponseEntity<VarResultDTO> valueAtRisk(
            @RequestParam(required = false, defaultValue = "USD") String home,
            @RequestParam(required = false) BigDecimal confidence,
            @RequestParam(required = false, defaultValue = "365") int lookbackDays,
            Authentication authentication) {

        return ResponseEntity.ok(
                riskMetricsService.valueAtRisk(currentUserId(authentication), home, confidence, lookbackDays));
    }

    private Long currentUserId(Authentication authentication) {
        User user = userService.getUserByEmail(authentication.getName());
        return user.getId();
    }
}

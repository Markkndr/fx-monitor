package com.currencyexchange.controller;

import com.currencyexchange.dto.statistics.PortfolioStatisticsDTO;
import com.currencyexchange.entity.User;
import com.currencyexchange.service.PortfolioStatisticsService;
import com.currencyexchange.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/statistics")
@Slf4j
public class StatisticsController {

    @Autowired
    private PortfolioStatisticsService portfolioStatisticsService;

    @Autowired
    private UserService userService;

    /**
     * Returns net exposure per currency and the total portfolio value for the
     * authenticated user, valued in {@code home} (defaults to EUR). Scoped to the
     * caller's own wallets — a user can never see another user's exposures.
     */
    @GetMapping("/portfolio")
    public ResponseEntity<PortfolioStatisticsDTO> getPortfolioStatistics(
            @RequestParam(required = false, defaultValue = "EUR") String home,
            Authentication authentication) {

        Long userId = currentUserId(authentication);
        log.info("Portfolio statistics requested for user {} in {}", userId, home);
        return ResponseEntity.ok(portfolioStatisticsService.getPortfolioStatistics(userId, home));
    }

    private Long currentUserId(Authentication authentication) {
        User user = userService.getUserByEmail(authentication.getName());
        return user.getId();
    }
}

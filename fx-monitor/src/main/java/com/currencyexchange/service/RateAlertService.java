package com.currencyexchange.service;

import com.currencyexchange.dto.alerts.AlertDTO;
import com.currencyexchange.dto.alerts.CreateAlertRequestDTO;
import com.currencyexchange.entity.RateAlert;
import com.currencyexchange.entity.User;
import com.currencyexchange.exception.AlertNotFoundException;
import com.currencyexchange.exception.InvalidAlertException;
import com.currencyexchange.repository.RateAlertRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * User-scoped CRUD and periodic evaluation of {@link RateAlert}s.
 *
 * <p>Every read/write is bound to the owning user's id. A scheduled sweep
 * ({@link #evaluateActiveAlerts()}) re-prices all active alerts against the latest
 * quotes from {@link ExchangeRateService} and fires any whose threshold has been
 * crossed. Rates are quoted as units of {@code quote} per 1 unit of {@code base}.
 */
@Service
@Slf4j
public class RateAlertService {

    private static final Set<String> VALID_DIRECTIONS =
            Set.of(RateAlert.DIRECTION_ABOVE, RateAlert.DIRECTION_BELOW);

    @Autowired
    private RateAlertRepository rateAlertRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private ExchangeRateService exchangeRateService;

    @Transactional(readOnly = true)
    public List<AlertDTO> getUserAlerts(Long userId) {
        return rateAlertRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AlertDTO getUserAlert(Long userId, Long alertId) {
        return toDTO(requireOwnedAlert(userId, alertId));
    }

    @Transactional
    public AlertDTO createAlert(Long userId, CreateAlertRequestDTO request) {
        User user = userService.getUserById(userId);

        RateAlert alert = new RateAlert();
        alert.setUser(user);
        alert.setBase(request.getBase().trim().toUpperCase());
        alert.setQuote(request.getQuote().trim().toUpperCase());
        alert.setDirection(normaliseDirection(request.getDirection()));
        alert.setThreshold(request.getThreshold());
        alert.setStatus(RateAlert.STATUS_ACTIVE);
        alert.setNote(blankToNull(request.getNote()));

        if (alert.getBase().equals(alert.getQuote())) {
            throw new InvalidAlertException("Base and quote currency must differ");
        }

        alert = rateAlertRepository.save(alert);
        log.info("Rate alert {} created for user {}: {}/{} {} {}", alert.getId(), userId,
                alert.getBase(), alert.getQuote(), alert.getDirection(), alert.getThreshold());
        return toDTO(alert);
    }

    /**
     * Re-arms a triggered alert so it can fire again, clearing the previous trigger.
     */
    @Transactional
    public AlertDTO rearmAlert(Long userId, Long alertId) {
        RateAlert alert = requireOwnedAlert(userId, alertId);
        alert.setStatus(RateAlert.STATUS_ACTIVE);
        alert.setTriggeredAt(null);
        alert.setTriggeredRate(null);
        alert = rateAlertRepository.save(alert);
        return toDTO(alert);
    }

    @Transactional
    public void deleteAlert(Long userId, Long alertId) {
        RateAlert alert = requireOwnedAlert(userId, alertId);
        rateAlertRepository.delete(alert);
        log.info("Rate alert {} deleted for user {}", alertId, userId);
    }

    /**
     * Periodically evaluates every active alert. Rates are fetched once per base
     * currency and reused across that base's alerts to avoid redundant provider
     * calls. A breached alert flips to {@code TRIGGERED}; the rest just record the
     * latest observed rate. Provider failures are logged and swallowed so the
     * scheduler keeps running.
     */
    @Scheduled(fixedRateString = "#{${forex.alert.interval:900} * 1000}")
    @Transactional
    public void evaluateActiveAlerts() {
        List<RateAlert> active = rateAlertRepository.findByStatus(RateAlert.STATUS_ACTIVE);
        if (active.isEmpty()) {
            return;
        }

        Map<String, Map<String, BigDecimal>> ratesByBase = new HashMap<>();
        int fired = 0;

        for (RateAlert alert : active) {
            BigDecimal rate = lookupRate(ratesByBase, alert.getBase(), alert.getQuote());
            if (rate == null) {
                continue;
            }
            alert.setLastCheckedRate(rate);
            if (alert.isBreachedBy(rate)) {
                alert.setStatus(RateAlert.STATUS_TRIGGERED);
                alert.setTriggeredRate(rate);
                alert.setTriggeredAt(LocalDateTime.now());
                fired++;
                log.info("Rate alert {} TRIGGERED: {}/{} {} {} (rate {})", alert.getId(),
                        alert.getBase(), alert.getQuote(), alert.getDirection(),
                        alert.getThreshold(), rate);
            }
        }

        rateAlertRepository.saveAll(active);
        if (fired > 0) {
            log.info("Rate alert sweep fired {} of {} active alerts", fired, active.size());
        }
    }

    /**
     * Fetches (and memoises) the {@code quote}-per-{@code base} rate for this sweep.
     * A failed provider lookup is cached as an empty map so we don't retry it for
     * every alert on the same base.
     */
    private BigDecimal lookupRate(Map<String, Map<String, BigDecimal>> cache, String base, String quote) {
        Map<String, BigDecimal> rates = cache.get(base);
        if (rates == null) {
            try {
                rates = exchangeRateService.getRates(base).getRates();
            } catch (Exception e) {
                log.warn("Alert sweep could not fetch rates for base {}: {}", base, e.getMessage());
                rates = Map.of();
            }
            cache.put(base, rates);
        }
        return rates.get(quote);
    }

    private RateAlert requireOwnedAlert(Long userId, Long alertId) {
        return rateAlertRepository.findById(alertId)
                .filter(a -> a.getUser() != null && userId.equals(a.getUser().getId()))
                .orElseThrow(() -> new AlertNotFoundException("Alert not found with ID: " + alertId));
    }

    private String normaliseDirection(String direction) {
        String upper = direction.trim().toUpperCase();
        if (!VALID_DIRECTIONS.contains(upper)) {
            throw new InvalidAlertException("Unknown alert direction: " + direction);
        }
        return upper;
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private AlertDTO toDTO(RateAlert a) {
        return AlertDTO.builder()
                .id(a.getId())
                .userId(a.getUser() != null ? a.getUser().getId() : null)
                .base(a.getBase())
                .quote(a.getQuote())
                .direction(a.getDirection())
                .threshold(a.getThreshold())
                .status(a.getStatus())
                .lastCheckedRate(a.getLastCheckedRate())
                .triggeredRate(a.getTriggeredRate())
                .triggeredAt(a.getTriggeredAt())
                .note(a.getNote())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }
}

package com.currencyexchange.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A user-defined threshold on an FX pair. When the live rate of {@code quote} per
 * 1 unit of {@code base} crosses {@link #threshold} in the configured
 * {@link #direction}, the alert fires (status flips to {@code TRIGGERED} and
 * {@link #triggeredAt} is stamped).
 *
 * <p>Evaluation is done periodically by {@code RateAlertService} against the latest
 * quotes from {@code ExchangeRateService}; the alert only ever fires once until it
 * is re-armed.
 */
@Entity
@Table(name = "rate_alerts", indexes = {
        @Index(name = "idx_rate_alert_user", columnList = "user_id"),
        @Index(name = "idx_rate_alert_active", columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RateAlert {

    /** Fire when the rate rises to or above the threshold. */
    public static final String DIRECTION_ABOVE = "ABOVE";
    /** Fire when the rate falls to or below the threshold. */
    public static final String DIRECTION_BELOW = "BELOW";

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_TRIGGERED = "TRIGGERED";
    public static final String STATUS_DISABLED = "DISABLED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String base;

    @Column(nullable = false)
    private String quote;

    @Column(nullable = false)
    private String direction; // ABOVE, BELOW

    @Column(precision = 19, scale = 6, nullable = false)
    private BigDecimal threshold;

    @Column(nullable = false)
    private String status = STATUS_ACTIVE;

    /** The rate observed the last time this alert was evaluated. */
    @Column(precision = 19, scale = 6)
    private BigDecimal lastCheckedRate;

    /** The rate that caused the alert to fire. */
    @Column(precision = 19, scale = 6)
    private BigDecimal triggeredRate;

    private LocalDateTime triggeredAt;

    private String note;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Whether the given live rate satisfies this alert's trigger condition.
     */
    public boolean isBreachedBy(BigDecimal rate) {
        if (rate == null) {
            return false;
        }
        return DIRECTION_ABOVE.equalsIgnoreCase(direction)
                ? rate.compareTo(threshold) >= 0
                : rate.compareTo(threshold) <= 0;
    }
}

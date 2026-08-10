package com.currencyexchange.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A single historical FX rate observation: {@code rate} units of {@code quote}
 * per 1 unit of {@code base}, captured at {@code capturedAt}.
 *
 * <p>These accumulate over time to give the rate history behind trend charts,
 * volatility, and P&amp;L attribution — the live {@code ExchangeRateService} only
 * ever holds the latest quote.
 */
@Entity
@Table(name = "rate_snapshots", indexes = {
        @Index(name = "idx_rate_snapshot_pair", columnList = "base,quote,capturedAt")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RateSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String base;

    @Column(nullable = false)
    private String quote;

    @Column(precision = 19, scale = 6, nullable = false)
    private BigDecimal rate;

    @Column(nullable = false)
    private LocalDateTime capturedAt;
}

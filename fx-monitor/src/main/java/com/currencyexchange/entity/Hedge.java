package com.currencyexchange.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A hedging instrument that offsets FX risk on a position.
 *
 * <p>Supported instruments are a {@code FORWARD} (an agreement to exchange
 * {@link #notional} units of {@link #baseCurrency} at {@link #contractRate} on
 * {@link #maturityDate}) and an {@code OPTION} (a right, not obligation, to do so at
 * the strike held in {@link #contractRate}, bought for {@link #premium}). The
 * {@link #direction} says whether the desk is buying or selling the base currency.
 *
 * <p>A hedge may be linked to a single {@link Exposure} it is intended to cover;
 * the mark-to-market and effectiveness maths live in {@code HedgeService}. Rates are
 * quoted as units of {@code quoteCurrency} per 1 unit of {@code baseCurrency}.
 */
@Entity
@Table(name = "hedges", indexes = {
        @Index(name = "idx_hedge_user", columnList = "user_id"),
        @Index(name = "idx_hedge_exposure", columnList = "exposure_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Hedge {

    public static final String INSTRUMENT_FORWARD = "FORWARD";
    public static final String INSTRUMENT_OPTION = "OPTION";

    public static final String OPTION_CALL = "CALL";
    public static final String OPTION_PUT = "PUT";

    /** Desk buys the base currency (hedges a base-currency payable / short position). */
    public static final String DIRECTION_BUY = "BUY";
    /** Desk sells the base currency (hedges a base-currency receivable / long position). */
    public static final String DIRECTION_SELL = "SELL";

    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_CLOSED = "CLOSED";
    public static final String STATUS_EXPIRED = "EXPIRED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** The position this hedge is intended to cover. Optional (a macro hedge may stand alone). */
    @ManyToOne
    @JoinColumn(name = "exposure_id")
    private Exposure exposure;

    @Column(nullable = false)
    private String instrumentType; // FORWARD, OPTION

    /** CALL or PUT for an option; null for a forward. */
    private String optionType;

    @Column(nullable = false)
    private String direction; // BUY, SELL

    @Column(name = "base_currency", nullable = false)
    private String baseCurrency;

    @Column(name = "quote_currency", nullable = false)
    private String quoteCurrency;

    /** Contract size in base currency; always positive. */
    @Column(precision = 19, scale = 2, nullable = false)
    private BigDecimal notional;

    /** Forward rate or option strike, quoted as quote per base. */
    @Column(precision = 19, scale = 6, nullable = false)
    private BigDecimal contractRate;

    /** Total option premium paid, in quote currency. Null/zero for a forward. */
    @Column(precision = 19, scale = 2)
    private BigDecimal premium;

    private LocalDate tradeDate;
    private LocalDate maturityDate;

    @Column(nullable = false)
    private String status = STATUS_OPEN;

    private String description;

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

    public boolean isForward() {
        return INSTRUMENT_FORWARD.equalsIgnoreCase(instrumentType);
    }

    public boolean isOption() {
        return INSTRUMENT_OPTION.equalsIgnoreCase(instrumentType);
    }

    /** +1 when the desk is long the base currency (BUY), -1 when short (SELL). */
    @Transient
    public int directionSign() {
        return DIRECTION_BUY.equalsIgnoreCase(direction) ? 1 : -1;
    }
}

package com.currencyexchange.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A single foreign-currency position that carries FX risk.
 *
 * <p>Unlike a {@link Wallet} (money actually held), an exposure represents any
 * source of currency risk — an unpaid receivable, an outstanding payable, a cash
 * balance, an intercompany balance, a forecast flow, or a subsidiary translation.
 * The {@link #amount} is always stored as a positive number; the {@link #type}
 * determines whether the position is long (an asset such as a receivable) or short
 * (a liability such as a payable). Netting logic lives in the statistics service.
 */
@Entity
@Table(name = "exposures")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Exposure {

    // Exposure types. Everything except PAYABLE is a long (asset) position.
    public static final String TYPE_RECEIVABLE = "RECEIVABLE";
    public static final String TYPE_PAYABLE = "PAYABLE";
    public static final String TYPE_CASH = "CASH";
    public static final String TYPE_INTERCOMPANY = "INTERCOMPANY";
    public static final String TYPE_FORECAST = "FORECAST";
    public static final String TYPE_TRANSLATION = "TRANSLATION";

    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_SETTLED = "SETTLED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String type; // RECEIVABLE, PAYABLE, CASH, INTERCOMPANY, FORECAST, TRANSLATION

    @Column(nullable = false)
    private String currency;

    /** Always positive; direction (long/short) is derived from {@link #type}. */
    @Column(precision = 19, scale = 2, nullable = false)
    private BigDecimal amount;

    /** Customer, supplier, or bank the position sits against. */
    private String counterparty;

    /** Legal entity / subsidiary the position belongs to. */
    @Column(name = "entity_name")
    private String entityName;

    /** When the position was booked. */
    private LocalDate valueDate;

    /** When the position settles (drives ageing, close prep, hedge maturities). */
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

    /**
     * The signed contribution of this position to a net currency exposure:
     * positive for asset-side types, negative for a payable.
     */
    @Transient
    public BigDecimal getSignedAmount() {
        BigDecimal value = amount != null ? amount : BigDecimal.ZERO;
        return TYPE_PAYABLE.equalsIgnoreCase(type) ? value.negate() : value;
    }
}

package com.currencyexchange.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Historical-simulation Value at Risk for the portfolio, expressed in the home
 * currency. {@link #valueAtRisk} is the loss not expected to be exceeded at
 * {@link #confidence} over the snapshot sampling interval; {@link #expectedShortfall}
 * is the average loss in the tail beyond it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VarResultDTO {
    private String home;
    private BigDecimal confidence;
    private int observations;
    private BigDecimal portfolioValue;
    private BigDecimal valueAtRisk;
    private BigDecimal expectedShortfall;
    private BigDecimal worstLoss;
    private String message;
}

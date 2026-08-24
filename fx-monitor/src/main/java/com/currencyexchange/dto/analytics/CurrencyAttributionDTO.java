package com.currencyexchange.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** How much of the period's FX P&L a single currency was responsible for. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrencyAttributionDTO {
    private String currency;
    private BigDecimal netExposure;
    private BigDecimal startRate;
    private BigDecimal endRate;
    private BigDecimal rateChangePercent;
    private BigDecimal pnl;
}

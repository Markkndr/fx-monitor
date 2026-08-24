package com.currencyexchange.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Per-currency contribution to a scenario or stress result, valued in the home currency. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrencyImpactDTO {
    private String currency;
    private BigDecimal netExposure;
    private BigDecimal appliedShockPercent;
    private BigDecimal baselineValue;
    private BigDecimal shockedValue;
    private BigDecimal pnl;
}

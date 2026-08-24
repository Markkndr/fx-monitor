package com.currencyexchange.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * The result of revaluing the portfolio under one set of rate shocks. Reused for
 * both ad-hoc scenarios and the named predefined stress tests ({@link #name}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScenarioResultDTO {
    private String name;
    private String home;
    private BigDecimal baselineValue;
    private BigDecimal shockedValue;
    private BigDecimal pnl;
    private List<CurrencyImpactDTO> impacts;
}

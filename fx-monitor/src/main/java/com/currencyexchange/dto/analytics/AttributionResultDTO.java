package com.currencyexchange.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * FX P&amp;L attribution over a lookback window: the total home-currency P&amp;L
 * driven by rate moves, broken down by the currency that caused it. Computed from
 * stored {@code RateSnapshot} history, so it only covers pairs with snapshots.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttributionResultDTO {
    private String home;
    private LocalDateTime from;
    private LocalDateTime to;
    private BigDecimal totalPnl;
    private List<CurrencyAttributionDTO> breakdown;
    private List<String> unattributedCurrencies;
}

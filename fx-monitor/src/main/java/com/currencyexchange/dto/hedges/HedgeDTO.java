package com.currencyexchange.dto.hedges;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HedgeDTO {
    private Long id;
    private Long userId;
    private Long exposureId;
    private String instrumentType;
    private String optionType;
    private String direction;
    private String baseCurrency;
    private String quoteCurrency;
    private BigDecimal notional;
    private BigDecimal contractRate;
    private BigDecimal premium;
    private LocalDate tradeDate;
    private LocalDate maturityDate;
    private String status;
    private String description;

    // --- Valuation, filled in against the latest spot when the hedge is priced ---

    /** Latest spot rate (quote per base) used to value the hedge. */
    private BigDecimal spotRate;
    /** Current mark-to-market of the instrument, in quote currency. */
    private BigDecimal markToMarket;
    /** MTM net of premium paid, in quote currency (equals MTM for a forward). */
    private BigDecimal unrealizedPnl;
    /** Notional as a percentage of the linked exposure's amount (hedge ratio). */
    private BigDecimal hedgeRatioPercent;
    /** Dollar-offset hedge effectiveness against the linked exposure, as a percentage. */
    private BigDecimal effectivenessPercent;
    /** Whether effectiveness falls in the 80–125% accounting-qualifying band. */
    private Boolean effective;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

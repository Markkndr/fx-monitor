package com.currencyexchange.dto.hedges;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateHedgeRequestDTO {

    @NotBlank(message = "Instrument type is required")
    private String instrumentType; // FORWARD, OPTION

    /** Required only for an OPTION: CALL or PUT. */
    private String optionType;

    @NotBlank(message = "Direction is required")
    private String direction; // BUY, SELL

    @NotBlank(message = "Base currency is required")
    private String baseCurrency;

    @NotBlank(message = "Quote currency is required")
    private String quoteCurrency;

    @NotNull(message = "Notional is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Notional must be greater than zero")
    private BigDecimal notional;

    @NotNull(message = "Contract rate is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Contract rate must be greater than zero")
    private BigDecimal contractRate;

    /** Total premium paid, in quote currency (options only). */
    private BigDecimal premium;

    /** Optional id of an exposure this hedge covers (must belong to the caller). */
    private Long exposureId;

    private LocalDate tradeDate;
    private LocalDate maturityDate;
    private String description;
}

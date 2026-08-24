package com.currencyexchange.dto.analytics;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * A single rate shock: the percentage change applied to {@code currency}'s value
 * against the home currency (e.g. {@code -5} means the currency loses 5%).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RateShockDTO {

    @NotBlank(message = "Currency is required")
    private String currency;

    @NotNull(message = "Change percent is required")
    private BigDecimal changePercent;
}

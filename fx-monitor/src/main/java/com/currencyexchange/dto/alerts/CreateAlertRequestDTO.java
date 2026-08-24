package com.currencyexchange.dto.alerts;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateAlertRequestDTO {

    @NotBlank(message = "Base currency is required")
    private String base;

    @NotBlank(message = "Quote currency is required")
    private String quote;

    @NotBlank(message = "Direction is required")
    private String direction; // ABOVE, BELOW

    @NotNull(message = "Threshold is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Threshold must be greater than zero")
    private BigDecimal threshold;

    private String note;
}

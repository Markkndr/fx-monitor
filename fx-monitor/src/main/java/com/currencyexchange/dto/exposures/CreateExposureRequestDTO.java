package com.currencyexchange.dto.exposures;

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
public class CreateExposureRequestDTO {

    @NotBlank(message = "Exposure type is required")
    private String type; // RECEIVABLE, PAYABLE, CASH, INTERCOMPANY, FORECAST, TRANSLATION

    @NotBlank(message = "Currency is required")
    private String currency;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Amount must be greater than zero")
    private BigDecimal amount;

    private String counterparty;
    private String entityName;
    private LocalDate valueDate;
    private LocalDate maturityDate;
    private String description;
}

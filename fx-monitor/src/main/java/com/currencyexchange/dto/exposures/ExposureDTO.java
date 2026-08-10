package com.currencyexchange.dto.exposures;

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
public class ExposureDTO {
    private Long id;
    private Long userId;
    private String type;
    private String currency;
    private BigDecimal amount;
    /** Signed by type: positive for asset positions, negative for a payable. */
    private BigDecimal signedAmount;
    private String counterparty;
    private String entityName;
    private LocalDate valueDate;
    private LocalDate maturityDate;
    private String status;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

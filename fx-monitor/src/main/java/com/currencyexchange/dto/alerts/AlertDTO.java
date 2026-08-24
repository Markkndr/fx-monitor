package com.currencyexchange.dto.alerts;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertDTO {
    private Long id;
    private Long userId;
    private String base;
    private String quote;
    private String direction;
    private BigDecimal threshold;
    private String status;
    private BigDecimal lastCheckedRate;
    private BigDecimal triggeredRate;
    private LocalDateTime triggeredAt;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

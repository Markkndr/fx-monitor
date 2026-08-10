package com.currencyexchange.dto.exchange;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A time-ordered series of stored rate observations for one currency pair
 * ({@code rate} units of {@code quote} per 1 unit of {@code base}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateHistoryDTO {
    private String base;
    private String quote;
    private List<Point> points;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Point {
        private LocalDateTime capturedAt;
        private BigDecimal rate;
    }
}

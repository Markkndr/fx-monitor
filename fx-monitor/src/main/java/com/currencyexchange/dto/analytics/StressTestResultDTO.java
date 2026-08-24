package com.currencyexchange.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** A battery of predefined stress scenarios run against the current portfolio. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StressTestResultDTO {
    private String home;
    private java.math.BigDecimal baselineValue;
    private List<ScenarioResultDTO> scenarios;
}

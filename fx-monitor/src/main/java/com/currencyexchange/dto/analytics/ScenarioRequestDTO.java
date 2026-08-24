package com.currencyexchange.dto.analytics;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScenarioRequestDTO {

    /** Home currency the impact is expressed in; defaults to USD when blank. */
    private String home;

    @NotEmpty(message = "At least one rate shock is required")
    @Valid
    private List<RateShockDTO> shocks;
}

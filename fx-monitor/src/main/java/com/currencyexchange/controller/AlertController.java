package com.currencyexchange.controller;

import com.currencyexchange.dto.alerts.AlertDTO;
import com.currencyexchange.dto.alerts.CreateAlertRequestDTO;
import com.currencyexchange.entity.User;
import com.currencyexchange.service.RateAlertService;
import com.currencyexchange.service.UserService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
@Slf4j
public class AlertController {

    @Autowired
    private RateAlertService rateAlertService;

    @Autowired
    private UserService userService;

    @GetMapping
    public ResponseEntity<List<AlertDTO>> getAlerts(Authentication authentication) {
        return ResponseEntity.ok(rateAlertService.getUserAlerts(currentUserId(authentication)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AlertDTO> getAlert(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(rateAlertService.getUserAlert(currentUserId(authentication), id));
    }

    @PostMapping
    public ResponseEntity<AlertDTO> createAlert(
            @Valid @RequestBody CreateAlertRequestDTO request,
            Authentication authentication) {

        Long userId = currentUserId(authentication);
        log.info("Create rate alert request for user: {}", userId);
        AlertDTO created = rateAlertService.createAlert(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/{id}/rearm")
    public ResponseEntity<AlertDTO> rearmAlert(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(rateAlertService.rearmAlert(currentUserId(authentication), id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAlert(@PathVariable Long id, Authentication authentication) {
        rateAlertService.deleteAlert(currentUserId(authentication), id);
        return ResponseEntity.noContent().build();
    }

    private Long currentUserId(Authentication authentication) {
        User user = userService.getUserByEmail(authentication.getName());
        return user.getId();
    }
}

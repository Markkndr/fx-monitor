package com.currencyexchange.controller;

import com.currencyexchange.dto.hedges.CreateHedgeRequestDTO;
import com.currencyexchange.dto.hedges.HedgeDTO;
import com.currencyexchange.entity.User;
import com.currencyexchange.service.HedgeService;
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
@RequestMapping("/api/hedges")
@Slf4j
public class HedgeController {

    @Autowired
    private HedgeService hedgeService;

    @Autowired
    private UserService userService;

    @GetMapping
    public ResponseEntity<List<HedgeDTO>> getHedges(
            @RequestParam(required = false) Long exposureId,
            Authentication authentication) {

        Long userId = currentUserId(authentication);
        List<HedgeDTO> hedges = exposureId != null
                ? hedgeService.getHedgesForExposure(userId, exposureId)
                : hedgeService.getUserHedges(userId);
        return ResponseEntity.ok(hedges);
    }

    @GetMapping("/{id}")
    public ResponseEntity<HedgeDTO> getHedge(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(hedgeService.getUserHedge(currentUserId(authentication), id));
    }

    @PostMapping
    public ResponseEntity<HedgeDTO> createHedge(
            @Valid @RequestBody CreateHedgeRequestDTO request,
            Authentication authentication) {

        Long userId = currentUserId(authentication);
        log.info("Create hedge request for user: {}", userId);
        HedgeDTO created = hedgeService.createHedge(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<HedgeDTO> updateStatus(
            @PathVariable Long id,
            @RequestParam String status,
            Authentication authentication) {

        return ResponseEntity.ok(hedgeService.updateStatus(currentUserId(authentication), id, status));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteHedge(@PathVariable Long id, Authentication authentication) {
        hedgeService.deleteHedge(currentUserId(authentication), id);
        return ResponseEntity.noContent().build();
    }

    private Long currentUserId(Authentication authentication) {
        User user = userService.getUserByEmail(authentication.getName());
        return user.getId();
    }
}

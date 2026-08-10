package com.currencyexchange.controller;

import com.currencyexchange.dto.exposures.CreateExposureRequestDTO;
import com.currencyexchange.dto.exposures.ExposureDTO;
import com.currencyexchange.entity.User;
import com.currencyexchange.service.ExposureService;
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
@RequestMapping("/api/exposures")
@Slf4j
public class ExposureController {

    @Autowired
    private ExposureService exposureService;

    @Autowired
    private UserService userService;

    @GetMapping
    public ResponseEntity<List<ExposureDTO>> getExposures(
            @RequestParam(required = false) String type,
            Authentication authentication) {

        Long userId = currentUserId(authentication);
        List<ExposureDTO> exposures = (type != null && !type.isBlank())
                ? exposureService.getUserExposuresByType(userId, type)
                : exposureService.getUserExposures(userId);

        return ResponseEntity.ok(exposures);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExposureDTO> getExposure(
            @PathVariable Long id,
            Authentication authentication) {

        return ResponseEntity.ok(exposureService.getUserExposure(currentUserId(authentication), id));
    }

    @PostMapping
    public ResponseEntity<ExposureDTO> createExposure(
            @Valid @RequestBody CreateExposureRequestDTO request,
            Authentication authentication) {

        Long userId = currentUserId(authentication);
        log.info("Create exposure request for user: {}", userId);
        ExposureDTO created = exposureService.createExposure(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ExposureDTO> updateStatus(
            @PathVariable Long id,
            @RequestParam String status,
            Authentication authentication) {

        Long userId = currentUserId(authentication);
        return ResponseEntity.ok(exposureService.updateStatus(userId, id, status));
    }

    /**
     * Resolves the authenticated principal (an email) to the owning user's id.
     * Every endpoint scopes its work to this id so a user can only ever see or
     * change their own exposures.
     */
    private Long currentUserId(Authentication authentication) {
        User user = userService.getUserByEmail(authentication.getName());
        return user.getId();
    }
}

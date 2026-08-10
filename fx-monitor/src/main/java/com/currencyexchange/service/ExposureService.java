package com.currencyexchange.service;

import com.currencyexchange.dto.exposures.CreateExposureRequestDTO;
import com.currencyexchange.dto.exposures.ExposureDTO;
import com.currencyexchange.entity.Exposure;
import com.currencyexchange.entity.User;
import com.currencyexchange.exception.ExposureNotFoundException;
import com.currencyexchange.exception.InvalidExposureException;
import com.currencyexchange.repository.ExposureRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * User-scoped CRUD for FX exposures. Every read and write is bound to the owning
 * user's id so a caller can only ever see or change their own positions.
 */
@Service
@Slf4j
public class ExposureService {

    private static final Set<String> VALID_TYPES = Set.of(
            Exposure.TYPE_RECEIVABLE, Exposure.TYPE_PAYABLE, Exposure.TYPE_CASH,
            Exposure.TYPE_INTERCOMPANY, Exposure.TYPE_FORECAST, Exposure.TYPE_TRANSLATION);

    private static final Set<String> VALID_STATUSES = Set.of(
            Exposure.STATUS_OPEN, Exposure.STATUS_SETTLED, Exposure.STATUS_CANCELLED);

    @Autowired
    private ExposureRepository exposureRepository;

    @Autowired
    private UserService userService;

    @Transactional(readOnly = true)
    public List<ExposureDTO> getUserExposures(Long userId) {
        return exposureRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ExposureDTO> getUserExposuresByType(Long userId, String type) {
        return exposureRepository.findByUserIdAndTypeOrderByCreatedAtDesc(userId, type.toUpperCase())
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Fetches a single exposure, enforcing that it belongs to the requesting user.
     * An exposure owned by someone else is reported as not found so we never leak
     * the existence of other users' positions.
     */
    @Transactional(readOnly = true)
    public ExposureDTO getUserExposure(Long userId, Long exposureId) {
        return toDTO(requireOwnedExposure(userId, exposureId));
    }

    @Transactional
    public ExposureDTO createExposure(Long userId, CreateExposureRequestDTO request) {
        User user = userService.getUserById(userId);
        String type = normaliseType(request.getType());

        Exposure exposure = new Exposure();
        exposure.setUser(user);
        exposure.setType(type);
        exposure.setCurrency(request.getCurrency().trim().toUpperCase());
        exposure.setAmount(request.getAmount());
        exposure.setCounterparty(blankToNull(request.getCounterparty()));
        exposure.setEntityName(blankToNull(request.getEntityName()));
        exposure.setValueDate(request.getValueDate());
        exposure.setMaturityDate(request.getMaturityDate());
        exposure.setStatus(Exposure.STATUS_OPEN);
        exposure.setDescription(blankToNull(request.getDescription()));

        exposure = exposureRepository.save(exposure);
        log.info("Exposure {} ({} {} {}) created for user {}",
                exposure.getId(), type, exposure.getAmount(), exposure.getCurrency(), userId);
        return toDTO(exposure);
    }

    /**
     * Updates the lifecycle status of an owned exposure (e.g. settling a
     * receivable or cancelling a forecast).
     */
    @Transactional
    public ExposureDTO updateStatus(Long userId, Long exposureId, String status) {
        Exposure exposure = requireOwnedExposure(userId, exposureId);
        exposure.setStatus(normaliseStatus(status));
        exposure = exposureRepository.save(exposure);
        log.info("Exposure {} status set to {} for user {}", exposureId, exposure.getStatus(), userId);
        return toDTO(exposure);
    }

    private Exposure requireOwnedExposure(Long userId, Long exposureId) {
        return exposureRepository.findById(exposureId)
                .filter(e -> e.getUser() != null && userId.equals(e.getUser().getId()))
                .orElseThrow(() -> new ExposureNotFoundException(
                        "Exposure not found with ID: " + exposureId));
    }

    private String normaliseType(String type) {
        String upper = type.trim().toUpperCase();
        if (!VALID_TYPES.contains(upper)) {
            throw new InvalidExposureException("Unknown exposure type: " + type);
        }
        return upper;
    }

    private String normaliseStatus(String status) {
        if (status == null) {
            throw new InvalidExposureException("Status is required");
        }
        String upper = status.trim().toUpperCase();
        if (!VALID_STATUSES.contains(upper)) {
            throw new InvalidExposureException("Unknown exposure status: " + status);
        }
        return upper;
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private ExposureDTO toDTO(Exposure e) {
        return ExposureDTO.builder()
                .id(e.getId())
                .userId(e.getUser() != null ? e.getUser().getId() : null)
                .type(e.getType())
                .currency(e.getCurrency())
                .amount(e.getAmount())
                .signedAmount(e.getSignedAmount())
                .counterparty(e.getCounterparty())
                .entityName(e.getEntityName())
                .valueDate(e.getValueDate())
                .maturityDate(e.getMaturityDate())
                .status(e.getStatus())
                .description(e.getDescription())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}

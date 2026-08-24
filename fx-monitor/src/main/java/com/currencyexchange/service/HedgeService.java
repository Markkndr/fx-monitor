package com.currencyexchange.service;

import com.currencyexchange.dto.hedges.CreateHedgeRequestDTO;
import com.currencyexchange.dto.hedges.HedgeDTO;
import com.currencyexchange.entity.Exposure;
import com.currencyexchange.entity.Hedge;
import com.currencyexchange.entity.User;
import com.currencyexchange.exception.ExposureNotFoundException;
import com.currencyexchange.exception.HedgeNotFoundException;
import com.currencyexchange.exception.InvalidHedgeException;
import com.currencyexchange.repository.ExposureRepository;
import com.currencyexchange.repository.HedgeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * User-scoped CRUD plus valuation of {@link Hedge} instruments.
 *
 * <p>Beyond storage, this service is the hedging engine: it marks each instrument
 * to market against the latest spot, computes the unrealised P&amp;L, and — for a
 * hedge linked to an {@link Exposure} — the hedge ratio and a dollar-offset
 * effectiveness measure used for hedge-accounting qualification. Rates are quoted
 * as units of {@code quoteCurrency} per 1 unit of {@code baseCurrency}.
 */
@Service
@Slf4j
public class HedgeService {

    private static final int MONEY_SCALE = 2;
    private static final int PERCENT_SCALE = 2;

    /** IAS 39 / ASC 815 "highly effective" band for the dollar-offset ratio. */
    private static final BigDecimal EFFECTIVE_LOWER = new BigDecimal("80");
    private static final BigDecimal EFFECTIVE_UPPER = new BigDecimal("125");

    private static final Set<String> VALID_INSTRUMENTS =
            Set.of(Hedge.INSTRUMENT_FORWARD, Hedge.INSTRUMENT_OPTION);
    private static final Set<String> VALID_OPTION_TYPES =
            Set.of(Hedge.OPTION_CALL, Hedge.OPTION_PUT);
    private static final Set<String> VALID_DIRECTIONS =
            Set.of(Hedge.DIRECTION_BUY, Hedge.DIRECTION_SELL);
    private static final Set<String> VALID_STATUSES =
            Set.of(Hedge.STATUS_OPEN, Hedge.STATUS_CLOSED, Hedge.STATUS_EXPIRED);

    @Autowired
    private HedgeRepository hedgeRepository;

    @Autowired
    private ExposureRepository exposureRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private ExchangeRateService exchangeRateService;

    @Transactional(readOnly = true)
    public List<HedgeDTO> getUserHedges(Long userId) {
        Map<String, Map<String, BigDecimal>> rateCache = new HashMap<>();
        return hedgeRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(h -> toValuedDTO(h, rateCache))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public HedgeDTO getUserHedge(Long userId, Long hedgeId) {
        return toValuedDTO(requireOwnedHedge(userId, hedgeId), new HashMap<>());
    }

    @Transactional(readOnly = true)
    public List<HedgeDTO> getHedgesForExposure(Long userId, Long exposureId) {
        requireOwnedExposure(userId, exposureId);
        Map<String, Map<String, BigDecimal>> rateCache = new HashMap<>();
        return hedgeRepository.findByExposureId(exposureId)
                .stream()
                .map(h -> toValuedDTO(h, rateCache))
                .collect(Collectors.toList());
    }

    @Transactional
    public HedgeDTO createHedge(Long userId, CreateHedgeRequestDTO request) {
        User user = userService.getUserById(userId);

        Hedge hedge = new Hedge();
        hedge.setUser(user);
        hedge.setInstrumentType(normalise(request.getInstrumentType(), VALID_INSTRUMENTS, "instrument type"));
        hedge.setDirection(normalise(request.getDirection(), VALID_DIRECTIONS, "direction"));
        hedge.setBaseCurrency(request.getBaseCurrency().trim().toUpperCase());
        hedge.setQuoteCurrency(request.getQuoteCurrency().trim().toUpperCase());
        hedge.setNotional(request.getNotional());
        hedge.setContractRate(request.getContractRate());
        hedge.setPremium(request.getPremium());
        hedge.setTradeDate(request.getTradeDate());
        hedge.setMaturityDate(request.getMaturityDate());
        hedge.setStatus(Hedge.STATUS_OPEN);
        hedge.setDescription(blankToNull(request.getDescription()));

        if (hedge.getBaseCurrency().equals(hedge.getQuoteCurrency())) {
            throw new InvalidHedgeException("Base and quote currency must differ");
        }

        if (hedge.isOption()) {
            if (request.getOptionType() == null || request.getOptionType().isBlank()) {
                throw new InvalidHedgeException("Option type (CALL/PUT) is required for an option");
            }
            hedge.setOptionType(normalise(request.getOptionType(), VALID_OPTION_TYPES, "option type"));
        } else {
            hedge.setOptionType(null);
        }

        if (request.getExposureId() != null) {
            Exposure exposure = requireOwnedExposure(userId, request.getExposureId());
            hedge.setExposure(exposure);
        }

        hedge = hedgeRepository.save(hedge);
        log.info("Hedge {} ({} {} {} {} @ {}) created for user {}", hedge.getId(),
                hedge.getInstrumentType(), hedge.getDirection(), hedge.getNotional(),
                hedge.getBaseCurrency() + "/" + hedge.getQuoteCurrency(), hedge.getContractRate(), userId);
        return toValuedDTO(hedge, new HashMap<>());
    }

    @Transactional
    public HedgeDTO updateStatus(Long userId, Long hedgeId, String status) {
        Hedge hedge = requireOwnedHedge(userId, hedgeId);
        hedge.setStatus(normalise(status, VALID_STATUSES, "status"));
        hedge = hedgeRepository.save(hedge);
        log.info("Hedge {} status set to {} for user {}", hedgeId, hedge.getStatus(), userId);
        return toValuedDTO(hedge, new HashMap<>());
    }

    @Transactional
    public void deleteHedge(Long userId, Long hedgeId) {
        Hedge hedge = requireOwnedHedge(userId, hedgeId);
        hedgeRepository.delete(hedge);
        log.info("Hedge {} deleted for user {}", hedgeId, userId);
    }

    // --- Valuation ------------------------------------------------------------

    /**
     * Marks a forward or option to market against the given spot.
     *
     * <p>For a forward the P&amp;L is {@code directionSign * notional * (spot - contractRate)}:
     * a SELL gains when the base weakens, a BUY when it strengthens. For an option the
     * mark is its intrinsic value — {@code max(spot - strike, 0)} for a CALL,
     * {@code max(strike - spot, 0)} for a PUT — scaled by the notional; the P&amp;L is
     * that intrinsic value net of the premium paid.
     *
     * @return the mark-to-market in quote currency, or {@code null} if the spot is unknown.
     */
    BigDecimal markToMarket(Hedge hedge, BigDecimal spot) {
        if (spot == null || spot.signum() <= 0) {
            return null;
        }
        if (hedge.isForward()) {
            return hedge.getNotional()
                    .multiply(spot.subtract(hedge.getContractRate()))
                    .multiply(BigDecimal.valueOf(hedge.directionSign()))
                    .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        // Option: intrinsic value only (a pragmatic proxy without a full pricing model).
        BigDecimal intrinsicPerUnit = Hedge.OPTION_CALL.equalsIgnoreCase(hedge.getOptionType())
                ? spot.subtract(hedge.getContractRate())
                : hedge.getContractRate().subtract(spot);
        intrinsicPerUnit = intrinsicPerUnit.max(BigDecimal.ZERO);
        return hedge.getNotional().multiply(intrinsicPerUnit).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private HedgeDTO toValuedDTO(Hedge hedge, Map<String, Map<String, BigDecimal>> rateCache) {
        HedgeDTO dto = toDTO(hedge);

        BigDecimal spot = lookupRate(rateCache, hedge.getBaseCurrency(), hedge.getQuoteCurrency());
        BigDecimal mtm = markToMarket(hedge, spot);
        dto.setSpotRate(spot);
        dto.setMarkToMarket(mtm);

        if (mtm != null) {
            BigDecimal premium = hedge.getPremium() != null ? hedge.getPremium() : BigDecimal.ZERO;
            dto.setUnrealizedPnl(hedge.isOption() ? mtm.subtract(premium).setScale(MONEY_SCALE, RoundingMode.HALF_UP) : mtm);
        }

        Exposure exposure = hedge.getExposure();
        if (exposure != null && exposure.getAmount() != null && exposure.getAmount().signum() > 0) {
            dto.setHedgeRatioPercent(hedge.getNotional()
                    .multiply(BigDecimal.valueOf(100))
                    .divide(exposure.getAmount(), PERCENT_SCALE, RoundingMode.HALF_UP));
            applyEffectiveness(dto, hedge, exposure, spot, mtm);
        }
        return dto;
    }

    /**
     * Dollar-offset effectiveness: the ratio of the hedge's value change to the
     * hedged item's value change for the observed move from the contract rate to the
     * current spot. A qualifying hedge lands in the 80–125% band. Only meaningful when
     * the hedge and its exposure share the base currency and the market has actually
     * moved off the contract rate.
     */
    private void applyEffectiveness(HedgeDTO dto, Hedge hedge, Exposure exposure,
                                    BigDecimal spot, BigDecimal mtm) {
        if (spot == null || mtm == null) {
            return;
        }
        if (!exposure.getCurrency().equalsIgnoreCase(hedge.getBaseCurrency())) {
            return; // effectiveness maths need a common base currency
        }
        BigDecimal rateMove = spot.subtract(hedge.getContractRate());
        if (rateMove.signum() == 0) {
            return; // no move yet — effectiveness is undefined
        }
        // Change in the hedged item's value for the same move (signed by exposure type).
        BigDecimal hedgedItemChange = exposure.getSignedAmount().multiply(rateMove);
        if (hedgedItemChange.signum() == 0) {
            return;
        }
        BigDecimal effectiveness = mtm.negate()
                .multiply(BigDecimal.valueOf(100))
                .divide(hedgedItemChange, PERCENT_SCALE, RoundingMode.HALF_UP);
        dto.setEffectivenessPercent(effectiveness);
        BigDecimal magnitude = effectiveness.abs();
        dto.setEffective(magnitude.compareTo(EFFECTIVE_LOWER) >= 0
                && magnitude.compareTo(EFFECTIVE_UPPER) <= 0);
    }

    private BigDecimal lookupRate(Map<String, Map<String, BigDecimal>> cache, String base, String quote) {
        Map<String, BigDecimal> rates = cache.get(base);
        if (rates == null) {
            try {
                rates = exchangeRateService.getRates(base).getRates();
            } catch (Exception e) {
                log.warn("Hedge valuation could not fetch rates for base {}: {}", base, e.getMessage());
                rates = Map.of();
            }
            cache.put(base, rates);
        }
        return rates.get(quote);
    }

    private Hedge requireOwnedHedge(Long userId, Long hedgeId) {
        return hedgeRepository.findById(hedgeId)
                .filter(h -> h.getUser() != null && userId.equals(h.getUser().getId()))
                .orElseThrow(() -> new HedgeNotFoundException("Hedge not found with ID: " + hedgeId));
    }

    private Exposure requireOwnedExposure(Long userId, Long exposureId) {
        return exposureRepository.findById(exposureId)
                .filter(e -> e.getUser() != null && userId.equals(e.getUser().getId()))
                .orElseThrow(() -> new ExposureNotFoundException("Exposure not found with ID: " + exposureId));
    }

    private String normalise(String value, Set<String> allowed, String label) {
        if (value == null || value.isBlank()) {
            throw new InvalidHedgeException(capitalise(label) + " is required");
        }
        String upper = value.trim().toUpperCase();
        if (!allowed.contains(upper)) {
            throw new InvalidHedgeException("Unknown " + label + ": " + value);
        }
        return upper;
    }

    private String capitalise(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private HedgeDTO toDTO(Hedge h) {
        return HedgeDTO.builder()
                .id(h.getId())
                .userId(h.getUser() != null ? h.getUser().getId() : null)
                .exposureId(h.getExposure() != null ? h.getExposure().getId() : null)
                .instrumentType(h.getInstrumentType())
                .optionType(h.getOptionType())
                .direction(h.getDirection())
                .baseCurrency(h.getBaseCurrency())
                .quoteCurrency(h.getQuoteCurrency())
                .notional(h.getNotional())
                .contractRate(h.getContractRate())
                .premium(h.getPremium())
                .tradeDate(h.getTradeDate())
                .maturityDate(h.getMaturityDate())
                .status(h.getStatus())
                .description(h.getDescription())
                .createdAt(h.getCreatedAt())
                .updatedAt(h.getUpdatedAt())
                .build();
    }
}

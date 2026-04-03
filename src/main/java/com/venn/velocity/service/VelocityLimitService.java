package com.venn.velocity.service;

import com.venn.velocity.entity.LoadRecord;
import com.venn.velocity.model.LoadAttempt;
import com.venn.velocity.model.LoadResponse;
import com.venn.velocity.repository.LoadRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.Optional;

@Service
public class VelocityLimitService {

    private static final Logger log = LoggerFactory.getLogger(VelocityLimitService.class);

    static final BigDecimal MAX_DAILY_AMOUNT = new BigDecimal("5000.00");
    static final BigDecimal MAX_WEEKLY_AMOUNT = new BigDecimal("20000.00");
    static final int MAX_DAILY_LOADS = 3;

    private final LoadRecordRepository repository;

    public VelocityLimitService(LoadRecordRepository repository) {
        this.repository = repository;
    }

    /**
     * Validate and execute fund load against velocity limits.
     *
     * @return the load response, or empty if this load ID was already seen for this customer
     */
    @Transactional
    public Optional<LoadResponse> ValidateAndExecute(LoadAttempt attempt) {
        if (repository.existsByLoadIdAndCustomerId(attempt.getId(), attempt.getCustomerId())) {
            log.debug("Duplicate load id={} for customer={}, ignoring", attempt.getId(), attempt.getCustomerId());
            return Optional.empty();
        }

        BigDecimal amount = parseAmount(attempt.getLoadAmount());
        Instant loadTime = Instant.parse(attempt.getTime());
        // All time window calculations are done in UTC
        LocalDate date = loadTime.atZone(ZoneOffset.UTC).toLocalDate();

        // Define day window
        Instant dayStart = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        // Define week window
        LocalDate weekStartDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Instant weekStart = weekStartDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant weekEnd = weekStartDate.plusWeeks(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        boolean accepted = checkLimits(attempt.getCustomerId(), amount, dayStart, dayEnd, weekStart, weekEnd);

        repository.save(new LoadRecord(attempt.getId(), attempt.getCustomerId(), amount, loadTime, accepted));

        log.info("Load id={} customer={} amount={} accepted={}", attempt.getId(), attempt.getCustomerId(), amount, accepted);
        return Optional.of(new LoadResponse(attempt.getId(), attempt.getCustomerId(), accepted));
    }

    private boolean checkLimits(String customerId, BigDecimal amount,
                                 Instant dayStart, Instant dayEnd,
                                 Instant weekStart, Instant weekEnd) {
        // Check: max 3 loads per day
        long dailyCount = repository.countAcceptedInDay(customerId, dayStart, dayEnd);
        if (dailyCount >= MAX_DAILY_LOADS) {
            log.debug("customer={} rejected: daily load count {} >= {}", customerId, dailyCount, MAX_DAILY_LOADS);
            return false;
        }

        // Check: max $5,000 per day
        BigDecimal dailyTotal = repository.sumAcceptedAmountInDay(customerId, dayStart, dayEnd);
        if (dailyTotal.add(amount).compareTo(MAX_DAILY_AMOUNT) > 0) {
            log.debug("customer={} rejected: daily amount {} + {} > {}", customerId, dailyTotal, amount, MAX_DAILY_AMOUNT);
            return false;
        }

        // Check: max $20,000 per week
        BigDecimal weeklyTotal = repository.sumAcceptedAmountInWeek(customerId, weekStart, weekEnd);
        if (weeklyTotal.add(amount).compareTo(MAX_WEEKLY_AMOUNT) > 0) {
            log.debug("customer={} rejected: weekly amount {} + {} > {}", customerId, weeklyTotal, amount, MAX_WEEKLY_AMOUNT);
            return false;
        }

        return true;
    }

    private BigDecimal parseAmount(String loadAmount) {
        if (loadAmount == null || loadAmount.isBlank()) {
            throw new IllegalArgumentException("load_amount must not be blank");
        }
        // Parse and normalize currency values.
        String cleaned = loadAmount.startsWith("$") ? loadAmount.substring(1) : loadAmount;
        return new BigDecimal(cleaned);
    }
}

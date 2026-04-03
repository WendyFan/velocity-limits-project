package com.venn.velocity.repository;

import com.venn.velocity.entity.LoadRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;

public interface LoadRecordRepository extends JpaRepository<LoadRecord, Long> {

    // Duplicate check: Returns true if a load with the same ID already exists for this customer
    boolean existsByLoadIdAndCustomerId(String loadId, String customerId);

    // Counts accepted loads within a UTC day window to check max count every day
    @Query("""
            SELECT COUNT(r) FROM LoadRecord r
            WHERE r.customerId = :customerId
              AND r.accepted = true
              AND r.loadTime >= :dayStart
              AND r.loadTime < :dayEnd
            """)
    long countAcceptedInDay(
            @Param("customerId") String customerId,
            @Param("dayStart") Instant dayStart,
            @Param("dayEnd") Instant dayEnd);

    // Sums accepted load amounts within a UTC day window to check max amount limit
    // COALESCE returns 0 when there are no records, avoiding a null result
    @Query("""
            SELECT COALESCE(SUM(r.amount), 0) FROM LoadRecord r
            WHERE r.customerId = :customerId
              AND r.accepted = true
              AND r.loadTime >= :dayStart
              AND r.loadTime < :dayEnd
            """)
    BigDecimal sumAcceptedAmountInDay(
            @Param("customerId") String customerId,
            @Param("dayStart") Instant dayStart,
            @Param("dayEnd") Instant dayEnd);

    // Sums accepted load amounts within a UTC week window (Mon–Sun) to check weekly limit
    // COALESCE returns 0 when there are no records, avoiding a null result
    @Query("""
            SELECT COALESCE(SUM(r.amount), 0) FROM LoadRecord r
            WHERE r.customerId = :customerId
              AND r.accepted = true
              AND r.loadTime >= :weekStart
              AND r.loadTime < :weekEnd
            """)
    BigDecimal sumAcceptedAmountInWeek(
            @Param("customerId") String customerId,
            @Param("weekStart") Instant weekStart,
            @Param("weekEnd") Instant weekEnd);
}

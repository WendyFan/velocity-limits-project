package com.venn.velocity.repository;

import com.venn.velocity.entity.LoadRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;

public interface LoadRecordRepository extends JpaRepository<LoadRecord, Long> {

    boolean existsByLoadIdAndCustomerId(String loadId, String customerId);

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

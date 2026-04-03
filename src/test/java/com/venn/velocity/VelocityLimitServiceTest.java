package com.venn.velocity;

import com.venn.velocity.model.LoadAttempt;
import com.venn.velocity.model.LoadResponse;
import com.venn.velocity.repository.LoadRecordRepository;
import com.venn.velocity.service.VelocityLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class VelocityLimitServiceTest {

    @Autowired
    private VelocityLimitService service;

    @Autowired
    private LoadRecordRepository repository;

    @BeforeEach
    void setUp() {
        // Clear DB between tests so each test starts with a clean state
        repository.deleteAll();
    }

    // Daily load count limit : max 3

    @Test
    void acceptsUpToThreeLoadsPerDay() {
        assertAccepted(load("1", "cust1", "$100.00", "2000-01-01T00:00:00Z"));
        assertAccepted(load("2", "cust1", "$100.00", "2000-01-01T01:00:00Z"));
        assertAccepted(load("3", "cust1", "$100.00", "2000-01-01T02:00:00Z"));
    }

    @Test
    void rejectsFourthLoadOnSameDay() {
        assertAccepted(load("1", "cust1", "$100.00", "2000-01-01T00:00:00Z"));
        assertAccepted(load("2", "cust1", "$100.00", "2000-01-01T01:00:00Z"));
        assertAccepted(load("3", "cust1", "$100.00", "2000-01-01T02:00:00Z"));
        assertRejected(load("4", "cust1", "$1.00", "2000-01-01T03:00:00Z"));
    }

    @Test
    void dailyCountResetsAtMidnightUtc() {
        assertAccepted(load("1", "cust1", "$100.00", "2000-01-01T00:00:00Z"));
        assertAccepted(load("2", "cust1", "$100.00", "2000-01-01T01:00:00Z"));
        assertAccepted(load("3", "cust1", "$100.00", "2000-01-01T02:00:00Z"));
        // Next day
        assertAccepted(load("4", "cust1", "$100.00", "2000-01-02T00:00:00Z"));
    }

    //Daily amount limit ($5,000)

    @Test
    void rejectsLoadThatExceedsDailyAmountLimit() {
        assertAccepted(load("1", "cust2", "$3000.00", "2000-01-01T00:00:00Z"));
        // $3000 + $2000.01 = $5000.01 — one cent over the daily limit
        assertRejected(load("2", "cust2", "$2000.01", "2000-01-01T01:00:00Z"));
    }

    @Test
    void acceptsLoadThatHitsExactDailyLimit() {
        assertAccepted(load("1", "cust2", "$3000.00", "2000-01-01T00:00:00Z"));
        // $3000 + $2000 = exactly $5000 — boundary must be inclusive
        assertAccepted(load("2", "cust2", "$2000.00", "2000-01-01T01:00:00Z"));
    }

    @Test
    void dailyAmountResetsAtMidnightUtc() {
        assertAccepted(load("1", "cust2", "$4999.00", "2000-01-01T23:59:59Z"));
        assertAccepted(load("2", "cust2", "$4999.00", "2000-01-02T00:00:00Z"));
    }

    // Weekly amount limit ($20,000)

    @Test
    void rejectsLoadThatExceedsWeeklyAmountLimit() {
        //Rejected on day 5 since over $20k
        assertAccepted(load("1", "cust3", "$5000.00", "2000-01-03T00:00:00Z"));
        assertAccepted(load("2", "cust3", "$5000.00", "2000-01-04T00:00:00Z"));
        assertAccepted(load("3", "cust3", "$5000.00", "2000-01-05T00:00:00Z"));
        assertAccepted(load("4", "cust3", "$5000.00", "2000-01-06T00:00:00Z"));
        assertRejected(load("5", "cust3", "$0.01", "2000-01-07T00:00:00Z"));
    }

    @Test
    void weeklyLimitResetsOnMonday() {
        //day 1
        assertAccepted(load("1", "cust3", "$5000.00", "2000-01-03T00:00:00Z"));
        //day 2
        assertAccepted(load("2", "cust3", "$5000.00", "2000-01-04T00:00:00Z"));
        //day 3
        assertAccepted(load("3", "cust3", "$5000.00", "2000-01-05T00:00:00Z"));
        //day 4
        assertAccepted(load("4", "cust3", "$5000.00", "2000-01-06T00:00:00Z"));
        //day 5 and day 6 no record
        //day 7, last day of a week
        assertRejected(load("5", "cust3", "$0.01", "2000-01-09T00:00:00Z"));

        //new week start
        assertAccepted(load("6", "cust3", "$5000.00", "2000-01-10T00:00:00Z"));
    }

    // Duplicate load ID
    @Test
    void ignoresDuplicateLoadIdForSameCustomer() {
        Optional<LoadResponse> first = service.ValidateAndExecute(load("dup1", "cust4", "$100.00", "2000-01-01T00:00:00Z"));
        Optional<LoadResponse> duplicate = service.ValidateAndExecute(load("dup1", "cust4", "$100.00", "2000-01-01T01:00:00Z"));

        assertThat(first).isPresent();
        // Duplicate returns empty
        assertThat(duplicate).isEmpty();
    }

    @Test
    void sameLoadIdAllowedForDifferentCustomers() {
        // Load ID uniqueness is scoped per customer, not globally
        Optional<LoadResponse> r1 = service.ValidateAndExecute(load("sharedId", "custA", "$100.00", "2000-01-01T00:00:00Z"));
        Optional<LoadResponse> r2 = service.ValidateAndExecute(load("sharedId", "custB", "$100.00", "2000-01-01T01:00:00Z"));
        Optional<LoadResponse> r3 = service.ValidateAndExecute(load("sharedId", "custB", "$100.00", "2000-01-01T02:00:00Z"));

        assertThat(r1).isPresent();
        assertThat(r2).isPresent();
        assertThat(r3).isEmpty();
    }

    // Rejected loads don't count toward limits
    @Test
    void rejectedLoadsDoNotCountTowardDailyTotal() {
        // accept: under $5000
        assertAccepted(load("1", "cust5", "$4999.00", "2000-01-01T00:00:00Z"));
        // rejected: exceed $5000
        assertRejected(load("2", "cust5", "$2.00", "2000-01-01T01:00:00Z"));
        // accept: still under $5000
        assertAccepted(load("3", "cust5", "$1.00", "2000-01-01T02:00:00Z"));
    }

    @Test
    void rejectedLoadsDoNotCountTowardDailyCount() {
        // accept: count=1, total=$5000
        assertAccepted(load("1", "cust6", "$5000.00", "2000-01-01T00:00:00Z"));
        // rejected by $ limit
        assertRejected(load("2", "cust6", "$0.01", "2000-01-01T01:00:00Z"));
        // accept: count=2, total=$5000
        assertAccepted(load("3", "cust6", "$0.00", "2000-01-01T02:00:00Z"));
        // accept: count=3, total=$5000
        assertAccepted(load("4", "cust6", "$0.00", "2000-01-01T03:00:00Z"));
        // rejected by count limit
        assertRejected(load("5", "cust6", "$0.00", "2000-01-01T04:00:00Z"));
    }

    //Helper functions for tests
    private LoadAttempt load(String id, String customerId, String amount, String time) {
        LoadAttempt a = new LoadAttempt();
        a.setId(id);
        a.setCustomerId(customerId);
        a.setLoadAmount(amount);
        a.setTime(time);
        return a;
    }

    private void assertAccepted(LoadAttempt attempt) {
        Optional<LoadResponse> r = service.ValidateAndExecute(attempt);
        assertThat(r).isPresent();
        assertThat(r.get().isAccepted())
                .as("Expected load id=%s to be accepted", attempt.getId())
                .isTrue();
    }

    private void assertRejected(LoadAttempt attempt) {
        Optional<LoadResponse> r = service.ValidateAndExecute(attempt);
        assertThat(r).isPresent();
        assertThat(r.get().isAccepted())
                .as("Expected load id=%s to be rejected", attempt.getId())
                .isFalse();
    }
}

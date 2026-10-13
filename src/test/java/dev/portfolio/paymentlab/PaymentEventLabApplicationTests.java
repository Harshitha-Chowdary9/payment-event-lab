package dev.portfolio.paymentlab;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import dev.portfolio.paymentlab.PaymentEventLabApplication.*;

class PaymentEventLabApplicationTests {
    static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
    Ledger ledger() { return new Ledger(Clock.fixed(NOW, ZoneOffset.UTC)); }
    PaymentRequest request(long amount) { return new PaymentRequest("demo-1", amount, "EUR", "DE"); }
    @Test void exactReplayDoesNotCreateSecondEvent() {
        Ledger l=ledger(); Result a=l.accept("key-1", request(500)); Result b=l.accept("key-1",request(500));
        assertFalse(a.replayed()); assertTrue(b.replayed()); assertEquals(a.event(), b.event());
        assertEquals(1,l.all(null).size());
    }
    @Test void changedPayloadConflicts() {
        Ledger l=ledger(); l.accept("key",request(500));
        assertThrows(PaymentEventLabApplication.ApiError.class, () -> l.accept("key",request(501)));
    }
    @Test void amountThresholdUsesMinorUnits() {
        Ledger l=ledger(); assertTrue(l.accept("a",request(99999)).event().flags().isEmpty());
        assertEquals(List.of("HIGH_AMOUNT"),l.accept("b",request(100000)).event().flags());
    }
    @Test void thirdEventIsFlaggedAndReplayDoesNotCountAgain() {
        Ledger l=ledger(); l.accept("a",request(100));l.accept("a",request(100));l.accept("b",request(100));
        assertEquals(List.of("RAPID_SEQUENCE"),l.accept("c",request(100)).event().flags());
    }
    @Test void accountFilterAndMissingId() {
        Ledger l=ledger();l.accept("a",request(100));
        assertEquals(0,l.all("other").size());
        assertThrows(PaymentEventLabApplication.ApiError.class,()->l.get(UUID.randomUUID()));
    }
    @Test void invalidKeyRejected() {
        for(String key:Arrays.asList(null,"","contains spaces"))
            assertThrows(PaymentEventLabApplication.ApiError.class,()->ledger().accept(key,request(100)));
    }
    @Test void concurrentRetriesAreAtomic() throws Exception {
        Ledger l=ledger(); ExecutorService pool=Executors.newFixedThreadPool(8);
        try {
            List<Callable<Result>> calls=new ArrayList<>();
            for(int i=0;i<30;i++)calls.add(()->l.accept("shared",request(500)));
            List<Future<Result>> results=pool.invokeAll(calls);
            long fresh=0; for(Future<Result> result:results)if(!result.get().replayed())fresh++;
            assertEquals(1,fresh);assertEquals(1,l.all(null).size());
        } finally { pool.shutdownNow(); }
    }
    @Test void expiredEventsDoNotTriggerVelocity() {
        class MutableClock extends Clock {
            Instant value=NOW;
            public ZoneId getZone(){return ZoneOffset.UTC;}
            public Clock withZone(ZoneId zone){return this;}
            public Instant instant(){return value;}
        }
        MutableClock clock=new MutableClock();Ledger l=new Ledger(clock);
        l.accept("a",request(100));l.accept("b",request(100));clock.value=NOW.plusSeconds(61);
        assertTrue(l.accept("c",request(100)).event().flags().isEmpty());
    }

    @Test void asynchronousAuditProcessesAcceptedEventsOnce() throws Exception {
        try(Ledger l=ledger()) {
            Result result=l.accept("a",request(100000));l.accept("a",request(100000));
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
            while(l.audits().isEmpty() && System.nanoTime()<deadline)Thread.sleep(5);
            assertEquals(1,l.audits().size());
            assertEquals(result.event().id(),l.audits().getFirst().eventId());
            assertEquals("REVIEW",l.audits().getFirst().decision());
        }
    }
}

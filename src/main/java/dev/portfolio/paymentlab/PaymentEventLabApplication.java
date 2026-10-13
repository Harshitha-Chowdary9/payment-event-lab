package dev.portfolio.paymentlab;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;

/** Fictional payment events only. No payment provider or money movement. */
@SpringBootApplication
@RestController
public class PaymentEventLabApplication {
    private final Ledger ledger = new Ledger(Clock.systemUTC());

    public static void main(String[] args) {
        SpringApplication.run(PaymentEventLabApplication.class, args);
    }

    public record PaymentRequest(
        @NotBlank @Pattern(regexp="[a-zA-Z0-9_-]{1,40}") String accountId,
        @Min(1) @Max(100000000) long amountMinor,
        @NotNull @Pattern(regexp="EUR") String currency,
        @NotNull @Pattern(regexp="DE|FR|NL|IN|US") String country) {}
    public record Event(UUID id, String accountId, long amountMinor, String currency,
                        String country, Instant receivedAt, List<String> flags) {}
    public record Result(Event event, boolean replayed) {}
    record Saved(PaymentRequest request, Event event) {}
    public record Audit(UUID eventId, Instant processedAt, String decision) {}

    // One lock makes request-key checks and velocity calculations atomic in one process.
    public static final class Ledger implements AutoCloseable {
        private final Clock clock;
        private final BlockingQueue<Event> queue = new LinkedBlockingQueue<>(10000);
        private final List<Audit> audit = new CopyOnWriteArrayList<>();
        private final Thread worker;
        private final Map<String, Saved> byKey = new HashMap<>();
        private final List<Event> events = new ArrayList<>();
        public Ledger(Clock clock) {
            this.clock = clock;
            worker = Thread.ofPlatform().daemon().name("demo-audit-consumer").start(() -> {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        Event event = queue.take();
                        audit.add(new Audit(event.id(), clock.instant(), event.flags().isEmpty() ? "NO_RULE_MATCH" : "REVIEW"));
                    }
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            });
        }
        public List<Audit> audits() { return List.copyOf(audit); }
        public void close() { worker.interrupt(); }

        public synchronized Result accept(String key, PaymentRequest request) {
            if (key == null || !key.matches("[a-zA-Z0-9_-]{1,80}"))
                throw new ApiError(HttpStatus.BAD_REQUEST, "Idempotency-Key must be 1-80 letters, digits, hyphens or underscores");
            Saved existing = byKey.get(key);
            if (existing != null) {
                if (!existing.request().equals(request))
                    throw new ApiError(HttpStatus.CONFLICT, "Key already used with a different request");
                return new Result(existing.event(), true);
            }
            if (events.size() >= 10000)
                throw new ApiError(HttpStatus.SERVICE_UNAVAILABLE, "Demo capacity reached; restart to reset");
            Instant now = clock.instant();
            long recent = events.stream().filter(e -> e.accountId().equals(request.accountId()))
                .filter(e -> !e.receivedAt().isBefore(now.minusSeconds(60)) && !e.receivedAt().isAfter(now)).count();
            List<String> flags = new ArrayList<>();
            if (request.amountMinor() >= 100000) flags.add("HIGH_AMOUNT");
            if (recent >= 2) flags.add("RAPID_SEQUENCE");
            Event event = new Event(UUID.randomUUID(), request.accountId(), request.amountMinor(),
                request.currency(), request.country(), now, List.copyOf(flags));
            events.add(event); byKey.put(key, new Saved(request, event));
            queue.add(event);
            return new Result(event, false);
        }
        public synchronized List<Event> all(String accountId) {
            return events.stream().filter(e -> accountId == null || e.accountId().equals(accountId)).toList();
        }
        public synchronized Event get(UUID id) {
            return events.stream().filter(e -> e.id().equals(id)).findFirst()
                .orElseThrow(() -> new ApiError(HttpStatus.NOT_FOUND, "Event not found"));
        }
    }
    static final class ApiError extends RuntimeException {
        final HttpStatus status;
        ApiError(HttpStatus status, String message) { super(message); this.status = status; }
    }
    @PostMapping("/api/events")
    public ResponseEntity<Result> create(@RequestHeader(value="Idempotency-Key", required=false) String key,
                                          @Valid @RequestBody PaymentRequest request) {
        Result result = ledger.accept(key, request);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(result);
    }
    @GetMapping("/api/events")
    public List<Event> list(@RequestParam(required=false) String accountId) { return ledger.all(accountId); }
    @GetMapping("/api/events/{id}")
    public Event get(@PathVariable UUID id) { return ledger.get(id); }
    @GetMapping("/api/audit")
    public List<Audit> audit() { return ledger.audits(); }
    @GetMapping("/api/health")
    public Map<String,String> health() { return Map.of("status", "ok", "mode", "fictional-events-only"); }

    @ExceptionHandler(ApiError.class)
    public ResponseEntity<Map<String,String>> error(ApiError e) {
        return ResponseEntity.status(e.status).body(Map.of("error", e.getMessage()));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String,String>> invalid(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("error", "Invalid request; see README for accepted fields"));
    }
}

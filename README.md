# Payment Event Lab

A small Java 21 / Spring Boot 4.1.1 backend learning project for fictional payment events.
It explores a useful fintech engineering problem: safe retries plus asynchronous audit processing.
**No money moves. No bank integration, real customer data, ML fraud model or financial advice.**

## What works

- REST endpoints create, list and retrieve events; JSON and field validation.
- `Idempotency-Key`: exact retry returns the same event; changed payload returns HTTP 409.
- EUR amounts in integer minor units, avoiding floating-point money errors.
- Explainable demo flags: HIGH_AMOUNT (at least EUR 1,000); RAPID_SEQUENCE (third event for one account within 60 seconds).
- A bounded in-process queue and background consumer asynchronously record audit decisions.
- Atomic key/velocity checks under a lock; concurrent retry test.
- Nine JUnit tests and 14 real HTTP smoke checks passed locally on 2026-10-13.
- Dockerfile provided for a local container. **Container build/run not verified** (Docker unavailable during validation).

This is one service with an event consumer, not a distributed microservices deployment.
There is no AWS, Terraform, Kotlin, Kafka or RabbitMQ implementation here.

## Run locally

Requires a JDK 21+, Python 3 for the HTTP smoke script, and internet for Maven dependencies.
The Maven wrapper came from Spring Initializr. Use `sh mvnw` so execute permissions are not needed.

```bash
sh mvnw test
sh mvnw package
python3 scripts/smoke.py
java -jar target/payment-event-lab-0.0.1-SNAPSHOT.jar
```

Server binds to `127.0.0.1:8080`. If busy, append `--server.port=8081`.
The smoke script picks a free port, launches the JAR and shuts it down.

```bash
curl -i http://localhost:8080/api/events \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: demo-001' \
  -d '{"accountId":"demo-1","amountMinor":150000,"currency":"EUR","country":"DE"}'
curl http://localhost:8080/api/events
curl http://localhost:8080/api/audit
```

Repeat the first request: 201 becomes 200 with `replayed: true`, not a new event.
Change amount with the same key: 409. Audit may briefly be empty while the consumer works.

| Endpoint | Meaning |
| --- | --- |
| POST /api/events | Create or replay, 201/200; 400 invalid input; 409 conflicting key |
| GET /api/events?accountId=demo-1 | List events, optional account filter |
| GET /api/events/{id} | Retrieve event; 404 missing; 400 malformed UUID |
| GET /api/audit | Eventually consistent audit records |
| GET /api/health | Process health, not a durable queue readiness guarantee |

Accepted account IDs: 1-40 ASCII letters/digits/underscore/hyphen.
Keys: same characters, 1-80 length. Amount: 1 to 100,000,000 minor units.
Currency: EUR only. Countries: DE, FR, NL, IN, US (input whitelist, not risk scores).
Unknown JSON fields are rejected. One global key namespace, intentionally small.

## Container (configuration only, not executed)

```bash
docker build -t payment-event-lab .
docker run --rm -p 127.0.0.1:8080:8080 payment-event-lab
```

Do not expose it publicly: there is no authentication or TLS. Image tags are not digest-pinned.
No cloud resources are created and no paid infrastructure is required.

## Architecture and limits

```text
HTTP JSON -> validation -> synchronized Ledger -> event map/list
                                     |
                                     v
                             bounded queue -> audit consumer -> audit list
```

All state is in memory and lost on restart. Idempotency only lasts within this process.
There is no database, durable outbox, redelivery, dead-letter queue, multi-instance coordination
or exactly-once guarantee. Accepted events are capped at 10,000; then 503 is returned.
The lock trades throughput for understandable correctness. List endpoints are not paginated.
Demo flags are arbitrary rules, not calibrated fraud detection. Event timestamps use server time.
A proper production system would require durable storage, auth, observability, retention,
backpressure handling and independent security review.

## Study before using it in interviews

Read [STUDY_GUIDE.md](STUDY_GUIDE.md), run the commands, change a threshold, and explain the tests.
This is an assistant-assisted learning project, not evidence of prior professional banking work.
Do not claim Docker deployment or cloud experience from files alone.
Test evidence and exact environment: [verification.json](verification.json).

### References

- Spring REST guide: https://spring.io/guides/gs/rest-service
- Project scaffold: https://start.spring.io
- Maven package version was verified in Maven Central metadata, not inferred from a version label.

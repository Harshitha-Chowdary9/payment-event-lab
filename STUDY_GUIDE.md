# Explain this project

## Start with a real walkthrough

1. Build and run all tests. Open the application and test code side by side.
2. Send the curl request twice. Explain why the event ID is unchanged.
3. Change the amount but keep the key. Explain why a 409 is safer than silently accepting it.
4. Send three different keys for one account. Inspect RAPID_SEQUENCE on the third.
5. Restart. Notice events disappear. Explain why an in-memory demo is not a production ledger.
6. Read /api/audit immediately and later. Explain eventual consistency without promising timing.

## Concepts to learn

- Java records: immutable request/response values; generated equals compares payload fields.
- Bean Validation: annotations reject bad JSON before the endpoint calls the ledger.
- Minor units: EUR 10.50 is 1050, not a floating-point number.
- Map vs list: key lookup avoids scanning all events; lists support the small demo history.
- synchronized: protects check-then-insert and velocity checks in a single JVM.
- BlockingQueue: API enqueues work; daemon thread consumes it separately.
- Clock injection: tests simulate time without waiting a minute.
- Status codes: new/replay/conflict/invalid/missing/capacity are distinct conditions.
- Test layers: fast domain unit tests plus a running-server HTTP script exercise serialization and validation.

## Questions you should be able to answer

**Is it exactly once?** No. It prevents duplicate creation for one key within one process.
Restart loses keys; process failure can lose queued work. A durable outbox and consumer
idempotency would be needed for a stronger delivery design.

**What if requests arrive together?** The lock makes the key check and insert atomic.
The concurrency test launches 30 retries and checks only one new event exists.

**What makes it event-based?** Accepted events enter an in-memory queue and an independent
background consumer produces audit records. It is not a distributed broker or separate service.

**Why not call it AI fraud detection?** These are fixed educational rules, with no trained
model or accuracy study. Calling it AI would be misleading.

**What is the Dockerfile for?** A build stage compiles the JAR; a runtime stage launches it
as a non-root user. The configuration is unverified until you build and run it with Docker.

## Good next steps after understanding the current code

- Add a database with a unique idempotency-key constraint and a durable outbox.
- Add auth, pagination, metrics, retention and bounded request sizes.
- Introduce a real broker only after designing retry and duplicate behavior.
- Run Docker locally and record the result; only then discuss hands-on container execution.
- Add CI on a reviewed free runner plan. No private-repo workflow was activated here.

## Honest resume wording

After you have run and studied it: "Java/Spring Boot learning project implementing an
idempotent REST API, asynchronous in-process event auditing, input validation and tests."
Do not turn this into claims of AWS, Terraform, Kotlin, production microservices or banking experience.

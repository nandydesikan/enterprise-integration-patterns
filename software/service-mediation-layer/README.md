# Service Mediation Layer

Java 25 / Spring Boot implementation of the returns-processing reference workflow.

## Package boundaries

```text
io.github.nandydesikan.eip.returns
├── api              # HTTP contracts and exception translation
├── application      # Use-case coordination and outbound ports
├── domain           # Framework-free workflow rules and value objects
└── infrastructure   # Spring configuration and driven adapters
```

Dependencies point inward. `domain` has no Spring imports; `application` depends on domain abstractions; framework annotations and runtime integrations remain at the edges.

## Current scope

This increment provides:

- create/query workflow endpoints;
- semantic request fingerprinting;
- same-key/same-request idempotent replay;
- same-key/different-request conflict detection;
- a database-free in-memory reference adapter;
- Flyway-managed PostgreSQL workflow, idempotency, transition, and outbox tables;
- atomic PostgreSQL writes with concurrent-idempotency and rollback tests;
- liveness/readiness endpoints for ECS;
- domain and application tests.

The outbox relay that publishes pending rows, participant adapters, and recovery polling are intentionally subsequent increments. See the [verification runbook](../../docs/implementation/verify-design.md) for exact local and CI commands.

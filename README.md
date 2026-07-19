# Enterprise Integration Patterns — Durable Service Mediation

An executable reference implementation of a persistent, idempotent service-mediation layer for long-running returns processing.

The repository focuses on correctness under partial failure: stable operation identities, explicit transaction boundaries, uncertain-outcome reconciliation, deterministic recovery, compensating transactions, and transactional outbox publication.

## What this repository demonstrates

- an orchestrator that owns workflow progress without owning participant domain state;
- a framework-free domain model behind Spring Boot API and adapter boundaries;
- idempotent ingress with semantic request-conflict detection;
- an incremental path from an in-memory reference slice to PostgreSQL-backed recovery;
- an ECS Fargate deployment boundary with health checks, graceful shutdown, least-privilege task roles, and rollback on failed deployments.

## Repository map

```text
.
├── software/
│   └── service-mediation-layer/    # Java 25 / Spring Boot executable service
├── infrastructure/
│   └── ecs/                        # AWS ECS Fargate deployment assets
├── docs/
│   └── technical-design/           # LLD and architecture diagrams
└── .github/                         # Dependency and CI automation
```

| Folder | Ownership | What belongs here |
|---|---|---|
| `software/` | Application team | Domain, use cases, ports, adapters, API, tests, and container build |
| `infrastructure/` | Platform/deployment boundary | AWS resources and environment deployment instructions |
| `docs/` | Architecture record | Design rationale, diagrams, trade-offs, and implementation sequence |
| `.github/` | Repository automation | Dependency updates and continuous integration |

The split is intentional: application behavior can be tested without AWS, while deployment policy can evolve without leaking cloud SDKs into the domain.

## Current executable increment

The first increment implements workflow creation and query with deterministic request fingerprinting and in-memory idempotency. It establishes the architectural boundaries and tests before durable PostgreSQL persistence is introduced.

The in-memory adapter is a reference-stage component, not a production durability claim. The next increment replaces it with PostgreSQL, Flyway migrations, optimistic concurrency, and atomic workflow/outbox persistence as specified in the LLD.

## Run locally

Prerequisites: Java 25 and Maven 3.9+.

```bash
cd software/service-mediation-layer
mvn verify
mvn spring-boot:run
```

Create a workflow:

```bash
curl -i -X POST http://localhost:8080/api/v1/returns \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: interview-demo-001' \
  -d '{"returnRequestId":"RET-1001","customerId":"CUS-42","orderId":"ORD-9001","reason":"DAMAGED"}'
```

Health endpoints:

```text
GET /actuator/health/liveness
GET /actuator/health/readiness
```

## Design material

- [High-level architecture](docs/technical-design/integration-orchestration-high-level-architecture.png)
- [Low-level technical design](docs/technical-design/integration-orchestration-low-level-design.md)
- [Low-level implementation diagram](docs/technical-design/integration-orchestration-low-level-technical-implementation.png)
- [Deterministic recovery and compensation](docs/technical-design/compensating-transactions-deterministic-recovery-orchestration.png)
- [ECS deployment guide](infrastructure/ecs/README.md)

## Implementation sequence

1. Domain and application core — current increment.
2. PostgreSQL persistence, Flyway schema, and ingress idempotency races.
3. Participant adapters, failure classification, retry, and reconciliation.
4. Independently recoverable compensating transactions.
5. Transactional outbox publishing and event-consumption idempotency.
6. Production observability, CI/CD, and scenario-level tests.

## Provenance

This is an independent demonstration of publicly documented distributed-systems and enterprise-integration patterns. It contains no employer source code, confidential schemas, proprietary identifiers, or production data.

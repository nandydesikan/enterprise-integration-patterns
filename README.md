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
│   ├── technical-design/           # LLD and architecture diagrams
│   └── implementation/             # Reproducible build and verification runbooks
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

The current increment implements workflow creation and query with deterministic request fingerprinting. It offers a database-free in-memory profile for fast demonstrations and a PostgreSQL profile with Flyway, durable idempotency, transition history, and atomic workflow/outbox persistence.

## Run locally

Prerequisites: Java 25 and Maven 3.9+. From the repository root:

```bash
mvn verify
mvn -pl :service-mediation-layer spring-boot:run
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
- [Build and verification runbook](docs/implementation/verify-design.md)

## Implementation sequence

1. Domain, application core, PostgreSQL persistence, and atomic outbox write — current increment.
2. Participant adapters, failure classification, retry, and reconciliation.
3. Independently recoverable compensating transactions.
4. Outbox relay publication and event-consumption idempotency.
5. Production observability and scenario-level tests.

## Provenance

This is an independent demonstration of publicly documented distributed-systems and enterprise-integration patterns. It contains no employer source code, confidential schemas, proprietary identifiers, or production data.

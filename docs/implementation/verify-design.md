# Build and verification runbook

This runbook is the shortest reproducible path from a clean clone to evidence that the service-mediation design works. Run commands from the repository root unless a section says otherwise.

## What is implemented

The application has two runtime profiles:

| Profile | Purpose | Database required |
|---|---|---|
| `in-memory` | Fast local API and domain demonstration; this is the default | No |
| `postgres` | Durable idempotency, workflow state, transition history, and transactional outbox | Yes |

The PostgreSQL write path commits four records as one transaction: the idempotency claim, workflow instance, initial transition, and outbox event. A failure rolls back all four. Flyway owns schema creation and validation.

## Prerequisites

- Java 25
- Maven 3.9 or newer
- Docker only for the optional local PostgreSQL and image checks
- Node.js 24 and pnpm 11 for CDK validation
- AWS credentials only for `cdk diff`, bootstrap, or deployment

Confirm the toolchain:

```bash
java --version
mvn --version
docker --version
node --version
pnpm --version
```

## Sequence 1 — verify the database-free application

Compile and run the unit and Spring context tests:

```bash
mvn --batch-mode --no-transfer-progress verify
```

Start the default in-memory profile:

```bash
mvn -pl :service-mediation-layer spring-boot:run
```

In another terminal, verify readiness and create a workflow:

```bash
curl --fail http://localhost:8080/actuator/health/readiness

curl -i -X POST http://localhost:8080/api/v1/returns \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: interview-demo-001' \
  -d '{"returnRequestId":"RET-1001","customerId":"CUS-42","orderId":"ORD-9001","reason":"DAMAGED"}'
```

Repeat the same `POST`. It should return the same workflow rather than create another one. Reuse the key with a different `reason`; the API should reject the semantic conflict.

## Sequence 2 — verify PostgreSQL without installing it on macOS

This container is ephemeral. Stopping it removes the database and frees the container's writable storage.

```bash
docker run --rm --detach \
  --name mediation-postgres \
  -e POSTGRES_DB=mediation \
  -e POSTGRES_USER=mediation \
  -e POSTGRES_PASSWORD=mediation \
  -p 5432:5432 \
  postgres:17-alpine

until docker exec mediation-postgres pg_isready -U mediation -d mediation; do sleep 1; done
```

Run Flyway automatically at application startup and execute the PostgreSQL integration suite:

```bash
DATABASE_URL=jdbc:postgresql://localhost:5432/mediation \
DATABASE_USERNAME=mediation \
DATABASE_PASSWORD=mediation \
mvn --batch-mode --no-transfer-progress \
  -pl :service-mediation-layer \
  verify -Ppostgres-integration
```

The suite verifies migration history, atomic persistence, replay, conflict detection, a concurrent idempotency race, and rollback behavior.

Optionally run the API against that database:

```bash
SPRING_PROFILES_ACTIVE=postgres \
DATABASE_URL=jdbc:postgresql://localhost:5432/mediation \
DATABASE_USERNAME=mediation \
DATABASE_PASSWORD=mediation \
mvn -pl :service-mediation-layer spring-boot:run
```

Stop and remove the ephemeral database:

```bash
docker stop mediation-postgres
```

## Sequence 3 — build the ARM64 service image

The CDK stack declares Linux ARM64 Fargate tasks, so build the same platform from an Intel or Apple Silicon workstation:

```bash
docker buildx build \
  --platform linux/arm64 \
  --load \
  --tag service-mediation-layer:local \
  software/service-mediation-layer

docker image inspect service-mediation-layer:local \
  --format '{{.Os}}/{{.Architecture}}'
```

The final command should print `linux/arm64`.

## Sequence 4 — validate the AWS CDK deployment model

```bash
cd infrastructure/ecs
corepack enable
pnpm install --frozen-lockfile
pnpm run build
pnpm test
pnpm run synth
```

Review account-specific changes before deployment:

```bash
pnpm exec cdk bootstrap
pnpm run diff
pnpm run deploy
```

The stack creates billable resources: a NAT gateway, Application Load Balancer, Fargate service, and RDS PostgreSQL instance. `cdk diff` is a required human review gate. Do not commit credentials; CDK generates the database secret and injects only its username and password into the ECS task.

For a compact demonstration, the same generated database owner credential runs Flyway and the application. A production promotion should run Flyway as a deployment job, create a separate least-privilege runtime role, rotate both credentials, and deny DDL to the ECS application role. Keeping that boundary explicit is more credible than claiming this showcase configuration is production-complete.

## Sequence 5 — use CI as the durable PostgreSQL proof

GitHub Actions starts a PostgreSQL 17 service container and runs the same Maven integration profile on every pull request and push to `main`. It separately compiles, tests, and synthesizes the CDK application.

Before pushing:

```bash
git diff --check
git status --short
git diff --stat
```

Stage and review deliberately:

```bash
git add pom.xml .github docs software infrastructure
git diff --cached --check
git diff --cached --stat
git commit -m "Add PostgreSQL-backed mediation verification slice"
git push origin main
```

Open the repository's **Actions** tab and require both `Java and PostgreSQL` and `AWS CDK` jobs to pass.

## Evidence map

| Design claim | Executable evidence |
|---|---|
| Domain logic remains framework-free | `software/service-mediation-layer/src/main/java/.../domain` |
| Default local startup needs no database | `application-in-memory.yml` and Spring context test |
| Schema changes are versioned | `db/migration/V001__create_workflow_core.sql` |
| Ingress is durable and idempotent | `PostgresWorkflowRepository` plus replay/race integration tests |
| State and event publication intent are atomic | workflow, transition, idempotency, and outbox inserts share one transaction |
| PostgreSQL is private in AWS | isolated data subnets and security-group tests in the CDK stack |
| Credentials are not in source or task environment literals | generated Secrets Manager secret and ECS secret injection |
| Workstation architecture cannot silently change deployment architecture | ARM64 Docker build and ARM64 Fargate runtime contract |

## Migration discipline

Never edit an applied Flyway migration. Add the next immutable file, for example `V002__add_recovery_lease.sql`, then run the PostgreSQL integration profile. Flyway's schema history checksum makes accidental rewrites visible.

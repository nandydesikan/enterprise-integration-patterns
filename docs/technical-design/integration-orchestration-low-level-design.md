# Integration Orchestration Layer — Low-Level Technical Design

**Status:** Design baseline  
**Scope:** First executable reference implementation  
**Repository:** `enterprise-integration-patterns`  
**Language:** Java 25  
**Build:** Maven  

## 1. Design intent

This implementation demonstrates how a workflow can coordinate work across independently owned bounded contexts without taking ownership of their domain state.

The design focuses on the hard parts of enterprise integration:

- durable workflow progress;
- explicit transaction boundaries;
- idempotent request and downstream operation handling;
- recovery from partial and uncertain failures;
- anti-corruption adapters between domain contracts;
- reliable event publication;
- observable execution.

The first release deliberately implements one concrete workflow. It does not begin as a generic workflow engine or configurable state-machine platform.

## 2. Reference workflow

The fictional domain is **returns processing**.

A customer-facing Returns context initiates a return. The orchestration layer coordinates with independently owned Shipping and Payments contexts.

### Happy path

1. Accept a return request.
2. Validate return eligibility.
3. Reserve a return shipment.
4. Wait for confirmation that the item was received.
5. Request a refund.
6. Mark the workflow complete.
7. Publish workflow lifecycle events through an outbox.

### Why this workflow

Returns processing naturally demonstrates:

- synchronous commands;
- asynchronous waiting;
- long-running durable state;
- duplicate client requests;
- retryable dependency failures;
- ambiguous downstream outcomes;
- compensation before physical receipt;
- reconciliation after irreversible business progress.

## 3. Bounded contexts and ownership

### Returns context

Owns:

- return policy;
- customer-facing return request;
- eligibility decision;
- return authorization identity.

Does not own:

- shipment reservation state;
- physical receipt state;
- refund ledger state.

### Shipping context

Owns:

- return-shipment reservation;
- tracking reference;
- shipment cancellation;
- item-received event.

### Payments context

Owns:

- refund decision;
- refund transaction;
- refund status.

### Integration Orchestration Layer

Owns only:

- workflow identity and progress;
- step-execution records;
- operation identities;
- retry and reconciliation decisions;
- correlation metadata;
- transition history;
- outbox records.

It does not persist authoritative copies of Shipping or Payments domain entities.

## 4. Architectural style

The implementation uses a **modular monolith with ports and adapters**.

This is intentional. The first artifact should expose the workflow, transaction, and failure semantics without adding distributed deployment complexity to the demonstration itself.

Logical boundaries remain explicit enough to extract later if scale, team ownership, or deployment independence justifies it.

## 5. Proposed package structure

```text
io.github.nandydesikan.eip.returns
├── api
│   ├── ReturnRequestController
│   ├── ReturnQueryController
│   ├── request
│   └── response
├── application
│   ├── StartReturnWorkflow
│   ├── AdvanceReturnWorkflow
│   ├── HandleItemReceived
│   ├── RetryStepExecution
│   ├── ReconcileUnknownOutcome
│   └── port
│       ├── ReturnPolicyPort
│       ├── ShippingPort
│       ├── PaymentsPort
│       ├── WorkflowRepository
│       ├── StepExecutionRepository
│       ├── OutboxRepository
│       ├── ClockPort
│       └── IdentifierPort
├── domain
│   ├── ReturnWorkflow
│   ├── ReturnWorkflowId
│   ├── ReturnRequestId
│   ├── ReturnWorkflowPhase
│   ├── WorkflowStatus
│   ├── WorkflowTransition
│   ├── StepExecution
│   ├── StepName
│   ├── StepExecutionStatus
│   ├── OperationIdentity
│   ├── FailureClassification
│   └── exception
└── infrastructure
    ├── persistence
    │   ├── entity
    │   ├── repository
    │   └── mapper
    ├── shipping
    ├── payments
    ├── policy
    ├── outbox
    ├── observability
    └── configuration
```

### Boundary rule

The `domain` package has no Spring, JPA, HTTP, or vendor SDK dependencies.

The `application` package coordinates use cases through ports.

The `infrastructure` package implements ports and contains framework-specific code.

The `api` package translates transport contracts into application commands and maps results back to transport responses.

## 6. Core domain model

### ReturnWorkflow

`ReturnWorkflow` is the aggregate responsible for validating legal workflow transitions.

It contains:

- workflow ID;
- originating return-request ID;
- current business phase;
- overall workflow status;
- current logical step;
- version;
- timestamps;
- external references required to continue execution;
- last recorded failure classification.

It does not perform network calls or persistence.

### Business phase and technical execution status

Business progress and technical execution status are modeled separately.

`ReturnWorkflowPhase`:

```text
REQUESTED
ELIGIBILITY_CONFIRMED
RETURN_SHIPMENT_RESERVED
AWAITING_ITEM_RECEIPT
ITEM_RECEIVED
REFUND_CONFIRMED
COMPLETED
REJECTED
CANCELLED
```

`WorkflowStatus`:

```text
ACTIVE
WAITING
RETRY_PENDING
RECONCILIATION_REQUIRED
COMPENSATION_REQUIRED
COMPLETED
FAILED
```

This separation avoids turning every timeout and retry into a new business state.

### StepExecution

Each external operation has a durable `StepExecution` record.

It contains:

- stable operation identity;
- workflow ID;
- step name;
- logical occurrence number;
- request fingerprint;
- execution status;
- attempt count;
- next eligible retry time;
- downstream reference;
- failure classification;
- response summary;
- created and updated timestamps.

`StepExecutionStatus`:

```text
READY
IN_PROGRESS
SUCCEEDED
RETRY_SCHEDULED
OUTCOME_UNKNOWN
PERMANENTLY_FAILED
COMPENSATED
```

## 7. State-transition rules

The aggregate permits only explicit transitions.

| Current phase | Trigger | Next phase |
|---|---|---|
| `REQUESTED` | Eligibility approved | `ELIGIBILITY_CONFIRMED` |
| `REQUESTED` | Eligibility rejected | `REJECTED` |
| `ELIGIBILITY_CONFIRMED` | Shipment reservation confirmed | `RETURN_SHIPMENT_RESERVED` |
| `RETURN_SHIPMENT_RESERVED` | Begin waiting | `AWAITING_ITEM_RECEIPT` |
| `AWAITING_ITEM_RECEIPT` | Item-received event accepted | `ITEM_RECEIVED` |
| `ITEM_RECEIVED` | Refund confirmed | `REFUND_CONFIRMED` |
| `REFUND_CONFIRMED` | Completion recorded | `COMPLETED` |

Illegal transitions fail before any persistence or external call.

Technical failures change `WorkflowStatus` and `StepExecutionStatus`; they do not falsely advance the business phase.

## 8. Idempotency model

Two idempotency boundaries are required.

### Ingress idempotency

The client supplies an `Idempotency-Key` when creating a return workflow.

The system stores:

- idempotency key;
- normalized request fingerprint;
- resulting workflow ID;
- current response status.

Behavior:

- same key and same fingerprint: return the existing workflow;
- same key and different fingerprint: reject with conflict;
- new key: create a new workflow.

### Downstream operation idempotency

Every logical external operation receives a stable operation identity.

The operation identity is derived from:

```text
workflow ID + step name + logical occurrence
```

Retries reuse the same operation identity. Attempt count is not part of the downstream idempotency identity.

This allows Shipping or Payments to recognize repeated delivery of the same logical command.

## 9. Request fingerprinting

The fingerprint is calculated from a canonical representation of fields that define the semantic request.

It excludes:

- timestamps generated by the server;
- tracing headers;
- transport-specific metadata;
- fields that do not alter the requested business operation.

The initial implementation uses a deterministic JSON representation and SHA-256.

The original request is not stored solely for the purpose of proving equality; only the minimum required business fields and fingerprint are retained.

## 10. Persistence model

The initial implementation uses PostgreSQL in integration tests through Testcontainers.

### `workflow_instance`

```text
workflow_id              UUID primary key
workflow_type            varchar
business_key             varchar
phase                    varchar
status                   varchar
current_step             varchar nullable
version                  bigint
failure_classification   varchar nullable
created_at               timestamp
updated_at               timestamp
```

Constraints:

- unique workflow ID;
- unique business key where the domain permits one active workflow;
- optimistic-lock version.

### `idempotency_record`

```text
idempotency_key          varchar primary key
request_fingerprint      varchar
workflow_id              UUID
response_status          varchar
created_at               timestamp
updated_at               timestamp
```

### `step_execution`

```text
operation_id             UUID primary key
workflow_id              UUID
step_name                varchar
occurrence               integer
request_fingerprint      varchar
status                   varchar
attempt_count            integer
next_retry_at            timestamp nullable
downstream_reference     varchar nullable
failure_classification   varchar nullable
response_summary         jsonb nullable
version                  bigint
created_at               timestamp
updated_at               timestamp
```

Constraints:

- unique `(workflow_id, step_name, occurrence)`;
- optimistic-lock version.

### `workflow_transition`

```text
transition_id            UUID primary key
workflow_id              UUID
from_phase                varchar
to_phase                  varchar
trigger                   varchar
operation_id              UUID nullable
occurred_at               timestamp
```

This table is an audit history, not an event-sourced aggregate store.

### `outbox_event`

```text
event_id                  UUID primary key
aggregate_id              UUID
event_type                varchar
payload                   jsonb
status                    varchar
attempt_count             integer
available_at              timestamp
published_at              timestamp nullable
created_at                timestamp
```

## 11. Transaction boundaries

No database transaction remains open during a network call.

### Start workflow transaction

One transaction:

1. claim the ingress idempotency key;
2. create the workflow;
3. record the initial transition;
4. write `ReturnWorkflowStarted` to the outbox.

### Prepare external operation transaction

One transaction:

1. load the workflow with optimistic version;
2. verify the expected phase and status;
3. create or claim the `StepExecution`;
4. mark it `IN_PROGRESS`;
5. persist the workflow's current logical step.

The transaction commits before dispatching the network request.

### External dispatch

The adapter invokes the participating context outside the database transaction.

### Record definitive result transaction

One transaction:

1. reload the workflow and step execution;
2. verify that the result applies to the current operation;
3. persist the downstream reference and result;
4. advance the workflow phase when appropriate;
5. record the transition;
6. write an outbox event.

### Record uncertain outcome transaction

When the request may have reached the participant but no definitive response exists:

1. mark the step `OUTCOME_UNKNOWN`;
2. mark the workflow `RECONCILIATION_REQUIRED`;
3. retain the same operation identity;
4. publish an operational event;
5. do not blindly retry.

## 12. Concurrency control

The first implementation uses optimistic concurrency.

- `workflow_instance.version` prevents two workers from advancing the same workflow from the same version.
- `step_execution.version` protects concurrent updates to an operation.
- unique constraints prevent duplicate logical operations.
- a worker that loses the optimistic-lock race reloads state and re-evaluates whether work remains.

The first version does not implement distributed leases. Lease-based execution is a documented production evolution, not a prerequisite for the portfolio slice.

## 13. Failure classification

### Business rejection

Examples:

- return window expired;
- item is not returnable;
- refund rejected by policy.

Behavior:

- no retry;
- transition to `REJECTED` or `FAILED`, depending on ownership;
- publish a definitive failure event.

### Retryable technical failure

Examples:

- connection refused;
- explicit throttling;
- temporary dependency outage.

Behavior:

- increment attempt count;
- calculate next retry time;
- mark step `RETRY_SCHEDULED`;
- retain the same operation identity;
- stop after the retry budget is exhausted.

### Uncertain outcome

Examples:

- timeout after request dispatch;
- connection loss while reading the response.

Behavior:

- do not assume failure;
- do not immediately issue a duplicate command;
- mark `OUTCOME_UNKNOWN`;
- reconcile using the stable operation identity.

### Internal invariant violation

Examples:

- impossible state transition;
- missing required external reference;
- conflicting persisted result.

Behavior:

- fail fast;
- emit high-severity telemetry;
- move the workflow to manual review rather than guessing.

## 14. Retry policy

Retry policy belongs to the application layer and is selected by step and failure classification.

The baseline supports:

- maximum attempt count;
- exponential backoff;
- maximum delay;
- retryable exception categories;
- optional server-provided retry-after time.

The implementation will not retry:

- validation errors;
- business rejections;
- authorization failures without refreshed credentials;
- uncertain outcomes.

## 15. Compensation and reconciliation

Compensation is not described as rollback.

### Compensation example

If a return shipment was reserved but the return is cancelled before the item is received, the orchestrator may request cancellation of the shipment reservation.

The cancellation command has its own stable operation identity and durable execution record.

### Reconciliation example

If a refund request times out after dispatch, the orchestrator queries Payments using the original operation identity.

Possible reconciliation results:

- refund exists: record the reference and continue;
- refund definitely does not exist: retry the original operation;
- outcome remains indeterminate: route to manual review.

After the physical item is received, the design does not attempt to "undo" receipt. It moves forward through refund or manual resolution.

## 16. Ports and anti-corruption adapters

### ReturnPolicyPort

Evaluates eligibility using the Returns context's contract.

### ShippingPort

Supports:

- reserve return shipment;
- cancel return shipment;
- find shipment by operation identity.

The Shipping adapter maps orchestration commands into Shipping-specific contracts and maps responses into narrow application results.

### PaymentsPort

Supports:

- request refund;
- find refund by operation identity.

The Payments adapter prevents Payments-specific models and error codes from leaking into the workflow aggregate.

## 17. API surface

### Create workflow

```text
POST /api/v1/returns
Idempotency-Key: <client-generated-key>
```

Response:

- `202 Accepted` for newly accepted long-running work;
- `200 OK` when returning a previously completed idempotent result;
- `409 Conflict` when an idempotency key is reused with different semantic input;
- `422 Unprocessable Entity` for a definitive business rejection.

### Query workflow

```text
GET /api/v1/returns/{workflowId}
```

Returns:

- business phase;
- technical status;
- current step;
- external references safe for exposure;
- failure category;
- links to available actions.

### Receive item event

The production design expects an event consumer.

For the first executable slice, an internal test endpoint may simulate the event:

```text
POST /internal/test/returns/{workflowId}/item-received
```

The endpoint is excluded from production profiles.

## 18. Outbox behavior

Workflow state and its corresponding event are written in the same local transaction.

A separate publisher:

1. polls eligible outbox rows;
2. publishes the event;
3. marks the row published;
4. retries failed publication.

Delivery is at least once.

Consumers must use the event ID or operation identity for deduplication. The outbox does not claim exactly-once end-to-end delivery.

## 19. Observability

### Correlation fields

Every structured log and event includes:

- workflow ID;
- operation ID when applicable;
- idempotency key hash, not the raw key;
- step name;
- workflow phase;
- workflow status;
- trace ID;
- failure classification.

### Metrics

Baseline metrics:

- workflows started, completed, rejected, and failed;
- active workflows by phase;
- step execution duration;
- retries by step and failure class;
- uncertain outcomes;
- reconciliation age;
- workflows stalled beyond threshold;
- outbox publication lag.

### Alerts

Production evolution:

- growing reconciliation backlog;
- workflows stalled in one phase;
- sustained downstream failure rate;
- retry exhaustion;
- outbox lag;
- optimistic-lock conflict spike.

## 20. Security boundaries

The reference implementation demonstrates the placement of security controls without embedding real credentials or provider-specific infrastructure.

Controls:

- request validation at ingress;
- authorization policy at use-case boundaries;
- service identity at external adapters;
- no credentials in source control;
- secrets supplied through environment variables;
- sensitive payload fields excluded from logs;
- least-privilege database and service permissions;
- immutable audit records for workflow transitions.

Authentication plumbing is not the focus of the first slice. A lightweight local security configuration may be used, with the production trust model documented separately.

## 21. Test strategy

### Domain tests

- legal and illegal transitions;
- completion invariants;
- compensation eligibility;
- no business-phase advancement on technical failure.

### Application tests with fake ports

- duplicate ingress request returns existing workflow;
- conflicting idempotency reuse is rejected;
- retryable failure schedules retry;
- uncertain outcome enters reconciliation;
- same downstream operation identity is reused across attempts;
- confirmed downstream result advances the workflow.

### Persistence integration tests

Using PostgreSQL Testcontainers:

- optimistic locking;
- unique operation constraint;
- idempotency-key race;
- workflow state and outbox atomicity;
- transaction rollback behavior.

### Scenario tests

Minimum credible scenario suite:

1. happy-path return;
2. duplicate request with identical input;
3. duplicate key with conflicting input;
4. shipment throttling followed by successful retry;
5. shipment timeout followed by successful reconciliation;
6. concurrent attempts to advance one workflow;
7. refund rejection;
8. process restart followed by resume from persisted state;
9. outbox publication failure followed by redelivery;
10. cancellation before item receipt followed by shipment compensation.

## 22. Deliberate exclusions

The first implementation will not include:

- a generic workflow DSL;
- dynamic state-machine definitions;
- a visual workflow editor;
- Kafka or cloud-specific messaging;
- Kubernetes manifests;
- multi-region deployment;
- distributed locking;
- event sourcing;
- a universal canonical enterprise model;
- arbitrary plugin loading;
- a generic policy engine;
- full production identity-provider integration.

These exclusions keep the artifact explainable and make the difficult correctness behavior visible.

## 23. Planned implementation increments

### Increment 1 — Domain and application core

- Maven project;
- package boundaries;
- workflow aggregate;
- state-transition tests;
- application ports;
- fake adapters.

### Increment 2 — Persistence and idempotency

- PostgreSQL schema;
- JPA or JDBC persistence adapters;
- ingress idempotency;
- step-execution persistence;
- optimistic concurrency tests.

### Increment 3 — External operation handling

- Shipping and Payments adapters;
- retry classification;
- uncertain-outcome reconciliation;
- compensation flow.

### Increment 4 — API and outbox

- REST API;
- outbox persistence and publisher;
- workflow query model;
- scenario tests.

### Increment 5 — Operational polish

- structured logging;
- metrics;
- CI build;
- architecture decision records;
- concise repository README.

## 24. Interview narrative

The design should support a 10–15 minute explanation:

1. Why direct service chaining is insufficient.
2. Why the orchestrator owns workflow progress but not domain state.
3. How stable operation identity prevents duplicate effects.
4. Why network calls are outside database transactions.
5. How definitive failure differs from uncertain outcome.
6. Why reconciliation is sometimes safer than retry.
7. How optimistic locking and unique constraints protect execution.
8. How state changes and events are committed atomically.
9. Why the first implementation is concrete rather than generic.
10. How the design could evolve under production scale.

## 25. Provenance

This repository is an independent implementation of publicly documented distributed-systems and enterprise-integration patterns. It contains no employer source code, confidential documentation, proprietary schemas, internal identifiers, or production data.

# ECS Fargate deployment

This AWS CDK application defines the runtime boundary for the service-mediation layer.

## Resources

- a two-AZ VPC with public ingress and private application subnets;
- one NAT gateway for private-task egress;
- an ECS cluster with Container Insights;
- an Application Load Balancer and readiness target check;
- a two-task Fargate service with deployment rollback;
- an encrypted RDS PostgreSQL instance in isolated subnets;
- generated database credentials injected from Secrets Manager;
- a security-group rule limited to ECS task-to-database traffic;
- a deterministic Linux ARM64 image and Fargate runtime contract;
- task-level container health checks;
- CPU target-tracking between two and six tasks;
- a one-month CloudWatch Logs group;
- CDK-managed least-privilege execution and task roles;
- a Docker image asset built from `software/service-mediation-layer`.

The task role receives no application permissions because the service does not call AWS APIs. ECS retrieves the database secret through its execution role; application code receives the values as runtime configuration.

## Important scope boundary

The deployed profile uses PostgreSQL and Flyway for durable ingress. For demonstration compactness, the generated database owner credential runs both migrations and the service. A production promotion would split migration and least-privilege runtime roles. Multi-AZ, deletion protection, private database administration, secret rotation, alarms, and the outbox relay also remain explicit production-hardening decisions rather than hidden demo claims.

## Prerequisites

- Node.js 22+ with Corepack/pnpm;
- Docker;
- AWS credentials for the target account;
- a bootstrapped CDK environment.

## Validate and deploy

```bash
cd infrastructure/ecs
corepack enable
pnpm install --frozen-lockfile
pnpm run build
pnpm test
pnpm exec cdk bootstrap
pnpm run diff
pnpm run deploy
```

`cdk diff` is the review gate. Do not deploy this demonstration stack blindly: it creates billable resources, including a NAT gateway, load balancer, Fargate tasks, RDS instance, and logs.

## Why CDK is separate

The infrastructure app consumes the service's Docker build context but has no influence on domain compilation. This preserves independent application tests and makes cloud-specific policy visible in one place.

# ECS Fargate deployment

This AWS CDK application defines the runtime boundary for the service-mediation layer.

## Resources

- a two-AZ VPC with public ingress and private application subnets;
- one NAT gateway for private-task egress;
- an ECS cluster with Container Insights;
- an Application Load Balancer and readiness target check;
- a two-task Fargate service with deployment rollback;
- a deterministic Linux ARM64 image and Fargate runtime contract;
- task-level container health checks;
- CPU target-tracking between two and six tasks;
- a one-month CloudWatch Logs group;
- CDK-managed least-privilege execution and task roles;
- a Docker image asset built from `software/service-mediation-layer`.

The task role receives no application permissions in this increment because the service does not call AWS APIs. Permissions should be added only alongside a concrete adapter that requires them.

## Important scope boundary

The deployed first increment uses the explicit `in-memory` adapter. It is suitable for validating packaging, health behavior, deployment rollback, and API shape—not for production workflow durability. PostgreSQL and its security-group/secret wiring arrive with the persistence increment.

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

`cdk diff` is the review gate. Do not deploy this demonstration stack blindly: it creates billable resources, including a NAT gateway, load balancer, Fargate tasks, and logs.

## Why CDK is separate

The infrastructure app consumes the service's Docker build context but has no influence on domain compilation. This preserves independent application tests and makes cloud-specific policy visible in one place.

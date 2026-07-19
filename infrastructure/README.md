# Infrastructure

This folder contains deployment definitions and environment-facing operational policy. Application business logic does not belong here.

`ecs/` packages the service for AWS ECS on Fargate. The current template assumes a supplied VPC and subnets and creates the service-specific load balancer, security groups, task definition, IAM roles, log group, cluster, and service.

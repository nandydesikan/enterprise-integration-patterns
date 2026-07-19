import * as path from 'node:path';
import {
  CfnOutput,
  Duration,
  RemovalPolicy,
  Stack,
  StackProps,
  aws_ec2 as ec2,
  aws_ecr_assets as ecrAssets,
  aws_ecs as ecs,
  aws_ecs_patterns as ecsPatterns,
  aws_logs as logs,
} from 'aws-cdk-lib';
import { Construct } from 'constructs';

export class ServiceMediationStack extends Stack {
  constructor(scope: Construct, id: string, props?: StackProps) {
    super(scope, id, props);

    const vpc = new ec2.Vpc(this, 'Vpc', {
      maxAzs: 2,
      natGateways: 1,
      subnetConfiguration: [
        {
          name: 'ingress',
          subnetType: ec2.SubnetType.PUBLIC,
          cidrMask: 24,
        },
        {
          name: 'application',
          subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS,
          cidrMask: 24,
        },
      ],
    });

    const cluster = new ecs.Cluster(this, 'Cluster', {
      vpc,
      containerInsightsV2: ecs.ContainerInsights.ENABLED,
    });

    const logGroup = new logs.LogGroup(this, 'ApplicationLogs', {
      retention: logs.RetentionDays.ONE_MONTH,
      removalPolicy: RemovalPolicy.DESTROY,
    });

    const service = new ecsPatterns.ApplicationLoadBalancedFargateService(this, 'Service', {
      cluster,
      serviceName: 'service-mediation-layer',
      cpu: 512,
      memoryLimitMiB: 1024,
      desiredCount: 2,
      minHealthyPercent: 100,
      maxHealthyPercent: 200,
      runtimePlatform: {
        cpuArchitecture: ecs.CpuArchitecture.ARM64,
        operatingSystemFamily: ecs.OperatingSystemFamily.LINUX,
      },
      publicLoadBalancer: true,
      assignPublicIp: false,
      circuitBreaker: { rollback: true },
      healthCheckGracePeriod: Duration.seconds(60),
      healthCheck: {
        command: [
          'CMD-SHELL',
          'curl --fail --silent http://localhost:8080/actuator/health/readiness || exit 1',
        ],
        interval: Duration.seconds(30),
        timeout: Duration.seconds(5),
        retries: 3,
        startPeriod: Duration.seconds(30),
      },
      taskImageOptions: {
        containerName: 'service-mediation-layer',
        containerPort: 8080,
        image: ecs.ContainerImage.fromAsset(
          path.resolve(__dirname, '../../../software/service-mediation-layer'),
          { platform: ecrAssets.Platform.LINUX_ARM64 },
        ),
        environment: {
          MEDIATION_PERSISTENCE_MODE: 'in-memory',
          MEDIATION_RECOVERY_BATCH_SIZE: '50',
          MEDIATION_RECOVERY_LEASE_DURATION: 'PT30S',
        },
        logDriver: ecs.LogDrivers.awsLogs({
          logGroup,
          streamPrefix: 'application',
        }),
      },
    });

    service.targetGroup.configureHealthCheck({
      path: '/actuator/health/readiness',
      healthyHttpCodes: '200',
      interval: Duration.seconds(30),
      timeout: Duration.seconds(5),
    });

    const scaling = service.service.autoScaleTaskCount({
      minCapacity: 2,
      maxCapacity: 6,
    });
    scaling.scaleOnCpuUtilization('CpuScaling', {
      targetUtilizationPercent: 65,
      scaleInCooldown: Duration.seconds(120),
      scaleOutCooldown: Duration.seconds(60),
    });

    new CfnOutput(this, 'ServiceUrl', {
      value: `http://${service.loadBalancer.loadBalancerDnsName}`,
      description: 'Public ALB endpoint for the reference service',
    });
  }
}

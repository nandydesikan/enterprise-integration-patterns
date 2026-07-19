import { App } from 'aws-cdk-lib';
import { Template } from 'aws-cdk-lib/assertions';
import { ServiceMediationStack } from '../lib/service-mediation-stack';

describe('ServiceMediationStack', () => {
  const app = new App();
  const stack = new ServiceMediationStack(app, 'TestStack');
  const template = Template.fromStack(stack);

  test('runs two private Fargate tasks behind a public load balancer', () => {
    template.hasResourceProperties('AWS::ECS::Service', {
      DesiredCount: 2,
      LaunchType: 'FARGATE',
      DeploymentConfiguration: {
        DeploymentCircuitBreaker: {
          Enable: true,
          Rollback: true,
        },
      },
      NetworkConfiguration: {
        AwsvpcConfiguration: {
          AssignPublicIp: 'DISABLED',
        },
      },
    });
  });

  test('checks application readiness at the task and target-group boundaries', () => {
    template.hasResourceProperties('AWS::ECS::TaskDefinition', {
      Cpu: '512',
      Memory: '1024',
      RuntimePlatform: {
        CpuArchitecture: 'ARM64',
        OperatingSystemFamily: 'LINUX',
      },
      ContainerDefinitions: [
        {
          HealthCheck: {
            Command: [
              'CMD-SHELL',
              'curl --fail --silent http://localhost:8080/actuator/health/readiness || exit 1',
            ],
            Interval: 30,
            Retries: 3,
            StartPeriod: 30,
            Timeout: 5,
          },
        },
      ],
    });

    template.hasResourceProperties('AWS::ElasticLoadBalancingV2::TargetGroup', {
      HealthCheckPath: '/actuator/health/readiness',
      Matcher: { HttpCode: '200' },
    });
  });
});

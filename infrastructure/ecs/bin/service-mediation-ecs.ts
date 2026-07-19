#!/usr/bin/env node
import * as cdk from 'aws-cdk-lib';
import { ServiceMediationStack } from '../lib/service-mediation-stack';

const app = new cdk.App();

new ServiceMediationStack(app, 'ServiceMediationStack', {
  env: {
    account: process.env.CDK_DEFAULT_ACCOUNT,
    region: process.env.CDK_DEFAULT_REGION,
  },
  description: 'ECS Fargate runtime for the durable service-mediation reference implementation',
});

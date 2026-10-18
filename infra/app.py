#!/usr/bin/env python3
import os
import aws_cdk as cdk
from network_stack import NetworkStack
from security_stack import SecurityStack
from data_stack import DataStack
from compute_stack import ComputeStack

app = cdk.App()

env = cdk.Environment(
    account=os.getenv("CDK_DEFAULT_ACCOUNT", "123456789012"),
    region=os.getenv("CDK_DEFAULT_REGION", "us-east-1")
)

# 1. Redes (VPC de 3 niveles)
network_stack = NetworkStack(app, "PieNetworkStack", env=env)

# 2. Seguridad (KMS y Secretos)
security_stack = SecurityStack(app, "PieSecurityStack", env=env)

# 3. Datos (Redis Multi-AZ & Aurora Serverless v2)
data_stack = DataStack(
    app, "PieDataStack",
    vpc=network_stack.vpc,
    kms_key=security_stack.kms_key,
    env=env
)

# 4. Cómputo (Cluster EKS Graviton3)
compute_stack = ComputeStack(
    app, "PieComputeStack",
    vpc=network_stack.vpc,
    kms_key=security_stack.kms_key,
    redis_sg=data_stack.redis_sg,
    env=env
)

app.synth()

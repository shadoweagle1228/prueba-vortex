#!/usr/bin/env python3
import os

from aws_cdk import App, Environment

from compute_stack import ComputeStack
from data_stack import DataStack
from network_stack import NetworkStack
from security_stack import SecurityStack

app = App(outdir="cdk.out")

env = Environment(
    account=os.environ.get("CDK_DEFAULT_ACCOUNT"),
    region=os.environ.get("CDK_DEFAULT_REGION", "us-east-1"),
)

network = NetworkStack(app, "AndesRetailNetworkStack", env=env)
# La clave KMS se crea antes que la capa de datos: Redis y Aurora la usan para el cifrado en reposo (NFR-06).
security = SecurityStack(app, "AndesRetailSecurityStack", env=env)
data = DataStack(app, "AndesRetailDataStack", network.vpc, security.kms_key, env=env)
compute = ComputeStack(app, "AndesRetailComputeStack", network.vpc, data.aurora_cluster, env=env)

app.synth()

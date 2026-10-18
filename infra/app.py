#!/usr/bin/env python3
import os
from aws_cdk import App, Environment
from network_stack import NetworkStack
from data_stack import DataStack
from compute_stack import ComputeStack

app = App()

env = Environment(
    account=os.environ.get("CDK_DEFAULT_ACCOUNT"),
    region=os.environ.get("CDK_DEFAULT_REGION", "us-east-1"),
)

network = NetworkStack(app, "AndesRetailNetworkStack", env=env)
data = DataStack(app, "AndesRetailDataStack", network.vpc, env=env)
compute = ComputeStack(app, "AndesRetailComputeStack", network.vpc, env=env)

app.synth()

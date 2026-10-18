from aws_cdk import Stack, CfnOutput
from aws_cdk.aws_ec2 import (
    Vpc,
    SubnetConfiguration,
    SubnetType,
    FlowLog,
    FlowLogTrafficType,
    FlowLogResourceType,
)
from constructs import Construct


class NetworkStack(Stack):
    def __init__(self, scope: Construct, construct_id: str, **kwargs):
        super().__init__(scope, construct_id, **kwargs)

        self.vpc = Vpc(
            self,
            "VPC",
            max_azs=3,
            nat_gateways=3,
            subnet_configuration=[
                SubnetConfiguration(
                    name="public",
                    subnet_type=SubnetType.PUBLIC,
                    cidr_mask=24,
                ),
                SubnetConfiguration(
                    name="private",
                    subnet_type=SubnetType.PRIVATE_WITH_EGRESS,
                    cidr_mask=24,
                ),
                SubnetConfiguration(
                    name="isolated",
                    subnet_type=SubnetType.PRIVATE_ISOLATED,
                    cidr_mask=24,
                ),
            ],
        )

        FlowLog(
            self,
            "VPCFlowLog",
            resource_type=FlowLogResourceType.from_vpc(self.vpc),
            traffic_type=FlowLogTrafficType.ALL,
        )

        CfnOutput(self, "VpcId", value=self.vpc.vpc_id)

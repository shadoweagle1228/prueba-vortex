from aws_cdk import Stack
from aws_cdk.aws_ec2 import Vpc, SubnetSelection, SubnetType, InstanceType, InstanceClass, InstanceSize
from aws_cdk.aws_eks import (
    Cluster,
    KubernetesVersion,
    Nodegroup,
    CapacityType,
)
from aws_cdk.aws_iam import Role, ManagedPolicy, ServicePrincipal
from constructs import Construct


class ComputeStack(Stack):
    def __init__(self, scope: Construct, construct_id: str, vpc: Vpc, **kwargs):
        super().__init__(scope, construct_id, **kwargs)

        cluster = Cluster(
            self,
            "EKSCluster",
            version=KubernetesVersion.V1_29,
            vpc=vpc,
            vpc_subnets=[SubnetSelection(subnet_type=SubnetType.PRIVATE_WITH_EGRESS)],
            default_capacity=0,
        )

        nodegroup_role = Role(
            self,
            "NodegroupRole",
            assumed_by=ServicePrincipal("ec2.amazonaws.com"),
            managed_policies=[
                ManagedPolicy.from_aws_managed_policy_name(
                    "AmazonEKSWorkerNodePolicy"
                ),
                ManagedPolicy.from_aws_managed_policy_name(
                    "AmazonEKS_CNI_Policy"
                ),
                ManagedPolicy.from_aws_managed_policy_name(
                    "AmazonEC2ContainerRegistryReadOnly"
                ),
            ],
        )

        Nodegroup(
            self,
            "NodeGroup",
            cluster=cluster,
            capacity_type=CapacityType.SPOT,
            instance_types=[InstanceType.of(InstanceClass.T4G, InstanceSize.MEDIUM)],
            min_size=2,
            max_size=4,
            desired_size=2,
            node_role=nodegroup_role,
            subnets=SubnetSelection(subnet_type=SubnetType.PRIVATE_WITH_EGRESS),
        )

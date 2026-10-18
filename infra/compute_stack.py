from aws_cdk import (
    Stack,
    aws_ec2 as ec2,
    aws_eks as eks,
    aws_iam as iam,
    aws_kms as kms,
)
from constructs import Construct

class ComputeStack(Stack):
    """
    ComputeStack: Aprovisiona el cluster EKS sobre instancias ARM64 Graviton3.
    """
    def __init__(self, scope: Construct, construct_id: str, vpc: ec2.IVpc, kms_key: kms.IKey, redis_sg: ec2.ISecurityGroup, **kwargs) -> None:
        super().__init__(scope, construct_id, **kwargs)

        # Cluster EKS gestionado con soporte OIDC para IRSA
        self.cluster = eks.Cluster(
            self,
            "PieEksCluster",
            version=eks.KubernetesVersion.V1_29,
            vpc=vpc,
            vpc_subnets=[ec2.SubnetSelection(subnet_type=ec2.SubnetType.PRIVATE_WITH_EGRESS)],
            endpoint_access=eks.EndpointAccess.PUBLIC_AND_PRIVATE,
            default_capacity=0,
            secrets_encryption_key=kms_key
        )

        # NodeGroup administrado sobre instancias c7g (Graviton3 ARM64)
        self.nodegroup = self.cluster.add_nodegroup_capacity(
            "AppNodeGroup",
            instance_types=[ec2.InstanceType("c7g.medium")],
            min_size=2,
            max_size=6,
            desired_size=2,
            subnets=ec2.SubnetSelection(subnet_type=ec2.SubnetType.PRIVATE_WITH_EGRESS),
            ami_type=eks.NodegroupAmiType.AL2_ARM_64
        )

        # Permitir tráfico desde los nodos de EKS hacia ElastiCache Redis
        redis_sg.add_ingress_rule(
            peer=self.nodegroup.role,
            connection=ec2.Port.tcp(6379),
            description="Permitir conexion de pods EKS hacia Redis"
        )

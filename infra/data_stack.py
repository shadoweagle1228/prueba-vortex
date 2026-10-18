from aws_cdk import (
    Stack,
    aws_ec2 as ec2,
    aws_elasticache as elasticache,
    aws_rds as rds,
    aws_kms as kms,
)
from constructs import Construct

class DataStack(Stack):
    """
    DataStack: Aprovisiona ElastiCache Redis Multi-AZ y Aurora Serverless v2.
    """
    def __init__(self, scope: Construct, construct_id: str, vpc: ec2.IVpc, kms_key: kms.IKey, **kwargs) -> None:
        super().__init__(scope, construct_id, **kwargs)

        # Security Group para ElastiCache Redis
        self.redis_sg = ec2.SecurityGroup(
            self,
            "RedisSecurityGroup",
            vpc=vpc,
            description="Control de acceso para ElastiCache Redis de inventario",
            allow_all_outbound=False
        )

        # Grupo de subredes aisladas CDE para Redis
        self.redis_subnet_group = elasticache.CfnSubnetGroup(
            self,
            "RedisSubnetGroup",
            description="Subredes aisladas CDE para Redis",
            subnet_ids=[s.subnet_id for s in vpc.isolated_subnets]
        )

        # Cluster ElastiCache Redis Multi-AZ
        self.redis_cluster = elasticache.CfnReplicationGroup(
            self,
            "StockRedisCluster",
            replication_group_description="Redis Cache Multi-AZ para Stock y Catálogo",
            engine="redis",
            cache_node_type="cache.t4g.medium",
            num_cache_clusters=2,
            automatic_failover_enabled=True,
            multi_az_enabled=True,
            transit_encryption_enabled=True,
            at_rest_encryption_enabled=True,
            kms_key_id=kms_key.key_id,
            cache_subnet_group_name=self.redis_subnet_group.ref,
            security_group_ids=[self.redis_sg.security_group_id]
        )

        # Aurora PostgreSQL Serverless v2 para Keycloak y persistencia
        self.aurora_cluster = rds.DatabaseCluster(
            self,
            "PieAuroraCluster",
            engine=rds.DatabaseClusterEngine.aurora_postgres(
                version=rds.AuroraPostgresEngineVersion.VER_16_1
            ),
            vpc=vpc,
            vpc_subnets=ec2.SubnetSelection(subnet_type=ec2.SubnetType.PRIVATE_ISOLATED),
            serverless_v2_min_capacity=0.5,
            serverless_v2_max_capacity=4.0,
            storage_encryption_key=kms_key,
            default_database_name="andes_pie"
        )

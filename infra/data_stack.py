from aws_cdk import Stack, Duration, RemovalPolicy
from aws_cdk.aws_ec2 import Vpc, SubnetSelection, SubnetType
from aws_cdk.aws_elasticache import (
    CfnCacheCluster,
    CfnSubnetGroup,
    CfnReplicationGroup,
)
from aws_cdk.aws_rds import (
    DatabaseCluster,
    DatabaseClusterEngine,
    AuroraMysqlEngineVersion,
    ServerlessV2ScalingConfiguration,
)
from aws_cdk.aws_kms import Key
from constructs import Construct


class DataStack(Stack):
    def __init__(self, scope: Construct, construct_id: str, vpc: Vpc, **kwargs):
        super().__init__(scope, construct_id, **kwargs)

        redis_subnet_group = CfnSubnetGroup(
            self,
            "RedisSubnetGroup",
            description="Subnet group for ElastiCache Redis",
            subnet_ids=vpc.select_subnets(
                subnet_type=SubnetType.PRIVATE_ISOLATED
            ).subnet_ids,
        )

        CfnReplicationGroup(
            self,
            "RedisReplicationGroup",
            replication_group_id="andes-retail-redis",
            description="Redis Multi-AZ",
            num_cache_clusters=2,
            engine="redis",
            engine_version="7.1",
            cache_node_type="cache.t4g.small",
            cache_subnet_group_name=redis_subnet_group.ref,
            automatic_failover_enabled=True,
            multi_az_enabled=True,
        )

        kms_key = Key(self, "DataKey", removal_policy=RemovalPolicy.DESTROY)

        DatabaseCluster(
            self,
            "Aurora",
            engine=DatabaseClusterEngine.aurora_mysql(
                version=AuroraMysqlEngineVersion.VER_3_07_1
            ),
            vpc=vpc,
            vpc_subnets=SubnetSelection(subnet_type=SubnetType.PRIVATE_ISOLATED),
            serverless_v2_min_capacity=0.5,
            serverless_v2_max_capacity=2,
            scaling_configuration=ServerlessV2ScalingConfiguration(
                min_capacity=0.5,
                max_capacity=2,
            ),
            storage_encrypted=True,
            storage_encryption_key=kms_key,
            removal_policy=RemovalPolicy.SNAPSHOT,
        )

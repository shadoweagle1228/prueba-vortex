from aws_cdk import Stack, RemovalPolicy
from aws_cdk.aws_ec2 import Vpc, SubnetSelection, SubnetType
from aws_cdk.aws_elasticache import CfnReplicationGroup, CfnSubnetGroup
from aws_cdk.aws_kms import IKey
from aws_cdk.aws_rds import (
    AuroraPostgresEngineVersion,
    ClusterInstance,
    DatabaseCluster,
    DatabaseClusterEngine,
)
from constructs import Construct


class DataStack(Stack):
    """Capa de datos: Redis (ElastiCache) Multi-AZ y Aurora PostgreSQL Serverless v2.

    Ambos recursos viven en subredes aisladas (sin ruta a Internet) y se cifran
    en reposo con la clave KMS de la plataforma. Redis exige TLS en tránsito
    (NFR-06) y responde al presupuesto de latencia de la caché de stock (NFR-01).
    """

    def __init__(self, scope: Construct, construct_id: str, vpc: Vpc, kms_key: IKey, **kwargs):
        super().__init__(scope, construct_id, **kwargs)

        isolated = vpc.select_subnets(subnet_type=SubnetType.PRIVATE_ISOLATED)

        redis_subnet_group = CfnSubnetGroup(
            self,
            "RedisSubnetGroup",
            description="Subredes aisladas para ElastiCache Redis",
            subnet_ids=isolated.subnet_ids,
        )

        redis = CfnReplicationGroup(
            self,
            "RedisReplicationGroup",
            replication_group_id="andes-retail-redis",
            replication_group_description="Redis Multi-AZ para cache de stock",
            num_cache_clusters=2,
            engine="redis",
            engine_version="7.1",
            # Tipo de nodo del diseño de infraestructura (1 primario + 1 réplica Multi-AZ).
            cache_node_type="cache.t4g.medium",
            cache_subnet_group_name=redis_subnet_group.ref,
            automatic_failover_enabled=True,
            multi_az_enabled=True,
            at_rest_encryption_enabled=True,
            kms_key_id=kms_key.key_id,
            transit_encryption_enabled=True,
            transit_encryption_mode="preferred",
        )
        self.redis_endpoint = redis.attr_primary_end_point_address

        # Aurora PostgreSQL 16 Serverless v2 (0.5-4 ACU) en subredes aisladas (CDE).
        self.aurora_cluster = DatabaseCluster(
            self,
            "Aurora",
            engine=DatabaseClusterEngine.aurora_postgres(
                version=AuroraPostgresEngineVersion.VER_16_1
            ),
            writer=ClusterInstance.serverless_v2("AuroraWriter"),
            vpc=vpc,
            vpc_subnets=SubnetSelection(subnet_type=SubnetType.PRIVATE_ISOLATED),
            serverless_v2_min_capacity=0.5,
            serverless_v2_max_capacity=4,
            storage_encrypted=True,
            storage_encryption_key=kms_key,
            removal_policy=RemovalPolicy.RETAIN,
        )
        self.aurora_endpoint = self.aurora_cluster.cluster_endpoint.hostname

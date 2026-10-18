from aws_cdk import Stack
from aws_cdk.aws_ec2 import (
    InstanceClass,
    InstanceSize,
    InstanceType,
    Port,
    SubnetSelection,
    SubnetType,
    Vpc,
)
from aws_cdk.aws_eks import (
    Addon,
    CapacityType,
    Cluster,
    KubernetesVersion,
    Nodegroup,
)
from aws_cdk.aws_iam import ManagedPolicy, Role, ServicePrincipal
from aws_cdk.aws_rds import DatabaseCluster
from aws_cdk.lambda_layer_kubectl_v29 import KubectlV29Layer
from constructs import Construct


class ComputeStack(Stack):
    """Plano de cómputo y plataforma: EKS + Kong + Keycloak + External Secrets.

    Kong Ingress Controller es el punto de aplicación del rate limiting por canal
    (NFR-11) y el único camino de entrada al servicio. Keycloak 24 es el
    proveedor OIDC multi-tenant que valida los JWT (FR-11, US-08); usa la Aurora
    PostgreSQL del stack de datos como almacén.
    """

    def __init__(self, scope: Construct, construct_id: str, vpc: Vpc, aurora: DatabaseCluster, **kwargs):
        super().__init__(scope, construct_id, **kwargs)

        cluster = Cluster(
            self,
            "EKSCluster",
            version=KubernetesVersion.V1_29,
            vpc=vpc,
            vpc_subnets=[SubnetSelection(subnet_type=SubnetType.PRIVATE_WITH_EGRESS)],
            default_capacity=0,
            kubectl_layer=KubectlV29Layer(self, "KubectlLayer"),
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
                    "AmazonECRReadOnlyAccess"
                ),
            ],
        )

        Nodegroup(
            self,
            "AppNodegroup",
            cluster=cluster,
            capacity_type=CapacityType.ON_DEMAND,
            # Graviton3 ARM64 según el diseño de infraestructura.
            instance_types=[InstanceType.of(InstanceClass.C7G, InstanceSize.MEDIUM)],
            min_size=2,
            max_size=6,
            desired_size=2,
            node_role=nodegroup_role,
        )

        # El HPA por CPU (NFR-03) necesita el addon metrics-server.
        Addon(self, "MetricsServer", cluster=cluster, addon_name="metrics-server")

        # --- Rate limiting perimetral (NFR-11) ---------------------------------
        cluster.add_helm_chart(
            "KongIngress",
            chart="ingress",
            release="kong",
            repository="https://charts.konghq.com",
            namespace="kong",
            create_namespace=True,
            values={
                "replicaCount": 2,
                "ingressController": {"installCRDs": True},
                "proxy": {"type": "LoadBalancer"},
                "env": {"database": "off"},
            },
        )

        # ─── Identidad multi-tenant (FR-11, US-08) ───────────────────────────
        identity = cluster.add_helm_chart(
            "Keycloak",
            chart="keycloak",
            release="keycloak",
            repository="https://charts.bitnami.com/bitnami",
            namespace="identity",
            create_namespace=True,
            values={
                "replicaCount": 2,
                "production": True,
                "proxy": "edge",
                # El almacén es la Aurora PostgreSQL del stack de datos, no un Postgres efímero.
                "postgresql": {"enabled": False},
                "externalDatabase": {
                    "host": aurora.cluster_endpoint.hostname,
                    "port": 5432,
                    "user": "keycloak",
                    "database": "keycloak",
                    "existingSecret": "keycloak-database",
                    "existingSecretPasswordKey": "password",
                },
                "auth": {
                    "adminUser": "admin",
                    "existingSecret": "keycloak-admin",
                },
                "service": {"type": "ClusterIP"},
            },
        )
        # El plano de identidad es la única carga que puede alcanzar la CDE. La regla
        # se crea sobre el security group del cluster (origen) para no invertir la
        # dirección de la dependencia entre stacks.
        cluster.connections.allow_to(
            aurora, Port.tcp(5432), "Keycloak hacia Aurora PostgreSQL"
        )

        # ─── External Secrets Operator (sincroniza KMS → Secret de K8s) ──────
        cluster.add_helm_chart(
            "ExternalSecrets",
            chart="external-secrets",
            release="external-secrets",
            repository="https://charts.external-secrets.io",
            namespace="external-secrets",
            create_namespace=True,
            values={"installCRDs": True},
        )

        self.cluster = cluster
        self.identity_release = identity

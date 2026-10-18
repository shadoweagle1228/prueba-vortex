from aws_cdk import (
    Stack,
    aws_kms as kms,
    aws_secretsmanager as sm,
    RemovalPolicy,
)
from constructs import Construct

class SecurityStack(Stack):
    """
    SecurityStack: Gestiona las llaves criptográficas KMS y secretos de la PIE.
    """
    def __init__(self, scope: Construct, construct_id: str, **kwargs) -> None:
        super().__init__(scope, construct_id, **kwargs)

        # Llave KMS administrada para cifrado en reposo (Redis, Aurora, Secrets)
        self.kms_key = kms.Key(
            self,
            "PieKmsKey",
            description="Llave de cifrado KMS para la plataforma PIE Andes Retail",
            enable_key_rotation=True,
            removal_policy=RemovalPolicy.RETAIN
        )

        # Secreto gestionado para credenciales SAP ERP
        self.sap_secret = sm.Secret(
            self,
            "SapErpSecret",
            description="Credenciales de acceso a SAP ERP on-premise",
            encryption_key=self.kms_key,
            generate_secret_string=sm.SecretStringGenerator(
                secret_string_template='{"username": "sap_pie_user"}',
                generate_string_key="password",
                exclude_punctuation=True
            )
        )

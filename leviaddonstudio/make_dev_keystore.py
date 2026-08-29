from datetime import datetime, timezone
from hashlib import sha256
from pathlib import Path

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.serialization import pkcs12
from cryptography.x509.oid import NameOID

OUT = Path("leviaddonstudio-dev.p12")
PASSWORD = b"android"
ALIAS = b"androiddebugkey"

# Public development-only seed. This key is intentionally NOT a production secret.
seed = sha256(b"LeviAddonStudio-development-signing-v1").digest()
order = int("FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551", 16)
private_value = (int.from_bytes(seed, "big") % (order - 1)) + 1
key = ec.derive_private_key(private_value, ec.SECP256R1())

name = x509.Name([
    x509.NameAttribute(NameOID.COMMON_NAME, "LeviAddonStudio Development"),
    x509.NameAttribute(NameOID.ORGANIZATION_NAME, "Victor Development"),
    x509.NameAttribute(NameOID.COUNTRY_NAME, "BR"),
])

serial = int.from_bytes(sha256(b"LeviAddonStudio-development-cert-v1").digest()[:16], "big")
cert = (
    x509.CertificateBuilder()
    .subject_name(name)
    .issuer_name(name)
    .public_key(key.public_key())
    .serial_number(serial)
    .not_valid_before(datetime(2026, 1, 1, tzinfo=timezone.utc))
    .not_valid_after(datetime(2053, 5, 18, tzinfo=timezone.utc))
    .add_extension(x509.BasicConstraints(ca=False, path_length=None), critical=True)
    .sign(key, hashes.SHA256())
)

OUT.write_bytes(pkcs12.serialize_key_and_certificates(
    name=ALIAS,
    key=key,
    cert=cert,
    cas=None,
    encryption_algorithm=serialization.BestAvailableEncryption(PASSWORD),
))

print(OUT)

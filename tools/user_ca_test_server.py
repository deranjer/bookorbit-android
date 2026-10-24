"""Loopback-only TLS fixture for the BookOrbit user-CA setup screen.

Requires Python 3.10+ and cryptography. Does not accept logins or proxy real credentials.
Use adb reverse tcp:8443 tcp:8443 (and 8444/8445) to reach it from a USB device.
"""

import argparse
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import ipaddress
import json
from pathlib import Path
import ssl
import threading

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.x509.oid import ExtendedKeyUsageOID, NameOID


def make_certificates(output: Path):
    output.mkdir(parents=True, exist_ok=True)
    now = datetime.now(timezone.utc)
    root_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    root_name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "BookOrbit temporary test CA")])
    root = (x509.CertificateBuilder().subject_name(root_name).issuer_name(root_name)
            .public_key(root_key.public_key()).serial_number(x509.random_serial_number())
            .not_valid_before(now - timedelta(minutes=5)).not_valid_after(now + timedelta(days=2))
            .add_extension(x509.BasicConstraints(ca=True, path_length=0), critical=True)
            .add_extension(x509.SubjectKeyIdentifier.from_public_key(root_key.public_key()), critical=False)
            .add_extension(x509.AuthorityKeyIdentifier.from_issuer_public_key(root_key.public_key()), critical=False)
            .add_extension(x509.KeyUsage(digital_signature=True, content_commitment=False,
                key_encipherment=False, data_encipherment=False, key_agreement=False,
                key_cert_sign=True, crl_sign=True, encipher_only=None, decipher_only=None), critical=True)
            .sign(root_key, hashes.SHA256()))
    (output / "bookorbit-test-ca.crt").write_bytes(root.public_bytes(serialization.Encoding.PEM))
    for name in ("valid", "wrong-host", "expired"):
        key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        hosts = [x509.DNSName("wrong-host.invalid")] if name == "wrong-host" else [
            x509.DNSName("localhost"), x509.IPAddress(ipaddress.ip_address("127.0.0.1"))]
        leaf = (x509.CertificateBuilder().subject_name(x509.Name([
                x509.NameAttribute(NameOID.COMMON_NAME, name)]))
                .issuer_name(root_name).public_key(key.public_key()).serial_number(x509.random_serial_number())
                .not_valid_before(now - timedelta(days=1) if name == "expired" else now - timedelta(minutes=5))
                .not_valid_after(now - timedelta(hours=1) if name == "expired" else now + timedelta(days=1))
                .add_extension(x509.BasicConstraints(ca=False, path_length=None), critical=True)
                .add_extension(x509.SubjectKeyIdentifier.from_public_key(key.public_key()), critical=False)
                .add_extension(x509.AuthorityKeyIdentifier.from_issuer_public_key(root_key.public_key()), critical=False)
                .add_extension(x509.KeyUsage(digital_signature=True, content_commitment=False,
                    key_encipherment=True, data_encipherment=False, key_agreement=False,
                    key_cert_sign=False, crl_sign=False, encipher_only=None, decipher_only=None), critical=True)
                .add_extension(x509.SubjectAlternativeName(hosts), critical=False)
                .add_extension(x509.ExtendedKeyUsage([ExtendedKeyUsageOID.SERVER_AUTH]), critical=False)
                .sign(root_key, hashes.SHA256()))
        (output / f"{name}.pem").write_bytes(leaf.public_bytes(serialization.Encoding.PEM))
        (output / f"{name}.key").write_bytes(key.private_bytes(serialization.Encoding.PEM,
            serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
    # The issuer's private key is intentionally never written to disk.


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        payload = {"needsSetup": False} if self.path == "/api/v1/auth/setup-status" else []
        body = json.dumps(payload).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_POST(self):
        self.send_error(403, "TLS fixture only; do not enter real credentials")

    def log_message(self, *_):
        pass  # Do not log URLs, request bodies or credentials.


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=Path("build/user-ca-fixture"))
    parser.add_argument("--generate-only", action="store_true")
    parser.add_argument("--regenerate", action="store_true", help="Creates a new CA; reinstall its certificate on Android")
    args = parser.parse_args()
    if args.regenerate or not (args.output / "bookorbit-test-ca.crt").exists():
        make_certificates(args.output)
    print(f"Android CA installer file: {(args.output / 'bookorbit-test-ca.crt').resolve()}", flush=True)
    if args.generate_only:
        return
    servers = []
    try:
        for port, name in ((8443, "valid"), (8444, "wrong-host"), (8445, "expired")):
            server = ThreadingHTTPServer(("127.0.0.1", port), Handler)
            servers.append(server)
            tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
            tls.load_cert_chain(args.output / f"{name}.pem", args.output / f"{name}.key")
            server.socket = tls.wrap_socket(server.socket, server_side=True)
            threading.Thread(target=server.serve_forever, daemon=True).start()
            print(f"https://localhost:{port} — {name}", flush=True)
        print("Fixture only: reaching the login screen is success. Do not enter real credentials.", flush=True)
        threading.Event().wait()
    except KeyboardInterrupt:
        pass
    finally:
        for server in servers:
            server.server_close()


if __name__ == "__main__":
    main()

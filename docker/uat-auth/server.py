"""Loopback UAT-only RS256 issuer. Generates an ephemeral key on every start."""
import base64, json, os, struct, subprocess, tempfile, time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

ISSUER = os.environ.get("UAT_OIDC_ISSUER", "http://uat-auth:8080")
AUDIENCE = os.environ.get("UAT_OIDC_AUDIENCE", "online-shop-uat")
SUBJECT = os.environ.get("UAT_OIDC_SUBJECT", "synthetic-admin")
KEY_ID = "ephemeral-uat"
directory = tempfile.mkdtemp(prefix="uat-oidc-")
private_key = os.path.join(directory, "private.pem")
public_der = os.path.join(directory, "public.der")
subprocess.run(["openssl", "genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:2048", "-out", private_key], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
subprocess.run(["openssl", "pkey", "-in", private_key, "-pubout", "-outform", "DER", "-out", public_der], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

def read_length(data, offset):
    value = data[offset]; offset += 1
    if value < 128: return value, offset
    count = value & 127
    return int.from_bytes(data[offset:offset + count], "big"), offset + count

def read_tlv(data, offset, tag):
    if data[offset] != tag: raise ValueError("unexpected DER tag")
    length, start = read_length(data, offset + 1)
    return data[start:start + length], start + length

spki = open(public_der, "rb").read()
outer, _ = read_tlv(spki, 0, 0x30)
_, pos = read_tlv(outer, 0, 0x30)
bits, _ = read_tlv(outer, pos, 0x03)
rsa, _ = read_tlv(bits[1:], 0, 0x30)
modulus, pos = read_tlv(rsa, 0, 0x02)
exponent, _ = read_tlv(rsa, pos, 0x02)

def b64(value): return base64.urlsafe_b64encode(value).rstrip(b"=").decode()
JWKS = {"keys": [{"kty": "RSA", "use": "sig", "alg": "RS256", "kid": KEY_ID,
                    "n": b64(modulus.lstrip(b"\x00")), "e": b64(exponent.lstrip(b"\x00"))}]}

def token():
    now = int(time.time())
    header = b64(json.dumps({"alg":"RS256","typ":"JWT","kid":KEY_ID}, separators=(",", ":")).encode())
    payload = b64(json.dumps({"iss":ISSUER,"sub":SUBJECT,"aud":AUDIENCE,"iat":now,"nbf":now-1,"exp":now+600}, separators=(",", ":")).encode())
    signing_input = f"{header}.{payload}".encode()
    signature = subprocess.run(["openssl", "dgst", "-sha256", "-sign", private_key], input=signing_input, check=True, capture_output=True).stdout
    return f"{header}.{payload}.{b64(signature)}"

class Handler(BaseHTTPRequestHandler):
    def reply(self, status, body, content_type="application/json"):
        encoded = body.encode(); self.send_response(status); self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(encoded))); self.end_headers(); self.wfile.write(encoded)
    def do_GET(self):
        if self.path == "/health": self.reply(200, "ok", "text/plain")
        elif self.path == "/jwks": self.reply(200, json.dumps(JWKS, separators=(",", ":")))
        elif self.path == "/token": self.reply(200, json.dumps({"access_token":token(),"token_type":"Bearer","expires_in":600}, separators=(",", ":")))
        else: self.reply(404, json.dumps({"error":"not_found"}))
    def log_message(self, fmt, *args): pass

ThreadingHTTPServer(("0.0.0.0", 8080), Handler).serve_forever()

// Device tokens: `<deviceId>.<hmac>`. Stateless to verify; the HMAC key never leaves the Worker.
// v1 trusts /register. Play Integrity attestation should gate it before a public launch.

const enc = new TextEncoder();

async function hmacKey(secret: string): Promise<CryptoKey> {
  return crypto.subtle.importKey("raw", enc.encode(secret), { name: "HMAC", hash: "SHA-256" }, false, [
    "sign",
    "verify",
  ]);
}

const b64url = (buf: ArrayBuffer) =>
  btoa(String.fromCharCode(...new Uint8Array(buf)))
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");

export async function issueToken(secret: string): Promise<string> {
  const deviceId = crypto.randomUUID();
  const sig = await crypto.subtle.sign("HMAC", await hmacKey(secret), enc.encode(deviceId));
  return `${deviceId}.${b64url(sig)}`;
}

/** Returns the device id, or null if the token is invalid. */
export async function verifyToken(secret: string, header: string | null): Promise<string | null> {
  const token = header?.replace(/^Bearer\s+/i, "") ?? "";
  const dot = token.indexOf(".");
  if (dot <= 0) return null;
  const deviceId = token.slice(0, dot);
  const expected = await crypto.subtle.sign("HMAC", await hmacKey(secret), enc.encode(deviceId));
  return b64url(expected) === token.slice(dot + 1) ? deviceId : null;
}

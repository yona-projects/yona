import { createHmac } from 'crypto';

/** RFC 4648 base32 decode (no padding required) -- just enough for RFC 6238 secrets, which are
 * always uppercase A-Z2-7. No external dependency; TotpCodeVerifier.kt confirms the product side
 * uses SHA1/6 digits/30s period (dev.samstevens.totp defaults), matched here. */
function base32Decode(base32: string): Buffer {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
  const clean = base32.toUpperCase().replace(/=+$/, '');
  let bits = '';
  for (const char of clean) {
    const value = alphabet.indexOf(char);
    if (value === -1) throw new Error(`Invalid base32 character: ${char}`);
    bits += value.toString(2).padStart(5, '0');
  }
  const bytes: number[] = [];
  for (let i = 0; i + 8 <= bits.length; i += 8) {
    bytes.push(parseInt(bits.slice(i, i + 8), 2));
  }
  return Buffer.from(bytes);
}

/** RFC 6238 TOTP over RFC 4226 HOTP (HMAC-SHA1), matching TotpCodeVerifier.kt's
 * dev.samstevens.totp DefaultCodeGenerator defaults (SHA1, 6 digits, 30s period). */
export function computeTotp(base32Secret: string, timeStepSeconds = 30, digits = 6, atUnixSeconds = Date.now() / 1000): string {
  const key = base32Decode(base32Secret);
  const counter = Math.floor(atUnixSeconds / timeStepSeconds);

  const counterBuffer = Buffer.alloc(8);
  counterBuffer.writeUInt32BE(Math.floor(counter / 0x100000000), 0);
  counterBuffer.writeUInt32BE(counter >>> 0, 4);

  const hmac = createHmac('sha1', key).update(counterBuffer).digest();
  const offset = hmac[hmac.length - 1] & 0x0f;
  const binary =
    ((hmac[offset] & 0x7f) << 24) |
    ((hmac[offset + 1] & 0xff) << 16) |
    ((hmac[offset + 2] & 0xff) << 8) |
    (hmac[offset + 3] & 0xff);

  const code = (binary % 10 ** digits).toString().padStart(digits, '0');
  return code;
}

/**
 * Constant-time string comparison for secret/token checks.
 *
 * Hashes both inputs to a fixed 32-byte SHA-256 digest before comparing, so:
 *  - no early return on length mismatch (which would leak the expected length via timing), and
 *  - crypto.subtle.timingSafeEqual never throws (it requires equal-length buffers).
 */
export async function timingSafeEqualStr(a: string, b: string): Promise<boolean> {
  const enc = new TextEncoder();
  const [ah, bh] = await Promise.all([
    crypto.subtle.digest("SHA-256", enc.encode(a)),
    crypto.subtle.digest("SHA-256", enc.encode(b)),
  ]);
  return crypto.subtle.timingSafeEqual(ah, bh);
}

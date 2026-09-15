import http from 'node:http';
import crypto from 'node:crypto';
import { URL } from 'node:url';
import pg from 'pg';

const { Pool } = pg;
const port = Number(process.env.PORT || 10000);
const publicBaseUrl = (process.env.PUBLIC_BASE_URL || '').replace(/\/$/, '');
const sessionTtlSeconds = Number(process.env.LIVE_SHARE_TTL_SECONDS || 7200);
const databaseUrl = process.env.DATABASE_URL;

if (!databaseUrl) throw new Error('DATABASE_URL is required');

const pool = new Pool({ connectionString: databaseUrl, ssl: { rejectUnauthorized: false } });

async function migrate() {
  await pool.query(`
    CREATE TABLE IF NOT EXISTS live_share_sessions (
      id TEXT PRIMARY KEY,
      token_hash TEXT NOT NULL UNIQUE,
      expires_at TIMESTAMPTZ NOT NULL,
      revoked_at TIMESTAMPTZ,
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      latitude DOUBLE PRECISION,
      longitude DOUBLE PRECISION,
      bearing DOUBLE PRECISION,
      speed_kmh DOUBLE PRECISION,
      eta_seconds INTEGER
    );
    CREATE INDEX IF NOT EXISTS live_share_sessions_expiry_idx ON live_share_sessions (expires_at);
  `);
}

const json = (res, status, body) => {
  res.writeHead(status, {
    'content-type': 'application/json; charset=utf-8',
    'cache-control': 'no-store',
    'access-control-allow-origin': '*',
    'access-control-allow-headers': 'content-type, authorization',
    'access-control-allow-methods': 'GET,POST,PUT,DELETE,OPTIONS'
  });
  res.end(JSON.stringify(body));
};

const readJson = async (req) => {
  const chunks = [];
  for await (const chunk of req) chunks.push(chunk);
  if (!chunks.length) return {};
  return JSON.parse(Buffer.concat(chunks).toString('utf8'));
};

const hashToken = (token) => crypto.createHash('sha256').update(token).digest('hex');
const newToken = () => crypto.randomBytes(32).toString('base64url');
const newId = () => crypto.randomBytes(12).toString('base64url');

function validCoordinate(value, min, max) {
  return typeof value === 'number' && Number.isFinite(value) && value >= min && value <= max;
}

function validNumber(value, min, max) {
  return value == null || (typeof value === 'number' && Number.isFinite(value) && value >= min && value <= max);
}

async function cleanupExpired() {
  await pool.query('DELETE FROM live_share_sessions WHERE expires_at < NOW() OR revoked_at IS NOT NULL');
}

async function authenticate(req, sessionId) {
  const header = req.headers.authorization || '';
  const token = header.startsWith('Bearer ') ? header.slice(7).trim() : '';
  if (!token) return null;
  const result = await pool.query(
    'SELECT * FROM live_share_sessions WHERE id = $1 AND token_hash = $2 AND revoked_at IS NULL AND expires_at > NOW()',
    [sessionId, hashToken(token)]
  );
  return result.rows[0] || null;
}

async function handle(req, res) {
  if (req.method === 'OPTIONS') return json(res, 204, {});
  const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  const parts = url.pathname.split('/').filter(Boolean);

  if (req.method === 'GET' && url.pathname === '/health') {
    return json(res, 200, { ok: true, service: 'lanu-harita-live-share' });
  }

  if (req.method === 'GET' && parts[0] === 'share' && parts[1]) {
    const id = parts[1];
    const token = url.searchParams.get('token') || '';
    const result = await pool.query(
      'SELECT id, expires_at, revoked_at, latitude, longitude, bearing, speed_kmh, eta_seconds, updated_at FROM live_share_sessions WHERE id = $1 AND token_hash = $2 AND revoked_at IS NULL AND expires_at > NOW()',
      [id, hashToken(token)]
    );
    if (!result.rows[0]) return json(res, 404, { error: 'share_session_not_found' });
    return json(res, 200, { session: result.rows[0] });
  }

  if (req.method === 'POST' && url.pathname === '/v1/sessions') {
    const body = await readJson(req);
    if (!validCoordinate(body.latitude, -90, 90) || !validCoordinate(body.longitude, -180, 180)) {
      return json(res, 400, { error: 'invalid_initial_location' });
    }
    const id = newId();
    const token = newToken();
    const expiresAt = new Date(Date.now() + sessionTtlSeconds * 1000);
    await pool.query(
      `INSERT INTO live_share_sessions (id, token_hash, expires_at, latitude, longitude, bearing, speed_kmh, eta_seconds)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8)`,
      [id, hashToken(token), expiresAt, body.latitude, body.longitude, body.bearing ?? null, body.speedKmh ?? null, body.etaSeconds ?? null]
    );
    const viewerUrl = `${publicBaseUrl}/share/${encodeURIComponent(id)}?token=${encodeURIComponent(token)}`;
    return json(res, 201, { sessionId: id, token, expiresAt: expiresAt.toISOString(), viewerUrl });
  }

  if (parts[0] === 'v1' && parts[1] === 'sessions' && parts[2]) {
    const id = parts[2];
    const session = await authenticate(req, id);
    if (!session) return json(res, 401, { error: 'invalid_or_expired_share_token' });

    if (req.method === 'PUT' && parts.length === 3) {
      const body = await readJson(req);
      if (!validCoordinate(body.latitude, -90, 90) || !validCoordinate(body.longitude, -180, 180) ||
          !validNumber(body.bearing, 0, 360) || !validNumber(body.speedKmh, 0, 400) ||
          !validNumber(body.etaSeconds, 0, 172800)) {
        return json(res, 400, { error: 'invalid_location_payload' });
      }
      await pool.query(
        `UPDATE live_share_sessions
         SET latitude=$2, longitude=$3, bearing=$4, speed_kmh=$5, eta_seconds=$6, updated_at=NOW()
         WHERE id=$1`,
        [id, body.latitude, body.longitude, body.bearing ?? null, body.speedKmh ?? null, body.etaSeconds ?? null]
      );
      return json(res, 200, { ok: true });
    }

    if (req.method === 'GET' && parts.length === 3) {
      return json(res, 200, {
        sessionId: session.id,
        expiresAt: session.expires_at,
        latitude: session.latitude,
        longitude: session.longitude,
        bearing: session.bearing,
        speedKmh: session.speed_kmh,
        etaSeconds: session.eta_seconds,
        updatedAt: session.updated_at
      });
    }

    if (req.method === 'DELETE' && parts.length === 3) {
      await pool.query('UPDATE live_share_sessions SET revoked_at=NOW(), updated_at=NOW() WHERE id=$1', [id]);
      return json(res, 200, { ok: true });
    }
  }

  return json(res, 404, { error: 'not_found' });
}

await migrate();
setInterval(() => cleanupExpired().catch((error) => console.error('cleanup', error)), 60_000).unref();

http.createServer((req, res) => handle(req, res).catch((error) => {
  console.error(error);
  json(res, 500, { error: 'internal_server_error' });
})).listen(port, '0.0.0.0', () => console.log(`LANU live-share listening on ${port}`));

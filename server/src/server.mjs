import http from 'node:http';
import crypto from 'node:crypto';
import { URL } from 'node:url';

const port = Number(process.env.PORT || 10000);
const publicBaseUrl = (process.env.PUBLIC_BASE_URL || '').replace(/\/$/, '');
const sessionTtlSeconds = Number(process.env.LIVE_SHARE_TTL_SECONDS || 7200);
const sessions = new Map();

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
const now = () => Date.now();

function validCoordinate(value, min, max) {
  return typeof value === 'number' && Number.isFinite(value) && value >= min && value <= max;
}

function validNumber(value, min, max) {
  return value == null || (typeof value === 'number' && Number.isFinite(value) && value >= min && value <= max);
}

function activeSession(id, tokenHash) {
  const session = sessions.get(id);
  if (!session || session.tokenHash !== tokenHash || session.revokedAt || session.expiresAt <= now()) return null;
  return session;
}

function cleanupExpired() {
  for (const [id, session] of sessions) {
    if (session.expiresAt <= now() || session.revokedAt) sessions.delete(id);
  }
}

async function handle(req, res) {
  if (req.method === 'OPTIONS') return json(res, 204, {});
  cleanupExpired();
  const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  const parts = url.pathname.split('/').filter(Boolean);

  if (req.method === 'GET' && url.pathname === '/health') {
    return json(res, 200, { ok: true, service: 'lanu-harita-live-share', sessions: sessions.size });
  }

  if (req.method === 'POST' && url.pathname === '/v1/sessions') {
    const body = await readJson(req);
    if (!validCoordinate(body.latitude, -90, 90) || !validCoordinate(body.longitude, -180, 180)) {
      return json(res, 400, { error: 'invalid_initial_location' });
    }
    const id = newId();
    const token = newToken();
    const session = {
      id,
      tokenHash: hashToken(token),
      expiresAt: now() + sessionTtlSeconds * 1000,
      revokedAt: null,
      latitude: body.latitude,
      longitude: body.longitude,
      bearing: body.bearing ?? null,
      speedKmh: body.speedKmh ?? null,
      etaSeconds: body.etaSeconds ?? null,
      updatedAt: now()
    };
    sessions.set(id, session);
    const viewerUrl = `${publicBaseUrl}/share/${encodeURIComponent(id)}?token=${encodeURIComponent(token)}`;
    return json(res, 201, { sessionId: id, token, expiresAt: new Date(session.expiresAt).toISOString(), viewerUrl });
  }

  if (req.method === 'GET' && parts[0] === 'share' && parts[1]) {
    const id = parts[1];
    const token = url.searchParams.get('token') || '';
    const session = activeSession(id, hashToken(token));
    if (!session) return json(res, 404, { error: 'share_session_not_found' });
    return json(res, 200, { session: {
      sessionId: session.id,
      expiresAt: new Date(session.expiresAt).toISOString(),
      latitude: session.latitude,
      longitude: session.longitude,
      bearing: session.bearing,
      speedKmh: session.speedKmh,
      etaSeconds: session.etaSeconds,
      updatedAt: new Date(session.updatedAt).toISOString()
    }});
  }

  if (parts[0] === 'v1' && parts[1] === 'sessions' && parts[2]) {
    const id = parts[2];
    const header = req.headers.authorization || '';
    const token = header.startsWith('Bearer ') ? header.slice(7).trim() : '';
    const session = activeSession(id, hashToken(token));
    if (!session) return json(res, 401, { error: 'invalid_or_expired_share_token' });

    if (req.method === 'PUT') {
      const body = await readJson(req);
      if (!validCoordinate(body.latitude, -90, 90) || !validCoordinate(body.longitude, -180, 180) ||
          !validNumber(body.bearing, 0, 360) || !validNumber(body.speedKmh, 0, 400) ||
          !validNumber(body.etaSeconds, 0, 172800)) {
        return json(res, 400, { error: 'invalid_location_payload' });
      }
      session.latitude = body.latitude;
      session.longitude = body.longitude;
      session.bearing = body.bearing ?? null;
      session.speedKmh = body.speedKmh ?? null;
      session.etaSeconds = body.etaSeconds ?? null;
      session.updatedAt = now();
      return json(res, 200, { ok: true });
    }

    if (req.method === 'GET') {
      return json(res, 200, {
        sessionId: session.id,
        expiresAt: new Date(session.expiresAt).toISOString(),
        latitude: session.latitude,
        longitude: session.longitude,
        bearing: session.bearing,
        speedKmh: session.speedKmh,
        etaSeconds: session.etaSeconds,
        updatedAt: new Date(session.updatedAt).toISOString()
      });
    }

    if (req.method === 'DELETE') {
      session.revokedAt = now();
      sessions.delete(id);
      return json(res, 200, { ok: true });
    }
  }

  return json(res, 404, { error: 'not_found' });
}

setInterval(cleanupExpired, 60_000).unref();
http.createServer((req, res) => handle(req, res).catch((error) => {
  console.error(error);
  json(res, 500, { error: 'internal_server_error' });
})).listen(port, '0.0.0.0', () => console.log(`LANU live-share listening on ${port}`));

const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const SERVER_ID = 'b27bbaac-028f-4717-bfcd-3e78cf1475bc';
const PORT = Number(process.env.PORT || process.env.SERVER_PORT || 5063);
const TOKEN = process.env.MONKEY_API_TOKEN || 'MonkeyMischief-0.0.4-alpha-b27bbaac';
const DATA_DIR = process.env.MONKEY_DB_DIR || path.join(__dirname, '..', 'json-db');
const DB_FILE = path.join(DATA_DIR, 'database.json');
const MAX_BODY = 256 * 1024;

function log(event, data = {}) {
  console.log(JSON.stringify({ at: new Date().toISOString(), service: 'monkey-mischief-json-db', event, ...data }));
}

function meta(req, url) {
  return { method: req.method, path: url.pathname, remote: req.socket?.remoteAddress || null, userAgent: req.headers['user-agent'] || null };
}

function emptyDb(extra = {}) {
  return { schemaVersion: 2, serverId: SERVER_ID, createdAt: new Date().toISOString(), users: {}, devices: {}, states: {}, events: [], ...extra };
}

function normalizeDb(db) {
  db.schemaVersion = Math.max(Number(db.schemaVersion || 1), 2);
  db.serverId = db.serverId || SERVER_ID;
  db.createdAt = db.createdAt || new Date().toISOString();
  db.users = db.users && typeof db.users === 'object' ? db.users : {};
  db.devices = db.devices && typeof db.devices === 'object' ? db.devices : {};
  db.states = db.states && typeof db.states === 'object' ? db.states : {};
  db.events = Array.isArray(db.events) ? db.events : [];
  return db;
}

function writeDb(db) {
  fs.mkdirSync(DATA_DIR, { recursive: true });
  const normal = normalizeDb(db);
  const tmp = `${DB_FILE}.tmp`;
  fs.writeFileSync(tmp, JSON.stringify(normal, null, 2));
  fs.renameSync(tmp, DB_FILE);
  log('db_write', {
    dbFile: DB_FILE,
    userCount: Object.keys(normal.users).length,
    deviceCount: Object.keys(normal.devices).length,
    stateCount: Object.keys(normal.states).length,
    eventCount: normal.events.length
  });
}

function ensureDb() {
  fs.mkdirSync(DATA_DIR, { recursive: true });
  if (!fs.existsSync(DB_FILE)) writeDb(emptyDb());
}

function readDb() {
  ensureDb();
  try {
    const db = normalizeDb(JSON.parse(fs.readFileSync(DB_FILE, 'utf8')));
    log('db_read', {
      dbFile: DB_FILE,
      userCount: Object.keys(db.users).length,
      deviceCount: Object.keys(db.devices).length,
      stateCount: Object.keys(db.states).length,
      eventCount: db.events.length
    });
    return db;
  } catch (error) {
    const backup = `${DB_FILE}.broken-${Date.now()}`;
    fs.renameSync(DB_FILE, backup);
    const db = emptyDb({ recoveredFrom: backup });
    writeDb(db);
    log('db_recovered', { dbFile: DB_FILE, backup, error: error.message });
    return db;
  }
}

function send(res, status, payload) {
  const body = JSON.stringify(payload);
  log('response_send', { status, bytes: Buffer.byteLength(body), ok: payload?.ok, error: payload?.error });
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET,POST,PUT,OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type, X-Monkey-Token',
    'Cache-Control': 'no-store'
  });
  res.end(body);
}

function requireToken(req, res) {
  if (req.headers['x-monkey-token'] !== TOKEN) {
    log('auth_failed', { got: req.headers['x-monkey-token'] ? 'present' : 'missing' });
    send(res, 401, { ok: false, error: 'bad_token' });
    return false;
  }
  return true;
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let size = 0;
    const chunks = [];
    req.on('data', c => {
      size += c.length;
      if (size > MAX_BODY) {
        reject(new Error('body_too_large'));
        req.destroy();
        return;
      }
      chunks.push(c);
    });
    req.on('end', () => {
      try {
        const raw = Buffer.concat(chunks).toString('utf8') || '{}';
        log('body_read', { bytes: Buffer.byteLength(raw), preview: raw.slice(0, 180) });
        resolve(JSON.parse(raw));
      } catch (e) {
        log('body_invalid_json', { error: e.message });
        reject(new Error('invalid_json'));
      }
    });
    req.on('error', reject);
  });
}

function cleanId(value, fallback) {
  const cleaned = String(value || '').toLowerCase().replace(/[^a-z0-9_.-]/g, '_').slice(0, 96);
  return cleaned || fallback;
}

function userIdForDevice(deviceId) {
  return `user_${crypto.createHash('sha256').update(deviceId).digest('hex').slice(0, 16)}`;
}

function slotFromUrl(url) {
  const parts = url.pathname.split('/').filter(Boolean);
  return cleanId(decodeURIComponent(parts[2] || 'default'), 'default').slice(0, 64);
}

function linkDevice(db, payload, now) {
  const deviceId = cleanId(payload.deviceId, null);
  if (!deviceId) return { userId: cleanId(payload.userId, null), deviceId: null, isNew: false };

  const existing = db.devices[deviceId];
  const userId = existing?.userId || cleanId(payload.userId, null) || userIdForDevice(deviceId);
  const isNew = !existing;
  db.users[userId] = {
    userId,
    displayName: db.users[userId]?.displayName || null,
    createdAt: db.users[userId]?.createdAt || now,
    lastSeenAt: now,
    deviceIds: Array.from(new Set([...(db.users[userId]?.deviceIds || []), deviceId]))
  };
  db.devices[deviceId] = {
    deviceId,
    userId,
    client: payload.client || existing?.client || 'android',
    version: payload.version || existing?.version || null,
    createdAt: existing?.createdAt || now,
    lastSeenAt: now
  };
  return { userId, deviceId, isNew };
}

async function registerDevice(req, res) {
  if (!requireToken(req, res)) return;
  try {
    const payload = await readBody(req);
    const deviceId = cleanId(payload.deviceId, null);
    if (!deviceId) return send(res, 400, { ok: false, error: 'missing_device_id' });

    const now = new Date().toISOString();
    const db = readDb();
    const linked = linkDevice(db, payload, now);
    db.events.unshift({
      at: now,
      slot: `device:${linked.deviceId}`,
      reason: linked.isNew ? 'user_registered' : 'user_seen',
      userId: linked.userId,
      deviceId: linked.deviceId,
      client: payload.client || 'android',
      version: payload.version || null,
      phase: null,
      turn: null
    });
    db.events = db.events.slice(0, 200);
    writeDb(db);
    log('user_registered', { userId: linked.userId, deviceId: linked.deviceId, isNew: linked.isNew, client: payload.client || 'android', version: payload.version || null });
    return send(res, 200, { ok: true, userId: linked.userId, deviceId: linked.deviceId, isNew: linked.isNew });
  } catch (error) {
    log('user_register_failed', { error: error.message });
    return send(res, 400, { ok: false, error: error.message });
  }
}

const server = http.createServer(async (req, res) => {
  const started = Date.now();
  const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  log('request_start', meta(req, url));
  res.on('finish', () => log('request_finish', { ...meta(req, url), status: res.statusCode, durationMs: Date.now() - started }));

  if (req.method === 'OPTIONS') return send(res, 204, { ok: true });

  if (req.method === 'GET' && url.pathname === '/health') {
    const db = readDb();
    return send(res, 200, {
      ok: true,
      service: 'monkey-mischief-json-db',
      serverId: SERVER_ID,
      port: PORT,
      dbFile: DB_FILE,
      userCount: Object.keys(db.users).length,
      deviceCount: Object.keys(db.devices).length,
      stateCount: Object.keys(db.states).length,
      eventCount: db.events.length
    });
  }

  if (req.method === 'POST' && url.pathname === '/v1/users/register') {
    return registerDevice(req, res);
  }

  if (url.pathname.startsWith('/v1/game/')) {
    if (!requireToken(req, res)) return;
    const slot = slotFromUrl(url);
    const db = readDb();
    if (req.method === 'GET') {
      const state = db.states?.[slot] || null;
      log('game_get', { slot, exists: Boolean(state), userId: state?.userId || null, deviceId: state?.deviceId || null, turn: state?.turn || null, phase: state?.phase || null });
      return send(res, 200, { ok: true, slot, exists: Boolean(state), state });
    }
    if (req.method === 'POST' || req.method === 'PUT') {
      try {
        const payload = await readBody(req);
        const now = new Date().toISOString();
        const linked = linkDevice(db, payload, now);
        db.states[slot] = { ...payload, slot, userId: linked.userId, deviceId: linked.deviceId, serverId: SERVER_ID, updatedAt: now };
        db.events.unshift({
          at: now,
          slot,
          reason: payload.reason || 'sync',
          userId: linked.userId,
          deviceId: linked.deviceId,
          turn: payload.turn ?? null,
          phase: payload.phase || null,
          playerActive: payload.player?.active?.name || null,
          aiActive: payload.ai?.active?.name || null
        });
        db.events = db.events.slice(0, 200);
        writeDb(db);
        log('game_saved', { slot, userId: linked.userId, deviceId: linked.deviceId, updatedAt: now, stateCount: Object.keys(db.states).length, eventCount: db.events.length });
        return send(res, 200, { ok: true, slot, userId: linked.userId, deviceId: linked.deviceId, updatedAt: now });
      } catch (error) {
        log('game_save_failed', { slot, error: error.message });
        return send(res, 400, { ok: false, error: error.message });
      }
    }
  }

  if (req.method === 'GET' && url.pathname === '/v1/events') {
    if (!requireToken(req, res)) return;
    const db = readDb();
    return send(res, 200, { ok: true, events: db.events.slice(0, 50) });
  }

  send(res, 404, { ok: false, error: 'not_found' });
});

process.on('uncaughtException', error => log('uncaught_exception', { error: error.stack || error.message }));
process.on('unhandledRejection', error => log('unhandled_rejection', { error: error?.stack || error?.message || String(error) }));
ensureDb();
log('server_boot', { serverId: SERVER_ID, port: PORT, dataDir: DATA_DIR, dbFile: DB_FILE });
server.listen(PORT, '0.0.0.0', () => log('server_listening', { port: PORT, dbFile: DB_FILE }));

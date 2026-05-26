const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const SERVER_ID = 'b27bbaac-028f-4717-bfcd-3e78cf1475bc';
const PORT = Number(process.env.PORT || process.env.SERVER_PORT || 5063);
const TOKEN = process.env.MONKEY_API_TOKEN || 'MonkeyMischief-0.0.4-alpha-b27bbaac';
const DATA_DIR = process.env.MONKEY_DB_DIR || path.join(__dirname, '..', 'db');
const DB_FILES = {
  meta: path.join(DATA_DIR, 'meta.json'),
  accounts: path.join(DATA_DIR, 'accounts.json'),
  users: path.join(DATA_DIR, 'users.json'),
  devices: path.join(DATA_DIR, 'devices.json'),
  events: path.join(DATA_DIR, 'events.json'),
  statesDir: path.join(DATA_DIR, 'states')
};
const MAX_BODY = 256 * 1024;

function log(event, data = {}) {
  console.log(JSON.stringify({ at: new Date().toISOString(), service: 'monkey-mischief-json-db', event, ...data }));
}

function meta(req, url) {
  return { method: req.method, path: url.pathname, remote: req.socket?.remoteAddress || null, userAgent: req.headers['user-agent'] || null };
}

function ensureDb() {
  fs.mkdirSync(DATA_DIR, { recursive: true });
  fs.mkdirSync(DB_FILES.statesDir, { recursive: true });
  if (!fs.existsSync(DB_FILES.meta)) writeJson(DB_FILES.meta, { schemaVersion: 4, serverId: SERVER_ID, createdAt: new Date().toISOString() });
  if (!fs.existsSync(DB_FILES.accounts)) writeJson(DB_FILES.accounts, {});
  if (!fs.existsSync(DB_FILES.users)) writeJson(DB_FILES.users, {});
  if (!fs.existsSync(DB_FILES.devices)) writeJson(DB_FILES.devices, {});
  if (!fs.existsSync(DB_FILES.events)) writeJson(DB_FILES.events, []);
  migrateLegacyDb();
}

function migrateLegacyDb() {
  const legacyFile = path.join(__dirname, '..', 'json-db', 'database.json');
  if (!fs.existsSync(legacyFile)) return;
  if (listStateSlots().length > 0 || Object.keys(readJson(DB_FILES.users, {})).length > 0) return;
  try {
    const legacy = JSON.parse(fs.readFileSync(legacyFile, 'utf8'));
    writeJson(DB_FILES.meta, {
      schemaVersion: 4,
      serverId: legacy.serverId || SERVER_ID,
      createdAt: legacy.createdAt || new Date().toISOString(),
      migratedFrom: legacyFile,
      migratedAt: new Date().toISOString()
    });
    writeJson(DB_FILES.accounts, legacy.accounts && typeof legacy.accounts === 'object' ? legacy.accounts : {});
    writeJson(DB_FILES.users, legacy.users && typeof legacy.users === 'object' ? legacy.users : {});
    writeJson(DB_FILES.devices, legacy.devices && typeof legacy.devices === 'object' ? legacy.devices : {});
    writeJson(DB_FILES.events, Array.isArray(legacy.events) ? legacy.events.slice(0, 200) : []);
    const states = legacy.states && typeof legacy.states === 'object' ? legacy.states : {};
    for (const [slot, state] of Object.entries(states)) writeJson(stateFile(slot), state);
    log('legacy_db_migrated', { legacyFile, stateCount: Object.keys(states).length });
  } catch (error) {
    log('legacy_db_migration_failed', { legacyFile, error: error.message });
  }
}

function readJson(file, fallback) {
  ensureParent(file);
  if (!fs.existsSync(file)) return fallback;
  try {
    return JSON.parse(fs.readFileSync(file, 'utf8'));
  } catch (error) {
    const backup = `${file}.broken-${Date.now()}`;
    fs.renameSync(file, backup);
    log('json_recovered', { file, backup, error: error.message });
    writeJson(file, fallback);
    return fallback;
  }
}

function writeJson(file, value) {
  ensureParent(file);
  const tmp = `${file}.tmp`;
  fs.writeFileSync(tmp, JSON.stringify(value, null, 2));
  fs.renameSync(tmp, file);
}

function ensureParent(file) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
}

function stateFile(slot) {
  return path.join(DB_FILES.statesDir, `${cleanId(slot, 'default')}.json`);
}

function listStateSlots() {
  fs.mkdirSync(DB_FILES.statesDir, { recursive: true });
  return fs.readdirSync(DB_FILES.statesDir)
    .filter(name => name.endsWith('.json'))
    .map(name => path.basename(name, '.json'));
}

function readDb() {
  ensureDb();
  const db = {
    meta: readJson(DB_FILES.meta, { schemaVersion: 4, serverId: SERVER_ID, createdAt: new Date().toISOString() }),
    accounts: readJson(DB_FILES.accounts, {}),
    users: readJson(DB_FILES.users, {}),
    devices: readJson(DB_FILES.devices, {}),
    events: readJson(DB_FILES.events, [])
  };
  db.meta.schemaVersion = Math.max(Number(db.meta.schemaVersion || 1), 4);
  db.meta.serverId = db.meta.serverId || SERVER_ID;
  db.meta.createdAt = db.meta.createdAt || new Date().toISOString();
  db.accounts = db.accounts && typeof db.accounts === 'object' ? db.accounts : {};
  db.users = db.users && typeof db.users === 'object' ? db.users : {};
  db.devices = db.devices && typeof db.devices === 'object' ? db.devices : {};
  db.events = Array.isArray(db.events) ? db.events : [];
  db.states = {};
  for (const slot of listStateSlots()) db.states[slot] = readJson(stateFile(slot), null);
  return db;
}

function writeDb(db) {
  ensureDb();
  writeJson(DB_FILES.meta, db.meta || { schemaVersion: 4, serverId: SERVER_ID, createdAt: new Date().toISOString() });
  writeJson(DB_FILES.accounts, db.accounts || {});
  writeJson(DB_FILES.users, db.users || {});
  writeJson(DB_FILES.devices, db.devices || {});
  writeJson(DB_FILES.events, Array.isArray(db.events) ? db.events.slice(0, 200) : []);
  for (const [slot, state] of Object.entries(db.states || {})) writeJson(stateFile(slot), state);
  log('db_write', {
    dbDir: DATA_DIR,
    userCount: Object.keys(db.users || {}).length,
    deviceCount: Object.keys(db.devices || {}).length,
    stateCount: Object.keys(db.states || {}).length,
    eventCount: (db.events || []).length
  });
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

function cleanUsername(value) {
  return cleanId(value, null)?.replace(/[_.-]+$/g, '').slice(0, 32) || null;
}

function hashPassword(password, salt) {
  return crypto.createHash('sha256').update(`${salt}:${password}`).digest('hex');
}

function userIdForUsername(username) {
  return `user_${crypto.createHash('sha256').update(username).digest('hex').slice(0, 16)}`;
}

function slotFromUrl(url) {
  const parts = url.pathname.split('/').filter(Boolean);
  return cleanId(decodeURIComponent(parts[2] || 'default'), 'default').slice(0, 64);
}

function linkDevice(db, payload, now) {
  const deviceId = cleanId(payload.deviceId, null);
  if (!deviceId) return { userId: cleanId(payload.userId, null), deviceId: null, isNew: false };

  const existing = db.devices[deviceId];
  const userId = cleanId(payload.userId, null) || existing?.userId || userIdForDevice(deviceId);
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

async function authenticate(req, res, mode) {
  if (!requireToken(req, res)) return;
  try {
    const payload = await readBody(req);
    const username = cleanUsername(payload.username);
    const password = String(payload.password || '');
    if (!username || username.length < 3) return send(res, 400, { ok: false, error: 'bad_username' });
    if (password.length < 6) return send(res, 400, { ok: false, error: 'bad_password' });

    const now = new Date().toISOString();
    const db = readDb();
    const existing = db.accounts[username];
    if (mode === 'signup' && existing) return send(res, 409, { ok: false, error: 'account_exists' });
    if (mode === 'login' && !existing) return send(res, 404, { ok: false, error: 'account_missing' });

    const account = existing || {
      username,
      userId: userIdForUsername(username),
      salt: crypto.randomBytes(12).toString('hex'),
      createdAt: now
    };
    const passwordHash = hashPassword(password, account.salt);
    if (existing && existing.passwordHash !== passwordHash) return send(res, 401, { ok: false, error: 'bad_credentials' });

    account.passwordHash = passwordHash;
    account.lastLoginAt = now;
    db.accounts[username] = account;
    const linked = linkDevice(db, { ...payload, userId: account.userId }, now);
    db.users[account.userId] = {
      ...(db.users[account.userId] || {}),
      userId: account.userId,
      displayName: username,
      createdAt: db.users[account.userId]?.createdAt || now,
      lastSeenAt: now,
      deviceIds: Array.from(new Set([...(db.users[account.userId]?.deviceIds || []), linked.deviceId].filter(Boolean)))
    };
    db.events.unshift({
      at: now,
      slot: `auth:${username}`,
      reason: mode === 'signup' ? 'account_created' : 'account_login',
      userId: account.userId,
      deviceId: linked.deviceId,
      client: payload.client || 'android',
      version: payload.version || null,
      phase: null,
      turn: null
    });
    db.events = db.events.slice(0, 200);
    writeDb(db);
    return send(res, 200, { ok: true, username, userId: account.userId, deviceId: linked.deviceId });
  } catch (error) {
    log('auth_failed', { mode, error: error.message });
    return send(res, 400, { ok: false, error: error.message });
  }
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
      dbDir: DATA_DIR,
      dbFiles: DB_FILES,
      userCount: Object.keys(db.users).length,
      deviceCount: Object.keys(db.devices).length,
      stateCount: Object.keys(db.states).length,
      eventCount: db.events.length
    });
  }

  if (req.method === 'POST' && url.pathname === '/v1/users/register') {
    return registerDevice(req, res);
  }

  if (req.method === 'POST' && url.pathname === '/v1/auth/signup') {
    return authenticate(req, res, 'signup');
  }

  if (req.method === 'POST' && url.pathname === '/v1/auth/login') {
    return authenticate(req, res, 'login');
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
log('server_boot', { serverId: SERVER_ID, port: PORT, dataDir: DATA_DIR, dbFiles: DB_FILES });
server.listen(PORT, '0.0.0.0', () => log('server_listening', { port: PORT, dataDir: DATA_DIR }));

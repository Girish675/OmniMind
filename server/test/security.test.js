import test from 'node:test';
import assert from 'node:assert';
import path from 'node:path';
import fs from 'node:fs';
import { OmniMindServer } from '../server.js';

test('Security Hardening Suite', async (t) => {
    const tmpDir = path.join(process.cwd(), 'tmp_test_security_' + Date.now());
    const modelsDir = path.join(tmpDir, 'models');
    fs.mkdirSync(modelsDir, { recursive: true });
    const dbPath = path.join(tmpDir, 'test.db');

    // Create server in LAN mode with authentication token
    const secretToken = 'OmniMind-Secure-Token-789';
    const server = new OmniMindServer({
        port: 0,
        dbPath,
        modelsDir,
        lanMode: true,
        authToken: secretToken,
        allowedOrigins: ['http://trusted-client.lan', 'http://localhost:5173']
    });

    const port = await server.start();
    const baseUrl = `http://127.0.0.1:${port}`;

    await t.test('Health endpoint is accessible without authentication', async () => {
        const res = await fetch(`${baseUrl}/health`);
        assert.strictEqual(res.status, 200);
        const data = await res.json();
        assert.strictEqual(data.authRequired, true);
    });

    await t.test('Protected endpoints reject unauthenticated requests in LAN mode (401)', async () => {
        const res = await fetch(`${baseUrl}/models`);
        assert.strictEqual(res.status, 401);
        const data = await res.json();
        assert.strictEqual(data.error.code, 'UNAUTHORIZED');
    });

    await t.test('Protected endpoints reject invalid bearer token (401)', async () => {
        const res = await fetch(`${baseUrl}/models`, {
            headers: { 'Authorization': 'Bearer wrong-secret-token' }
        });
        assert.strictEqual(res.status, 401);
    });

    await t.test('Protected endpoints succeed with valid bearer token (200)', async () => {
        const res = await fetch(`${baseUrl}/models`, {
            headers: { 'Authorization': `Bearer ${secretToken}` }
        });
        assert.strictEqual(res.status, 200);
        const data = await res.json();
        assert.ok(Array.isArray(data));
    });

    await t.test('CORS headers: allowed origin is reflected', async () => {
        const res = await fetch(`${baseUrl}/health`, {
            headers: { 'Origin': 'http://trusted-client.lan' }
        });
        assert.strictEqual(res.headers.get('access-control-allow-origin'), 'http://trusted-client.lan');
    });

    await t.test('CORS headers: untrusted external origin is rejected in LAN mode', async () => {
        const res = await fetch(`${baseUrl}/health`, {
            headers: { 'Origin': 'http://malicious-site.com' }
        });
        assert.strictEqual(res.headers.get('access-control-allow-origin'), null);
    });

    await t.test('Malformed JSON payloads return 500/400 with structured error', async () => {
        const res = await fetch(`${baseUrl}/settings`, {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${secretToken}`,
                'Content-Type': 'application/json'
            },
            body: '{ malformed json: not valid }'
        });
        assert.strictEqual(res.status, 500);
        const err = await res.json();
        assert.ok(err.error.message.includes('Malformed JSON'));
    });

    await server.stop();
    fs.rmSync(tmpDir, { recursive: true, force: true });
});

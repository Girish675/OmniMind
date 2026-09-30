import test from 'node:test';
import assert from 'node:assert';
import path from 'node:path';
import fs from 'node:fs';
import { OmniMindServer } from '../server.js';

test('Health Endpoint Suite', async (t) => {
    const tmpDir = path.join(process.cwd(), 'tmp_test_health_' + Date.now());
    fs.mkdirSync(tmpDir, { recursive: true });
    const dbPath = path.join(tmpDir, 'test.db');

    const server = new OmniMindServer({
        port: 0, // Ephemeral port
        dbPath,
        modelsDir: path.join(tmpDir, 'models')
    });

    const port = await server.start();
    const baseUrl = `http://127.0.0.1:${port}`;

    await t.test('GET /health returns 200 and valid schema', async () => {
        const res = await fetch(`${baseUrl}/health`);
        assert.strictEqual(res.status, 200);

        const data = await res.json();
        assert.strictEqual(data.status, 'ok');
        assert.strictEqual(data.version, '0.1.0-alpha.1');
        assert.ok(typeof data.uptimeSeconds === 'number');
        assert.strictEqual(data.lanMode, false);
        assert.strictEqual(data.authRequired, false);
        assert.ok(data.memory && typeof data.memory.heapUsedBytes === 'number');
    });

    await t.test('GET /unknown returns 404 with structured error', async () => {
        const res = await fetch(`${baseUrl}/non_existent_route`);
        assert.strictEqual(res.status, 404);

        const data = await res.json();
        assert.ok(data.error);
        assert.strictEqual(data.error.code, 'ENDPOINT_NOT_FOUND');
    });

    await server.stop();
    fs.rmSync(tmpDir, { recursive: true, force: true });
});

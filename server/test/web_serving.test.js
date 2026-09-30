import test from 'node:test';
import assert from 'node:assert';
import path from 'node:path';
import fs from 'node:fs';
import { OmniMindServer } from '../server.js';

test('Web SPA Static Serving Suite', async (t) => {
    const tmpDir = path.join(process.cwd(), 'tmp_test_web_' + Date.now());
    fs.mkdirSync(tmpDir, { recursive: true });

    const server = new OmniMindServer({
        port: 0,
        dbPath: path.join(tmpDir, 'test.db'),
        staticDir: path.join(process.cwd(), 'web', 'dist')
    });

    const port = await server.start();
    const baseUrl = `http://127.0.0.1:${port}`;

    await t.test('GET / serves index.html with 200', async () => {
        const res = await fetch(`${baseUrl}/`);
        assert.strictEqual(res.status, 200);
        assert.ok(res.headers.get('content-type')?.includes('text/html'));
        const html = await res.text();
        assert.ok(html.includes('OmniMind'));
        assert.ok(html.includes('<div id="root"></div>'));
    });

    await t.test('GET /chat (SPA client-side route) falls back to index.html with 200', async () => {
        const res = await fetch(`${baseUrl}/chat`);
        assert.strictEqual(res.status, 200);
        assert.ok(res.headers.get('content-type')?.includes('text/html'));
        const html = await res.text();
        assert.ok(html.includes('<div id="root"></div>'));
    });

    await t.test('GET /assets/*.js serves compiled script with application/javascript', async () => {
        const distAssets = path.join(process.cwd(), 'web', 'dist', 'assets');
        const jsFiles = fs.readdirSync(distAssets).filter(f => f.endsWith('.js'));
        assert.ok(jsFiles.length > 0);

        const res = await fetch(`${baseUrl}/assets/${jsFiles[0]}`);
        assert.strictEqual(res.status, 200);
        assert.ok(res.headers.get('content-type')?.includes('application/javascript'));
    });

    await server.stop();
    fs.rmSync(tmpDir, { recursive: true, force: true });
});

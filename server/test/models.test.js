import test from 'node:test';
import assert from 'node:assert';
import path from 'node:path';
import fs from 'node:fs';
import { OmniMindServer } from '../server.js';

test('Models Subsystem Suite', async (t) => {
    const tmpDir = path.join(process.cwd(), 'tmp_test_models_' + Date.now());
    const modelsDir = path.join(tmpDir, 'models');
    fs.mkdirSync(modelsDir, { recursive: true });
    const dbPath = path.join(tmpDir, 'test.db');

    // Create a mock valid GGUF file: magic "GGUF" (0x46475547) + version 3
    const mockGgufPath = path.join(modelsDir, 'Qwen3-4B-Q4_K_M.gguf');
    const header = Buffer.alloc(1024);
    header.write('GGUF', 0, 'utf8');
    header.writeUInt32LE(3, 4);
    header.writeBigUInt64LE(10n, 8); // 10 tensors
    fs.writeFileSync(mockGgufPath, header);

    // Create an invalid mock file
    const invalidPath = path.join(modelsDir, 'invalid.gguf');
    fs.writeFileSync(invalidPath, Buffer.from('NOT_GGUF_DATA'));

    const server = new OmniMindServer({
        port: 0,
        dbPath,
        modelsDir
    });

    const port = await server.start();
    const baseUrl = `http://127.0.0.1:${port}`;

    let registeredModelId = null;

    await t.test('GET /models lists synced GGUF models', async () => {
        const res = await fetch(`${baseUrl}/models`);
        assert.strictEqual(res.status, 200);

        const models = await res.json();
        assert.ok(Array.isArray(models));
        assert.ok(models.length >= 1);

        const qwen = models.find(m => m.fileName === 'Qwen3-4B-Q4_K_M.gguf');
        assert.ok(qwen);
        assert.strictEqual(qwen.architecture, 'qwen2');
        assert.strictEqual(qwen.quantization, 'Q4_K_M');
        registeredModelId = qwen.id;
    });

    await t.test('GET /models/:id retrieves specific model', async () => {
        const res = await fetch(`${baseUrl}/models/${registeredModelId}`);
        assert.strictEqual(res.status, 200);

        const model = await res.json();
        assert.strictEqual(model.id, registeredModelId);
        assert.strictEqual(model.name, 'Qwen3-4B-Q4_K_M');
    });

    await t.test('POST /models/:id/load loads model and updates status', async () => {
        const res = await fetch(`${baseUrl}/models/${registeredModelId}/load`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ threads: 4, contextSize: 4096 })
        });
        assert.strictEqual(res.status, 200);

        const data = await res.json();
        assert.strictEqual(data.success, true);
        assert.strictEqual(data.modelId, registeredModelId);

        // Verify status in GET /health
        const healthRes = await fetch(`${baseUrl}/health`);
        const health = await healthRes.json();
        assert.strictEqual(health.loadedModelId, registeredModelId);
    });

    await t.test('DELETE /models/:id fails if model is loaded (Locking)', async () => {
        const res = await fetch(`${baseUrl}/models/${registeredModelId}`, {
            method: 'DELETE'
        });
        assert.strictEqual(res.status, 500);

        const data = await res.json();
        assert.ok(data.error);
        assert.ok(data.error.message.includes('currently loaded'));
    });

    await t.test('POST /models/unload unloads model', async () => {
        const res = await fetch(`${baseUrl}/models/unload`, { method: 'POST' });
        assert.strictEqual(res.status, 200);

        const healthRes = await fetch(`${baseUrl}/health`);
        const health = await healthRes.json();
        assert.strictEqual(health.loadedModelId, null);
    });

    await t.test('POST /models/import rejects non-existent or invalid files', async () => {
        const res = await fetch(`${baseUrl}/models/import`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ sourceFilePath: invalidPath, fileName: 'invalid.gguf' })
        });
        assert.strictEqual(res.status, 500);
        const err = await res.json();
        assert.ok(err.error.message.includes('not a valid GGUF'));
    });

    await server.stop();
    fs.rmSync(tmpDir, { recursive: true, force: true });
});

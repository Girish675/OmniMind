import test from 'node:test';
import assert from 'node:assert';
import path from 'node:path';
import fs from 'node:fs';
import { OmniMindServer } from '../server.js';

test('Chat & Streaming Subsystem Suite', async (t) => {
    const tmpDir = path.join(process.cwd(), 'tmp_test_chat_' + Date.now());
    const modelsDir = path.join(tmpDir, 'models');
    fs.mkdirSync(modelsDir, { recursive: true });
    const dbPath = path.join(tmpDir, 'test.db');

    // Create a mock GGUF model
    const mockGguf = path.join(modelsDir, 'Qwen3-4B-Q4_K_M.gguf');
    const header = Buffer.alloc(1024);
    header.write('GGUF', 0, 'utf8');
    header.writeUInt32LE(3, 4);
    fs.writeFileSync(mockGguf, header);

    const server = new OmniMindServer({
        port: 0,
        dbPath,
        modelsDir
    });

    const port = await server.start();
    const baseUrl = `http://127.0.0.1:${port}`;

    let conversationId = null;

    await t.test('POST /conversations creates a new chat', async () => {
        const res = await fetch(`${baseUrl}/conversations`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ title: 'Test Chat', systemPrompt: 'Be concise.' })
        });
        assert.strictEqual(res.status, 201);
        const conv = await res.json();
        assert.ok(conv.id);
        assert.strictEqual(conv.title, 'Test Chat');
        conversationId = conv.id;
    });

    await t.test('POST /chat/completions (non-streaming) returns full message and stats', async () => {
        const res = await fetch(`${baseUrl}/chat/completions`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                conversationId,
                messages: [{ role: 'user', content: 'What is local inference?' }],
                stream: false
            })
        });

        assert.strictEqual(res.status, 200);
        const data = await res.json();
        assert.ok(data.message);
        assert.strictEqual(data.message.role, 'assistant');
        assert.ok(data.message.content.length > 0);
        assert.ok(data.stats);
        assert.ok(data.stats.generationTokensPerSec > 0);

        // Verify conversation stored both user and assistant messages
        const convRes = await fetch(`${baseUrl}/conversations/${conversationId}`);
        const convData = await convRes.json();
        assert.strictEqual(convData.messages.length, 2);
        assert.strictEqual(convData.messages[0].role, 'user');
        assert.strictEqual(convData.messages[1].role, 'assistant');
        assert.strictEqual(convData.messages[1].is_partial, 0);
    });

    await t.test('POST /chat/completions (streaming) delivers SSE tokens', async () => {
        const res = await fetch(`${baseUrl}/chat/completions`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                conversationId,
                messages: [{ role: 'user', content: 'Stream a quick answer' }],
                stream: true
            })
        });

        assert.strictEqual(res.status, 200);
        assert.ok(res.headers.get('content-type')?.includes('text/event-stream'));

        const reader = res.body.getReader();
        const decoder = new TextDecoder();
        let receivedTokens = 0;
        let receivedDone = false;

        while (true) {
            const { done, value } = await reader.read();
            if (done) break;

            const text = decoder.decode(value);
            for (const line of text.split('\n')) {
                if (line.startsWith('data: ')) {
                    const dataStr = line.slice(6).trim();
                    if (dataStr === '[DONE]') {
                        receivedDone = true;
                    } else {
                        try {
                            const parsed = JSON.parse(dataStr);
                            if (parsed.token) receivedTokens++;
                        } catch (_) {}
                    }
                }
            }
        }

        assert.ok(receivedTokens > 0, 'Expected at least 1 streamed token');
        assert.strictEqual(receivedDone, true, 'Expected [DONE] stream termination event');
    });

    await t.test('POST /chat/stop immediately cancels generation', async () => {
        const controller = new AbortController();

        // Start long generation
        const streamPromise = fetch(`${baseUrl}/chat/completions`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                conversationId,
                messages: [{ role: 'user', content: 'Tell me a very long story' }],
                stream: true
            }),
            signal: controller.signal
        });

        // Give it 30ms to start
        await new Promise(r => setTimeout(r, 30));

        // Call stop
        const stopRes = await fetch(`${baseUrl}/chat/stop`, { method: 'POST' });
        assert.strictEqual(stopRes.status, 200);
        const stopData = await stopRes.json();
        assert.strictEqual(stopData.success, true);

        // Await stream completion
        const res = await streamPromise;
        const bodyText = await res.text();
        assert.ok(bodyText.includes('[DONE]'));
    });

    await server.stop();
    fs.rmSync(tmpDir, { recursive: true, force: true });
});

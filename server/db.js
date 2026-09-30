import { DatabaseSync } from 'node:sqlite';
import path from 'node:path';
import fs from 'node:fs';
import crypto from 'node:crypto';

export class AppDatabase {
    constructor(dbPath) {
        if (!dbPath) {
            const dataDir = path.join(process.cwd(), 'data');
            fs.mkdirSync(dataDir, { recursive: true });
            dbPath = path.join(dataDir, 'omnimind.db');
        } else {
            const dir = path.dirname(dbPath);
            fs.mkdirSync(dir, { recursive: true });
        }
        this.db = new DatabaseSync(dbPath);
        this.initSchema();
    }

    initSchema() {
        this.db.exec(`
            CREATE TABLE IF NOT EXISTS models (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                file_name TEXT NOT NULL,
                file_path TEXT NOT NULL,
                file_size INTEGER NOT NULL,
                sha256 TEXT,
                architecture TEXT,
                parameter_count INTEGER,
                quantization TEXT,
                context_length INTEGER,
                author TEXT,
                license TEXT,
                download_source TEXT,
                imported_at INTEGER NOT NULL,
                last_used_at INTEGER,
                is_default INTEGER NOT NULL DEFAULT 0,
                status TEXT NOT NULL DEFAULT 'ready'
            );

            CREATE TABLE IF NOT EXISTS conversations (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                model_id TEXT,
                system_prompt TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                is_pinned INTEGER NOT NULL DEFAULT 0
            );

            CREATE TABLE IF NOT EXISTS messages (
                id TEXT PRIMARY KEY,
                conversation_id TEXT NOT NULL,
                role TEXT NOT NULL,
                content TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                sequence_number INTEGER NOT NULL,
                is_partial INTEGER NOT NULL DEFAULT 0,
                token_count INTEGER,
                generation_stats_json TEXT,
                FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE
            );

            CREATE TABLE IF NOT EXISTS settings (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS benchmarks (
                id TEXT PRIMARY KEY,
                model_id TEXT NOT NULL,
                context_length INTEGER NOT NULL,
                threads INTEGER NOT NULL,
                backend TEXT NOT NULL,
                prompt_tokens INTEGER,
                gen_tokens INTEGER,
                prompt_tps REAL,
                gen_tps REAL,
                ttft_ms INTEGER,
                total_ms INTEGER,
                timestamp INTEGER NOT NULL
            );

            CREATE INDEX IF NOT EXISTS idx_messages_conversation ON messages(conversation_id, sequence_number);
        `);

        // Seed default settings if empty
        const count = this.db.prepare('SELECT count(*) as cnt FROM settings').get().cnt;
        if (count === 0) {
            const defaultSettings = {
                'temperature': '0.7',
                'top_p': '0.9',
                'top_k': '40',
                'min_p': '0.05',
                'repeat_penalty': '1.1',
                'presence_penalty': '0.0',
                'frequency_penalty': '0.0',
                'max_tokens': '2048',
                'context_size': '4096',
                'threads': '4',
                'gpu_layers': '0',
                'server_port': '8080',
                'lan_mode': '0',
                'auth_token': ''
            };
            const insert = this.db.prepare('INSERT INTO settings (key, value) VALUES (?, ?)');
            for (const [k, v] of Object.entries(defaultSettings)) {
                insert.run(k, v);
            }
        }
    }

    // --- Models ---
    getAllModels() {
        return this.db.prepare('SELECT * FROM models ORDER BY imported_at DESC').all();
    }

    getModelById(id) {
        return this.db.prepare('SELECT * FROM models WHERE id = ?').get(id);
    }

    insertModel(model) {
        const stmt = this.db.prepare(`
            INSERT INTO models (
                id, name, file_name, file_path, file_size, sha256,
                architecture, parameter_count, quantization, context_length,
                author, license, download_source, imported_at, last_used_at, is_default, status
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        `);
        stmt.run(
            model.id || crypto.randomUUID(),
            model.name,
            model.fileName,
            model.filePath,
            model.fileSize,
            model.sha256 || null,
            model.architecture || null,
            model.parameterCount || null,
            model.quantization || null,
            model.contextLength || null,
            model.author || null,
            model.license || null,
            model.downloadSource || null,
            model.importedAt || Date.now(),
            model.lastUsedAt || null,
            model.isDefault ? 1 : 0,
            model.status || 'ready'
        );
        return this.getModelById(model.id);
    }

    updateModelStatus(id, status) {
        this.db.prepare('UPDATE models SET status = ? WHERE id = ?').run(status, id);
    }

    setDefaultModel(id) {
        this.db.prepare('UPDATE models SET is_default = 0').run();
        this.db.prepare('UPDATE models SET is_default = 1 WHERE id = ?').run(id);
    }

    deleteModel(id) {
        this.db.prepare('DELETE FROM models WHERE id = ?').run(id);
    }

    // --- Conversations ---
    getAllConversations() {
        return this.db.prepare('SELECT * FROM conversations ORDER BY updated_at DESC').all();
    }

    getConversationById(id) {
        return this.db.prepare('SELECT * FROM conversations WHERE id = ?').get(id);
    }

    createConversation(title = 'New Conversation', modelId = null, systemPrompt = 'You are a helpful assistant.') {
        const id = crypto.randomUUID();
        const now = Date.now();
        this.db.prepare(`
            INSERT INTO conversations (id, title, model_id, system_prompt, created_at, updated_at, is_pinned)
            VALUES (?, ?, ?, ?, ?, ?, 0)
        `).run(id, title, modelId, systemPrompt, now, now);
        return this.getConversationById(id);
    }

    updateConversation(id, title) {
        const now = Date.now();
        this.db.prepare('UPDATE conversations SET title = ?, updated_at = ? WHERE id = ?').run(title, now, id);
        return this.getConversationById(id);
    }

    touchConversation(id) {
        this.db.prepare('UPDATE conversations SET updated_at = ? WHERE id = ?').run(Date.now(), id);
    }

    deleteConversation(id) {
        this.db.prepare('DELETE FROM messages WHERE conversation_id = ?').run(id);
        this.db.prepare('DELETE FROM conversations WHERE id = ?').run(id);
    }

    // --- Messages ---
    getMessagesByConversationId(conversationId) {
        return this.db.prepare(`
            SELECT * FROM messages WHERE conversation_id = ? ORDER BY sequence_number ASC
        `).all(conversationId);
    }

    getMessageById(id) {
        return this.db.prepare('SELECT * FROM messages WHERE id = ?').get(id);
    }

    insertMessage({ id = crypto.randomUUID(), conversationId, role, content, sequenceNumber, isPartial = 0, tokenCount = null, generationStatsJson = null }) {
        this.db.prepare(`
            INSERT INTO messages (id, conversation_id, role, content, created_at, sequence_number, is_partial, token_count, generation_stats_json)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        `).run(id, conversationId, role, content, Date.now(), sequenceNumber, isPartial ? 1 : 0, tokenCount, generationStatsJson);
        this.touchConversation(conversationId);
        return this.getMessageById(id);
    }

    updateMessageContent(id, content, isPartial = 0, statsJson = null, tokenCount = null) {
        this.db.prepare(`
            UPDATE messages
            SET content = ?, is_partial = ?, generation_stats_json = ?, token_count = ?
            WHERE id = ?
        `).run(content, isPartial ? 1 : 0, statsJson, tokenCount, id);
    }

    // --- Settings ---
    getAllSettings() {
        const rows = this.db.prepare('SELECT key, value FROM settings').all();
        const res = {};
        for (const r of rows) {
            res[r.key] = r.value;
        }
        return res;
    }

    updateSettings(settingsObj) {
        const stmt = this.db.prepare(`
            INSERT INTO settings (key, value) VALUES (?, ?)
            ON CONFLICT(key) DO UPDATE SET value = excluded.value
        `);
        for (const [k, v] of Object.entries(settingsObj)) {
            stmt.run(k, String(v));
        }
        return this.getAllSettings();
    }

    // --- Benchmarks ---
    insertBenchmark(b) {
        const id = crypto.randomUUID();
        this.db.prepare(`
            INSERT INTO benchmarks (id, model_id, context_length, threads, backend, prompt_tokens, gen_tokens, prompt_tps, gen_tps, ttft_ms, total_ms, timestamp)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        `).run(id, b.modelId, b.contextLength, b.threads, b.backend, b.promptTokens, b.genTokens, b.promptTps, b.genTps, b.ttftMs, b.totalMs, Date.now());
        return id;
    }

    getAllBenchmarks() {
        return this.db.prepare('SELECT * FROM benchmarks ORDER BY timestamp DESC').all();
    }

    close() {
        this.db.close();
    }
}

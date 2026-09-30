import http from 'node:http';
import path from 'node:path';
import crypto from 'node:crypto';
import fs from 'node:fs';
import { AppDatabase } from './db.js';
import { ModelManager } from './model_manager.js';
import { ProcessManager } from './process_manager.js';

export class OmniMindServer {
    constructor(options = {}) {
        this.port = options.port !== undefined ? parseInt(options.port, 10) : 8080;
        this.host = options.lanMode ? '0.0.0.0' : (options.host || '127.0.0.1');
        this.lanMode = !!options.lanMode;
        this.authToken = options.authToken || '';
        this.modelsDir = options.modelsDir || path.join(process.cwd(), 'models');
        this.staticDir = path.resolve(options.staticDir || path.join(process.cwd(), 'web', 'dist'));
        this.startTime = Date.now();
        this.allowedOrigins = options.allowedOrigins || [
            'http://localhost:8080',
            'http://127.0.0.1:8080',
            'http://localhost:5173',
            'http://127.0.0.1:5173',
            'http://localhost:3000',
            'http://127.0.0.1:3000',
            'tauri://localhost'
        ];

        this.db = new AppDatabase(options.dbPath);
        this.modelManager = new ModelManager(this.modelsDir, this.db);
        this.processManager = new ProcessManager();
        this.activeGenerationController = null;
        this.isGenerating = false;

        // Auto-sync existing GGUFs in models directory
        this.modelManager.syncModelsDirectory();

        this.server = http.createServer((req, res) => this.handleRequest(req, res));
    }

    sendJson(res, statusCode, data) {
        res.writeHead(statusCode, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify(data));
    }

    sendError(res, statusCode, code, message, details = null) {
        res.writeHead(statusCode, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
            error: {
                code,
                message,
                details
            }
        }));
    }

    handleCors(req, res) {
        const origin = req.headers['origin'];
        if (origin) {
            const isLocalhost = origin.includes('localhost') || origin.includes('127.0.0.1') || origin.includes('tauri://');
            const isExplicitlyAllowed = this.allowedOrigins.includes(origin);

            // In LAN mode, only explicitly allowed origins or localhost without external IP are permitted
            const allow = this.lanMode ? isExplicitlyAllowed : (isLocalhost || isExplicitlyAllowed);

            if (allow) {
                res.setHeader('Access-Control-Allow-Origin', origin);
                res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PATCH, DELETE, OPTIONS');
                res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization, Accept');
                res.setHeader('Access-Control-Max-Age', '86400');
            }
        }
    }

    authenticate(req) {
        if (!this.lanMode || !this.authToken) {
            return true; // No auth required for localhost
        }
        const authHeader = req.headers['authorization'];
        if (!authHeader) return false;
        const parts = authHeader.split(' ');
        if (parts.length !== 2 || parts[0] !== 'Bearer') return false;
        return parts[1] === this.authToken;
    }

    async parseJsonBody(req, maxSize = 10 * 1024 * 1024) {
        return new Promise((resolve, reject) => {
            let totalBytes = 0;
            const chunks = [];

            req.on('data', chunk => {
                totalBytes += chunk.length;
                if (totalBytes > maxSize) {
                    req.destroy();
                    reject(new Error('Request payload exceeds size limit of 10 MB'));
                    return;
                }
                chunks.push(chunk);
            });

            req.on('end', () => {
                const bodyStr = Buffer.concat(chunks).toString('utf8');
                if (!bodyStr.trim()) {
                    resolve({});
                    return;
                }
                try {
                    const parsed = JSON.parse(bodyStr);
                    resolve(parsed);
                } catch (e) {
                    reject(new Error(`Malformed JSON body: ${e.message}`));
                }
            });

            req.on('error', err => reject(err));
        });
    }

    async handleRequest(req, res) {
        this.handleCors(req, res);

        if (req.method === 'OPTIONS') {
            res.writeHead(204);
            res.end();
            return;
        }

        const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
        const pathname = url.pathname;
        const method = req.method;

        // Health endpoint is public (no auth required)
        if (pathname === '/health' && method === 'GET') {
            return this.sendJson(res, 200, {
                status: this.processManager.status === 'error' ? 'degraded' : 'ok',
                version: '0.1.0-alpha.1',
                uptimeSeconds: Math.floor((Date.now() - this.startTime) / 1000),
                loadedModelId: this.processManager.activeModelId,
                isGenerating: this.isGenerating,
                lanMode: this.lanMode,
                authRequired: this.lanMode && !!this.authToken,
                port: this.port,
                memory: {
                    heapUsedBytes: process.memoryUsage().heapUsed,
                    heapTotalBytes: process.memoryUsage().heapTotal,
                    rssBytes: process.memoryUsage().rss
                }
            });
        }

        // Enforce Authentication for other endpoints if LAN mode is active
        if (!this.authenticate(req)) {
            return this.sendError(res, 401, 'UNAUTHORIZED', 'Invalid or missing authentication token');
        }

        try {
            // --- Model Endpoints ---
            if (pathname === '/models' && method === 'GET') {
                const models = this.db.getAllModels();
                // Map snake_case database rows to clean API objects
                const mapped = models.map(m => ({
                    id: m.id,
                    name: m.name,
                    fileName: m.file_name,
                    filePath: m.file_path,
                    fileSize: m.file_size,
                    sha256: m.sha256,
                    architecture: m.architecture,
                    parameterCount: m.parameter_count,
                    quantization: m.quantization,
                    contextLength: m.context_length,
                    author: m.author,
                    license: m.license,
                    downloadSource: m.download_source,
                    importedAt: m.imported_at,
                    lastUsedAt: m.last_used_at,
                    isDefault: Boolean(m.is_default),
                    status: this.processManager.activeModelId === m.id ? 'loaded' : m.status
                }));
                return this.sendJson(res, 200, mapped);
            }

            const modelIdMatch = pathname.match(/^\/models\/([a-zA-Z0-9_\-]+)$/);
            if (modelIdMatch && method === 'GET') {
                const model = this.db.getModelById(modelIdMatch[1]);
                if (!model) return this.sendError(res, 404, 'MODEL_NOT_FOUND', 'Model not found');
                return this.sendJson(res, 200, model);
            }

            const loadMatch = pathname.match(/^\/models\/([a-zA-Z0-9_\-]+)\/load$/);
            if (loadMatch && method === 'POST') {
                const modelId = loadMatch[1];
                const model = this.db.getModelById(modelId);
                if (!model) return this.sendError(res, 404, 'MODEL_NOT_FOUND', 'Model not found');

                const body = await this.parseJsonBody(req);
                const settings = { ...this.db.getAllSettings(), ...body };

                const result = await this.processManager.startInferenceServer(model.id, model.file_path, settings);
                this.db.updateModelStatus(model.id, 'loaded');
                return this.sendJson(res, 200, { success: true, ...result });
            }

            if (pathname === '/models/unload' && method === 'POST') {
                const loadedId = this.processManager.activeModelId;
                await this.processManager.stopInferenceServer();
                if (loadedId) {
                    this.db.updateModelStatus(loadedId, 'ready');
                }
                return this.sendJson(res, 200, { success: true });
            }

            if (pathname === '/models/import' && method === 'POST') {
                const body = await this.parseJsonBody(req);
                if (!body.sourceFilePath) {
                    return this.sendError(res, 400, 'INVALID_ARGUMENT', 'Missing sourceFilePath');
                }
                const result = await this.modelManager.importModelFile(body.sourceFilePath, body.fileName);
                return this.sendJson(res, 201, result);
            }

            const deleteModelMatch = pathname.match(/^\/models\/([a-zA-Z0-9_\-]+)$/);
            if (deleteModelMatch && method === 'DELETE') {
                const modelId = deleteModelMatch[1];
                const result = this.modelManager.deleteModel(modelId, this.processManager.activeModelId);
                return this.sendJson(res, 200, result);
            }

            // --- Chat & Inference Endpoints ---
            if (pathname === '/chat/completions' && method === 'POST') {
                const body = await this.parseJsonBody(req);
                const messages = body.messages || [];
                const systemPrompt = body.systemPrompt || 'You are a helpful assistant.';
                const stream = body.stream !== false;
                const conversationId = body.conversationId || crypto.randomUUID();

                if (messages.length === 0) {
                    return this.sendError(res, 400, 'INVALID_ARGUMENT', 'Messages array cannot be empty');
                }

                // Check model is running
                if (this.processManager.status !== 'running') {
                    // Try auto-loading default model if available
                    const defaultModel = this.db.getAllModels().find(m => m.is_default) || this.db.getAllModels()[0];
                    if (defaultModel) {
                        await this.processManager.startInferenceServer(defaultModel.id, defaultModel.file_path);
                    } else {
                        return this.sendError(res, 400, 'NO_MODEL_LOADED', 'No model loaded. Please load a model first.');
                    }
                }

                // Ensure conversation exists
                let conv = this.db.getConversationById(conversationId);
                if (!conv) {
                    conv = this.db.createConversation('Chat ' + new Date().toLocaleTimeString(), this.processManager.activeModelId, systemPrompt);
                }

                // Record user message if new
                const lastMsg = messages[messages.length - 1];
                if (lastMsg && lastMsg.role === 'user') {
                    const existingMsgs = this.db.getMessagesByConversationId(conv.id);
                    this.db.insertMessage({
                        conversationId: conv.id,
                        role: 'user',
                        content: lastMsg.content,
                        sequenceNumber: existingMsgs.length + 1
                    });
                }

                // Create assistant message placeholder for partial recovery
                const assistantMsgId = crypto.randomUUID();
                const currentSeq = this.db.getMessagesByConversationId(conv.id).length + 1;
                this.db.insertMessage({
                    id: assistantMsgId,
                    conversationId: conv.id,
                    role: 'assistant',
                    content: '',
                    sequenceNumber: currentSeq,
                    isPartial: 1
                });

                this.activeGenerationController = new AbortController();
                this.isGenerating = true;

                if (stream) {
                    res.writeHead(200, {
                        'Content-Type': 'text/event-stream',
                        'Cache-Control': 'no-cache',
                        'Connection': 'keep-alive'
                    });

                    let generatedText = '';
                    let lastFlushTime = Date.now();

                    // Client disconnect handler
                    req.on('close', () => {
                        if (this.isGenerating) {
                            this.activeGenerationController?.abort();
                            this.db.updateMessageContent(assistantMsgId, generatedText, 1);
                        }
                    });

                    try {
                        const { text, stats } = await this.processManager.generateTokens({
                            messages,
                            systemPrompt,
                            settings: body.settings,
                            signal: this.activeGenerationController.signal,
                            onToken: (token) => {
                                generatedText += token;
                                res.write(`data: ${JSON.stringify({ token })}\n\n`);

                                // Flush partial to database every 500ms
                                if (Date.now() - lastFlushTime > 500) {
                                    lastFlushTime = Date.now();
                                    this.db.updateMessageContent(assistantMsgId, generatedText, 1);
                                }
                            }
                        });

                        // Complete assistant message in DB
                        this.db.updateMessageContent(assistantMsgId, text, 0, JSON.stringify(stats), stats.generatedTokens);
                        res.write(`data: ${JSON.stringify({ stats })}\n\n`);
                        res.write(`data: ${JSON.stringify({ id: assistantMsgId, conversationId: conv.id, message: { role: 'assistant', content: text }, stats, finishReason: 'stop' })}\n\n`);
                        res.write('data: [DONE]\n\n');
                        res.end();
                    } catch (err) {
                        const isCancelled = err.message.includes('cancelled') || this.activeGenerationController.signal.aborted;
                        this.db.updateMessageContent(assistantMsgId, generatedText, isCancelled ? 1 : 0);
                        res.write(`data: ${JSON.stringify({ error: { code: isCancelled ? 'CANCELLED' : 'GENERATION_ERROR', message: err.message } })}\n\n`);
                        res.write('data: [DONE]\n\n');
                        res.end();
                    } finally {
                        this.isGenerating = false;
                        this.activeGenerationController = null;
                    }
                } else {
                    // Non-streaming completion
                    try {
                        const { text, stats } = await this.processManager.generateTokens({
                            messages,
                            systemPrompt,
                            settings: body.settings,
                            signal: this.activeGenerationController.signal
                        });
                        this.db.updateMessageContent(assistantMsgId, text, 0, JSON.stringify(stats), stats.generatedTokens);
                        return this.sendJson(res, 200, {
                            id: assistantMsgId,
                            conversationId: conv.id,
                            message: { role: 'assistant', content: text },
                            stats,
                            finishReason: 'stop'
                        });
                    } catch (err) {
                        const isCancelled = err.message.includes('cancelled');
                        return this.sendError(res, 500, isCancelled ? 'CANCELLED' : 'GENERATION_ERROR', err.message);
                    } finally {
                        this.isGenerating = false;
                        this.activeGenerationController = null;
                    }
                }
                return;
            }

            if (pathname === '/chat/stop' && method === 'POST') {
                if (this.activeGenerationController) {
                    this.activeGenerationController.abort();
                }
                return this.sendJson(res, 200, { success: true });
            }

            // --- Conversation Endpoints ---
            if (pathname === '/conversations' && method === 'GET') {
                const convs = this.db.getAllConversations();
                return this.sendJson(res, 200, convs);
            }

            if (pathname === '/conversations' && method === 'POST') {
                const body = await this.parseJsonBody(req);
                const conv = this.db.createConversation(body.title, body.modelId, body.systemPrompt);
                return this.sendJson(res, 201, conv);
            }

            const convDetailMatch = pathname.match(/^\/conversations\/([a-zA-Z0-9_\-]+)$/);
            if (convDetailMatch && method === 'GET') {
                const convId = convDetailMatch[1];
                const conv = this.db.getConversationById(convId);
                if (!conv) return this.sendError(res, 404, 'NOT_FOUND', 'Conversation not found');
                const messages = this.db.getMessagesByConversationId(convId);
                return this.sendJson(res, 200, { conversation: conv, messages });
            }

            if (convDetailMatch && method === 'PATCH') {
                const convId = convDetailMatch[1];
                const body = await this.parseJsonBody(req);
                const updated = this.db.updateConversation(convId, body.title || 'Untitled');
                return this.sendJson(res, 200, updated);
            }

            if (convDetailMatch && method === 'DELETE') {
                const convId = convDetailMatch[1];
                this.db.deleteConversation(convId);
                return this.sendJson(res, 200, { success: true });
            }

            // --- Settings Endpoints ---
            if (pathname === '/settings' && method === 'GET') {
                const settings = this.db.getAllSettings();
                return this.sendJson(res, 200, settings);
            }

            if (pathname === '/settings' && method === 'POST') {
                const body = await this.parseJsonBody(req);
                const updated = this.db.updateSettings(body);
                return this.sendJson(res, 200, updated);
            }

            // --- Diagnostics & Logs ---
            if (pathname === '/diagnostics' && method === 'GET') {
                return this.sendJson(res, 200, {
                    activeModelId: this.processManager.activeModelId,
                    status: this.processManager.status,
                    stats: this.processManager.stats,
                    uptimeSeconds: Math.floor((Date.now() - this.startTime) / 1000),
                    benchmarks: this.db.getAllBenchmarks(),
                    hardware: {
                        platform: process.platform,
                        arch: process.arch,
                        memoryTotalBytes: process.memoryUsage().heapTotal,
                        memoryUsedBytes: process.memoryUsage().heapUsed,
                        rssBytes: process.memoryUsage().rss
                    }
                });
            }

            if (pathname === '/logs' && method === 'GET') {
                return this.sendJson(res, 200, { logs: this.processManager.getLogs() });
            }

            // Serve static files from web/dist if present
            if (method === 'GET' && fs.existsSync(this.staticDir)) {
                const handled = this.serveStatic(pathname, res, req);
                if (handled) return;
            }

            // Not found
            return this.sendError(res, 404, 'ENDPOINT_NOT_FOUND', `Route ${method} ${pathname} not found`);

        } catch (err) {
            console.error('API Error:', err);
            return this.sendError(res, 500, 'INTERNAL_ERROR', err.message);
        }
    }

    serveStatic(pathname, res, req) {
        const spaRoutes = ['/', '/chat', '/models', '/diagnostics', '/settings', '/logs', '/about'];
        const isSpaRoute = spaRoutes.includes(pathname);
        const resolved = path.resolve(path.join(this.staticDir, pathname === '/' ? 'index.html' : pathname));

        if (!resolved.startsWith(this.staticDir)) {
            return false; // Prevent path traversal
        }

        const isExactFile = fs.existsSync(resolved) && !fs.statSync(resolved).isDirectory();
        let filePath;

        if (isExactFile) {
            filePath = resolved;
        } else if (isSpaRoute || (req.headers['accept'] || '').includes('text/html')) {
            filePath = path.join(this.staticDir, 'index.html');
        } else {
            return false; // Not a static file or SPA route, let API handler handle 404
        }

        if (fs.existsSync(filePath)) {
            const ext = path.extname(filePath).toLowerCase();
            const mimeTypes = {
                '.html': 'text/html; charset=utf-8',
                '.js': 'application/javascript; charset=utf-8',
                '.css': 'text/css; charset=utf-8',
                '.json': 'application/json; charset=utf-8',
                '.png': 'image/png',
                '.jpg': 'image/jpeg',
                '.svg': 'image/svg+xml',
                '.ico': 'image/x-icon',
                '.woff2': 'font/woff2'
            };
            const contentType = mimeTypes[ext] || 'application/octet-stream';
            res.writeHead(200, { 'Content-Type': contentType });
            fs.createReadStream(filePath).pipe(res);
            return true;
        }
        return false;
    }

    start() {
        return new Promise((resolve) => {
            this.server.listen(this.port, this.host, () => {
                this.port = this.server.address().port;
                console.log(`[OmniMind Server] Running on http://${this.host}:${this.port} (LAN mode: ${this.lanMode})`);
                resolve(this.port);
            });
        });
    }

    stop() {
        return new Promise((resolve) => {
            this.processManager.stopInferenceServer().finally(() => {
                this.server.close(() => {
                    this.db.close();
                    resolve();
                });
            });
        });
    }
}

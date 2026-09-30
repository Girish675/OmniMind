import {
    AppSettings,
    ChatCompletionRequest,
    ChatCompletionResponse,
    Conversation,
    InferenceStats,
    ModelMetadata,
    ServerHealth
} from './types';

export interface StreamCallbacks {
    onToken?: (token: string) => void;
    onStats?: (stats: InferenceStats) => void;
    onDone?: (response: ChatCompletionResponse) => void;
    onError?: (error: { code: string; message: string }) => void;
}

export class OmniMindClient {
    private baseUrl: string;
    private authToken?: string;

    constructor(baseUrl: string = 'http://localhost:8080', authToken?: string) {
        this.baseUrl = baseUrl.replace(/\/+$/, '');
        this.authToken = authToken;
    }

    setBaseUrl(url: string) {
        this.baseUrl = url.replace(/\/+$/, '');
    }

    setAuthToken(token?: string) {
        this.authToken = token;
    }

    getBaseUrl(): string {
        return this.baseUrl;
    }

    private getHeaders(contentType: string = 'application/json'): Record<string, string> {
        const headers: Record<string, string> = {
            'Content-Type': contentType,
            'Accept': 'application/json'
        };
        if (this.authToken) {
            headers['Authorization'] = `Bearer ${this.authToken}`;
        }
        return headers;
    }

    private async handleResponse<T>(res: Response): Promise<T> {
        if (!res.ok) {
            let errorMsg = `HTTP Error ${res.status}: ${res.statusText}`;
            try {
                const body = await res.json();
                if (body && body.error && body.error.message) {
                    errorMsg = body.error.message;
                }
            } catch (_) {}
            throw new Error(errorMsg);
        }
        return res.json() as Promise<T>;
    }

    async getHealth(): Promise<ServerHealth> {
        const res = await fetch(`${this.baseUrl}/health`, {
            headers: this.getHeaders()
        });
        return this.handleResponse<ServerHealth>(res);
    }

    async getModels(): Promise<ModelMetadata[]> {
        const res = await fetch(`${this.baseUrl}/models`, {
            headers: this.getHeaders()
        });
        return this.handleResponse<ModelMetadata[]>(res);
    }

    async getModel(id: string): Promise<ModelMetadata> {
        const res = await fetch(`${this.baseUrl}/models/${encodeURIComponent(id)}`, {
            headers: this.getHeaders()
        });
        return this.handleResponse<ModelMetadata>(res);
    }

    async loadModel(id: string, settings?: any): Promise<{ success: boolean; modelId: string }> {
        const res = await fetch(`${this.baseUrl}/models/${encodeURIComponent(id)}/load`, {
            method: 'POST',
            headers: this.getHeaders(),
            body: JSON.stringify(settings || {})
        });
        return this.handleResponse<{ success: boolean; modelId: string }>(res);
    }

    async unloadModel(): Promise<{ success: boolean }> {
        const res = await fetch(`${this.baseUrl}/models/unload`, {
            method: 'POST',
            headers: this.getHeaders()
        });
        return this.handleResponse<{ success: boolean }>(res);
    }

    async deleteModel(id: string): Promise<{ success: boolean }> {
        const res = await fetch(`${this.baseUrl}/models/${encodeURIComponent(id)}`, {
            method: 'DELETE',
            headers: this.getHeaders()
        });
        return this.handleResponse<{ success: boolean }>(res);
    }

    async getSettings(): Promise<AppSettings> {
        const res = await fetch(`${this.baseUrl}/settings`, {
            headers: this.getHeaders()
        });
        return this.handleResponse<AppSettings>(res);
    }

    async updateSettings(settings: Partial<AppSettings>): Promise<AppSettings> {
        const res = await fetch(`${this.baseUrl}/settings`, {
            method: 'POST',
            headers: this.getHeaders(),
            body: JSON.stringify(settings)
        });
        return this.handleResponse<AppSettings>(res);
    }

    async getConversations(): Promise<Conversation[]> {
        const res = await fetch(`${this.baseUrl}/conversations`, {
            headers: this.getHeaders()
        });
        return this.handleResponse<Conversation[]>(res);
    }

    async getConversation(id: string): Promise<{ conversation: Conversation; messages: any[] }> {
        const res = await fetch(`${this.baseUrl}/conversations/${encodeURIComponent(id)}`, {
            headers: this.getHeaders()
        });
        return this.handleResponse<{ conversation: Conversation; messages: any[] }>(res);
    }

    async createConversation(title?: string, modelId?: string, systemPrompt?: string): Promise<Conversation> {
        const res = await fetch(`${this.baseUrl}/conversations`, {
            method: 'POST',
            headers: this.getHeaders(),
            body: JSON.stringify({ title, modelId, systemPrompt })
        });
        return this.handleResponse<Conversation>(res);
    }

    async updateConversation(id: string, title: string): Promise<Conversation> {
        const res = await fetch(`${this.baseUrl}/conversations/${encodeURIComponent(id)}`, {
            method: 'PATCH',
            headers: this.getHeaders(),
            body: JSON.stringify({ title })
        });
        return this.handleResponse<Conversation>(res);
    }

    async deleteConversation(id: string): Promise<{ success: boolean }> {
        const res = await fetch(`${this.baseUrl}/conversations/${encodeURIComponent(id)}`, {
            method: 'DELETE',
            headers: this.getHeaders()
        });
        return this.handleResponse<{ success: boolean }>(res);
    }

    async chatCompletion(request: ChatCompletionRequest): Promise<ChatCompletionResponse> {
        const res = await fetch(`${this.baseUrl}/chat/completions`, {
            method: 'POST',
            headers: this.getHeaders(),
            body: JSON.stringify({ ...request, stream: false })
        });
        return this.handleResponse<ChatCompletionResponse>(res);
    }

    async stopChat(conversationId?: string): Promise<{ success: boolean }> {
        const res = await fetch(`${this.baseUrl}/chat/stop`, {
            method: 'POST',
            headers: this.getHeaders(),
            body: JSON.stringify({ conversationId })
        });
        return this.handleResponse<{ success: boolean }>(res);
    }

    chatCompletionStream(
        request: ChatCompletionRequest,
        callbacks: StreamCallbacks,
        abortSignal?: AbortSignal
    ): () => void {
        let isCancelled = false;
        const controller = new AbortController();

        const cancel = () => {
            if (!isCancelled) {
                isCancelled = true;
                controller.abort();
                this.stopChat(request.conversationId).catch(() => {});
            }
        };

        if (abortSignal) {
            abortSignal.addEventListener('abort', cancel);
        }

        const runStream = async () => {
            try {
                const headers = this.getHeaders('application/json');
                headers['Accept'] = 'text/event-stream';

                const response = await fetch(`${this.baseUrl}/chat/completions`, {
                    method: 'POST',
                    headers,
                    body: JSON.stringify({ ...request, stream: true }),
                    signal: controller.signal
                });

                if (!response.ok) {
                    let errMsg = `Stream HTTP ${response.status}: ${response.statusText}`;
                    try {
                        const errObj = await response.json();
                        if (errObj?.error?.message) errMsg = errObj.error.message;
                    } catch (_) {}
                    callbacks.onError?.({ code: 'HTTP_ERROR', message: errMsg });
                    return;
                }

                if (!response.body) {
                    callbacks.onError?.({ code: 'NO_BODY', message: 'ReadableStream body not available' });
                    return;
                }

                const reader = response.body.getReader();
                const decoder = new TextDecoder('utf-8');
                let buffer = '';

                while (true) {
                    const { done, value } = await reader.read();
                    if (done) break;

                    buffer += decoder.decode(value, { stream: true });
                    const lines = buffer.split('\n');
                    buffer = lines.pop() || '';

                    for (const line of lines) {
                        const trimmed = line.trim();
                        if (!trimmed || trimmed.startsWith(':')) continue; // Skip keep-alives and empty lines

                        if (trimmed.startsWith('data: ')) {
                            const dataStr = trimmed.slice(6);
                            if (dataStr === '[DONE]') {
                                continue;
                            }
                            try {
                                const parsed = JSON.parse(dataStr);
                                if (parsed.token !== undefined) {
                                    callbacks.onToken?.(parsed.token);
                                }
                                if (parsed.stats) {
                                    callbacks.onStats?.(parsed.stats);
                                }
                                if (parsed.finishReason || parsed.message) {
                                    callbacks.onDone?.(parsed);
                                }
                                if (parsed.error) {
                                    callbacks.onError?.(parsed.error);
                                }
                            } catch (e) {
                                // Non-JSON data or custom message
                            }
                        }
                    }
                }
            } catch (err: any) {
                if (err.name === 'AbortError') {
                    // Stream aborted by client
                } else {
                    callbacks.onError?.({ code: 'STREAM_FAILED', message: err.message || 'Stream connection error' });
                }
            }
        };

        runStream();
        return cancel;
    }

    async getDiagnostics(): Promise<any> {
        const res = await fetch(`${this.baseUrl}/diagnostics`, {
            headers: this.getHeaders()
        });
        return this.handleResponse<any>(res);
    }

    async getLogs(): Promise<{ logs: string[] }> {
        const res = await fetch(`${this.baseUrl}/logs`, {
            headers: this.getHeaders()
        });
        return this.handleResponse<{ logs: string[] }>(res);
    }
}

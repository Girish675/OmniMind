/**
 * OmniMind Shared Type Definitions
 * Shared across Android, Desktop, Web, and Local Inference Host.
 */

export type Role = 'system' | 'user' | 'assistant';

export interface ModelMetadata {
    id: string;
    name: string;
    fileName: string;
    filePath: string;
    fileSize: number;
    sha256?: string;
    architecture?: string;
    parameterCount?: number;
    quantization?: string;
    contextLength?: number;
    author?: string;
    license?: string;
    downloadSource?: string;
    importedAt: number;
    lastUsedAt?: number;
    isDefault: boolean;
    status: 'ready' | 'loading' | 'loaded' | 'corrupted' | 'error';
}

export interface Conversation {
    id: string;
    title: string;
    modelId: string;
    systemPrompt: string;
    createdAt: number;
    updatedAt: number;
    isPinned: boolean;
}

export interface Message {
    id: string;
    conversationId: string;
    role: Role;
    content: string;
    createdAt: number;
    sequenceNumber: number;
    isPartial: boolean;
    tokenCount?: number;
    generationStats?: InferenceStats;
}

export interface GenerationSettings {
    temperature: number;
    topP: number;
    topK: number;
    minP: number;
    repeatPenalty: number;
    presencePenalty: number;
    frequencyPenalty: number;
    maxTokens: number;
    seed: number;
    contextSize: number;
    threads: number;
    batchSize: number;
    gpuLayers: number;
}

export interface InferenceStats {
    promptTokens: number;
    generatedTokens: number;
    promptTokensPerSec: number;
    generationTokensPerSec: number;
    timeToFirstTokenMs: number;
    totalTimeMs: number;
    loadTimeMs: number;
    peakMemoryBytes: number;
}

export interface ChatCompletionRequest {
    conversationId?: string;
    modelId?: string;
    messages: Array<{
        role: Role;
        content: string;
    }>;
    systemPrompt?: string;
    settings?: Partial<GenerationSettings>;
    stream?: boolean;
}

export interface ChatCompletionResponse {
    id: string;
    conversationId: string;
    message: {
        role: Role;
        content: string;
    };
    stats: InferenceStats;
    finishReason: 'stop' | 'length' | 'cancelled' | 'error';
}

export interface ServerHealth {
    status: 'ok' | 'degraded' | 'error';
    version: string;
    uptimeSeconds: number;
    loadedModelId: string | null;
    isGenerating: boolean;
    lanMode: boolean;
    authRequired: boolean;
    port: number;
    memory: {
        heapUsedBytes: number;
        heapTotalBytes: number;
        rssBytes: number;
    };
}

export interface BackendInfo {
    name: string;
    description: string;
    isAccelerator: boolean;
    isAvailable: boolean;
}

export interface AppSettings {
    generation: GenerationSettings;
    serverPort: number;
    lanMode: boolean;
    authToken: string;
    allowedOrigins: string[];
    modelsDirectory: string;
}

export interface ApiError {
    error: {
        code: string;
        message: string;
        details?: any;
    };
}

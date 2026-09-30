import React, { useState, useEffect, useRef } from 'react';
import { Conversation, Message, ModelMetadata, InferenceStats } from '@shared/types';

interface ChatViewProps {
  conversation: Conversation | null;
  messages: Message[];
  models: ModelMetadata[];
  activeModelId: string | null;
  onSelectModel: (id: string) => void;
  onSendMessage: (content: string, systemPrompt?: string, settings?: any) => void;
  onStopGeneration: () => void;
  isGenerating: boolean;
  streamingText: string;
  activeStats: InferenceStats | null;
  onClearConversation: () => void;
}

export const ChatView: React.FC<ChatViewProps> = ({
  conversation,
  messages,
  models,
  activeModelId,
  onSelectModel,
  onSendMessage,
  onStopGeneration,
  isGenerating,
  streamingText,
  activeStats,
  onClearConversation
}) => {
  const [inputText, setInputText] = useState('');
  const [systemPrompt, setSystemPrompt] = useState(conversation?.systemPrompt || 'You are a helpful, respectful, and honest assistant.');
  const [showSystemPrompt, setShowSystemPrompt] = useState(false);
  const [temperature, setTemperature] = useState(0.7);
  const [contextLength, setContextLength] = useState(4096);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, streamingText]);

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
      e.preventDefault();
      handleSend();
    }
  };

  const handleSend = () => {
    if (!inputText.trim() || isGenerating) return;
    onSendMessage(inputText.trim(), systemPrompt, { temperature, contextSize: contextLength });
    setInputText('');
  };

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
  };

  const loadedModel = models.find(m => m.id === activeModelId);

  return (
    <div className="main-content">
      <header className="top-header">
        <div className="header-title-group">
          <span className="header-title">{conversation?.title || 'New Conversation'}</span>
          <select
            className="form-input"
            style={{ padding: '4px 10px', fontSize: '0.8rem', width: 'auto' }}
            value={activeModelId || ''}
            onChange={(e) => onSelectModel(e.target.value)}
          >
            {models.length === 0 ? (
              <option value="">No models available</option>
            ) : (
              models.map(m => (
                <option key={m.id} value={m.id}>
                  {m.name} ({m.quantization || 'GGUF'})
                </option>
              ))
            )}
          </select>
        </div>

        <div className="header-stats">
          {activeStats && (
            <>
              {activeStats.generationTokensPerSec > 0 && (
                <span className="badge badge-cyan">{activeStats.generationTokensPerSec} tok/s</span>
              )}
              {activeStats.timeToFirstTokenMs > 0 && (
                <span className="badge badge-indigo">TTFT: {activeStats.timeToFirstTokenMs}ms</span>
              )}
            </>
          )}
          {messages.length > 0 && (
            <button className="btn-icon" onClick={onClearConversation} title="Clear conversation">
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <polyline points="3 6 5 6 21 6"></polyline>
                <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path>
              </svg>
            </button>
          )}
        </div>
      </header>

      {/* Messages Scroll Area */}
      <div className="chat-messages">
        {messages.length === 0 && !streamingText ? (
          <div style={{ margin: 'auto', textAlign: 'center', maxWidth: '480px', color: 'var(--text-muted)' }}>
            <div style={{ fontSize: '2.5rem', marginBottom: '12px' }}>🧠</div>
            <h2 style={{ fontFamily: 'var(--font-heading)', color: 'var(--text-primary)', marginBottom: '8px' }}>
              OmniMind Local Chat
            </h2>
            <p style={{ fontSize: '0.9rem', lineHeight: '1.6' }}>
              Inference runs directly on your machine using <strong>llama.cpp</strong>.
              Your prompts, weights, and conversations remain completely offline and private.
            </p>
          </div>
        ) : (
          messages.map((m) => (
            <div key={m.id} className={`message-wrapper message-${m.role}`}>
              <div className="message-card">
                {m.content.split('\n').map((line: string, idx: number) => (
                  <p key={idx} style={{ margin: '3px 0' }}>{line || '\u00A0'}</p>
                ))}
              </div>
              <div className="message-meta">
                <span>{new Date(m.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>
                <div className="message-actions">
                  <button className="btn-icon" onClick={() => copyToClipboard(m.content)} title="Copy message">
                    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                      <rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>
                      <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>
                    </svg>
                  </button>
                </div>
              </div>
            </div>
          ))
        )}

        {/* Live streaming bubble */}
        {isGenerating && streamingText && (
          <div className="message-wrapper message-assistant">
            <div className="message-card">
              {streamingText.split('\n').map((line: string, idx: number) => (
                <p key={idx} style={{ margin: '3px 0' }}>{line || '\u00A0'}</p>
              ))}
              <span style={{ display: 'inline-block', width: '8px', height: '15px', background: 'var(--accent-cyan)', marginLeft: '4px', verticalAlign: 'middle', animation: 'blink 1s infinite' }} />
            </div>
            <div className="message-meta">
              <span>Streaming tokens...</span>
            </div>
          </div>
        )}

        <div ref={messagesEndRef} />
      </div>

      {/* Input Bar */}
      <div className="chat-input-wrapper">
        <div className="chat-input-box">
          {showSystemPrompt && (
            <div style={{ borderBottom: '1px solid var(--border-subtle)', paddingBottom: '8px' }}>
              <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginBottom: '4px' }}>System Prompt</div>
              <input
                type="text"
                className="form-input"
                style={{ width: '100%', fontSize: '0.85rem' }}
                value={systemPrompt}
                onChange={(e) => setSystemPrompt(e.target.value)}
                placeholder="Set system instruction..."
              />
            </div>
          )}

          <textarea
            ref={textareaRef}
            className="chat-textarea"
            placeholder={loadedModel ? `Ask ${loadedModel.name}... (Ctrl+Enter to send)` : "Ask anything... (Ctrl+Enter to send)"}
            value={inputText}
            onChange={(e) => setInputText(e.target.value)}
            onKeyDown={handleKeyDown}
            rows={2}
          />

          <div className="input-toolbar">
            <div className="toolbar-controls">
              <button
                className={`btn-icon ${showSystemPrompt ? 'active' : ''}`}
                onClick={() => setShowSystemPrompt(!showSystemPrompt)}
                title="Toggle System Prompt"
                style={{ color: showSystemPrompt ? 'var(--accent-cyan)' : undefined }}
              >
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M12 20h9"></path>
                  <path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"></path>
                </svg>
              </button>

              <div className="slider-group" title="Temperature (Creativity)">
                <span>Temp</span>
                <input
                  type="range"
                  min="0.1"
                  max="1.5"
                  step="0.05"
                  value={temperature}
                  onChange={(e) => setTemperature(parseFloat(e.target.value))}
                  className="slider-input"
                />
                <span style={{ fontFamily: 'var(--font-mono)' }}>{temperature.toFixed(2)}</span>
              </div>

              <div className="slider-group" title="Context Length">
                <span>Ctx</span>
                <select
                  className="form-input"
                  style={{ padding: '2px 6px', fontSize: '0.75rem' }}
                  value={contextLength}
                  onChange={(e) => setContextLength(parseInt(e.target.value, 10))}
                >
                  <option value={2048}>2048</option>
                  <option value={4096}>4096</option>
                  <option value={8192}>8192</option>
                  <option value={16384}>16384</option>
                </select>
              </div>
            </div>

            {isGenerating ? (
              <button className="btn-stop" onClick={onStopGeneration} title="Stop generation (Esc)">
                <svg width="13" height="13" viewBox="0 0 24 24" fill="currentColor">
                  <rect x="4" y="4" width="16" height="16" rx="2"></rect>
                </svg>
                <span>Stop</span>
              </button>
            ) : (
              <button className="btn-send" onClick={handleSend} title="Send (Ctrl+Enter)">
                <span>Send</span>
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                  <line x1="22" y1="2" x2="11" y2="13"></line>
                  <polygon points="22 2 15 22 11 13 2 9 22 2"></polygon>
                </svg>
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

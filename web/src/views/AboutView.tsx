import React from 'react';

export const AboutView: React.FC = () => {
  return (
    <div className="view-container">
      <div className="view-header">
        <h1 className="view-title">About OmniMind</h1>
        <p className="view-subtitle">Private, offline-first local LLM inference across Android, Desktop, and Web.</p>
      </div>

      <div className="card-grid" style={{ gridTemplateColumns: '1fr' }}>
        <div className="glass-card">
          <h3 style={{ fontFamily: 'var(--font-heading)', color: '#FFF', marginBottom: '10px' }}>
            Privacy by Architecture
          </h3>
          <p style={{ fontSize: '0.9rem', color: 'var(--text-secondary)', lineHeight: '1.7', marginBottom: '12px' }}>
            OmniMind was engineered from day one on a strict privacy-first foundation:
          </p>
          <ul style={{ paddingLeft: '20px', color: 'var(--text-secondary)', fontSize: '0.88rem', lineHeight: '1.8' }}>
            <li><strong>Zero Cloud Inference:</strong> All prompt evaluations and token generation occur strictly on your local device.</li>
            <li><strong>No Telemetry or Tracking:</strong> No analytics, tracking pixels, or diagnostic telemetry are transmitted anywhere.</li>
            <li><strong>Air-Gapped Operation:</strong> You can disconnect from the internet or activate airplane mode and continue using OmniMind without interruption.</li>
            <li><strong>Model Weights Stay Local:</strong> GGUF model files remain in your local app storage and are never uploaded or synced to external cloud services.</li>
          </ul>
        </div>

        <div className="glass-card">
          <h3 style={{ fontFamily: 'var(--font-heading)', color: '#FFF', marginBottom: '10px' }}>
            Keyboard Shortcuts
          </h3>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: '10px', fontSize: '0.85rem' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 12px', background: 'var(--bg-input)', borderRadius: 'var(--radius-sm)' }}>
              <span>Send Message</span>
              <kbd style={{ fontFamily: 'var(--font-mono)', background: 'rgba(255,255,255,0.1)', padding: '2px 6px', borderRadius: '4px' }}>Ctrl + Enter</kbd>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 12px', background: 'var(--bg-input)', borderRadius: 'var(--radius-sm)' }}>
              <span>Stop Generation</span>
              <kbd style={{ fontFamily: 'var(--font-mono)', background: 'rgba(255,255,255,0.1)', padding: '2px 6px', borderRadius: '4px' }}>Escape</kbd>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 12px', background: 'var(--bg-input)', borderRadius: 'var(--radius-sm)' }}>
              <span>New Conversation</span>
              <kbd style={{ fontFamily: 'var(--font-mono)', background: 'rgba(255,255,255,0.1)', padding: '2px 6px', borderRadius: '4px' }}>Ctrl + N</kbd>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 12px', background: 'var(--bg-input)', borderRadius: 'var(--radius-sm)' }}>
              <span>Insert Newline</span>
              <kbd style={{ fontFamily: 'var(--font-mono)', background: 'rgba(255,255,255,0.1)', padding: '2px 6px', borderRadius: '4px' }}>Shift + Enter</kbd>
            </div>
          </div>
        </div>

        <div className="glass-card">
          <h3 style={{ fontFamily: 'var(--font-heading)', color: '#FFF', marginBottom: '10px' }}>
            Third-Party Licenses & Technology
          </h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', fontSize: '0.85rem', color: 'var(--text-secondary)' }}>
            <div><strong>llama.cpp:</strong> MIT License — High-performance LLM inference engine by Georgi Gerganov & contributors.</div>
            <div><strong>Qwen3-4B-GGUF:</strong> Apache 2.0 License — Open-weights foundational LLM developed by Alibaba Cloud Qwen Team.</div>
            <div><strong>React & TypeScript:</strong> MIT / Apache 2.0 — User interface runtime.</div>
            <div><strong>Android NDK & Jetpack Compose:</strong> Apache 2.0 — Native mobile acceleration and UI toolkit.</div>
          </div>
        </div>
      </div>
    </div>
  );
};

import React, { useState } from 'react';
import { AppSettings } from '@shared/types';

interface SettingsViewProps {
  settings: AppSettings | null;
  serverUrl: string;
  onUpdateServerUrl: (url: string) => void;
  onSaveSettings: (settings: Partial<AppSettings>) => Promise<void>;
}

export const SettingsView: React.FC<SettingsViewProps> = ({
  settings,
  serverUrl,
  onUpdateServerUrl,
  onSaveSettings
}) => {
  const [urlInput, setUrlInput] = useState(serverUrl);
  const [threads, setThreads] = useState(settings?.generation?.threads || 4);
  const [contextSize, setContextSize] = useState(settings?.generation?.contextSize || 4096);
  const [temperature, setTemperature] = useState(settings?.generation?.temperature || 0.7);
  const [topP, setTopP] = useState(settings?.generation?.topP || 0.9);
  const [lanMode, setLanMode] = useState(settings?.lanMode || false);
  const [authToken, setAuthToken] = useState(settings?.authToken || '');
  const [saveStatus, setSaveStatus] = useState<string | null>(null);

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    onUpdateServerUrl(urlInput.trim());

    try {
      await onSaveSettings({
        lanMode,
        authToken: authToken.trim(),
        generation: {
          ...(settings?.generation || {}),
          threads,
          contextSize,
          temperature,
          topP,
          topK: 40,
          minP: 0.05,
          repeatPenalty: 1.1,
          presencePenalty: 0.0,
          frequencyPenalty: 0.0,
          maxTokens: 2048,
          seed: -1,
          batchSize: 512,
          gpuLayers: 0
        }
      });
      setSaveStatus('Settings saved successfully!');
      setTimeout(() => setSaveStatus(null), 3000);
    } catch (err: any) {
      setSaveStatus(`Failed to save: ${err.message}`);
    }
  };

  return (
    <div className="view-container">
      <div className="view-header">
        <h1 className="view-title">Settings</h1>
        <p className="view-subtitle">Configure model inference parameters, network accessibility, and security.</p>
      </div>

      <form onSubmit={handleSave} style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
        {/* Network & Host Configuration */}
        <div className="glass-card">
          <h3 style={{ fontFamily: 'var(--font-heading)', color: '#FFF', marginBottom: '14px' }}>
            Inference Host Connection
          </h3>

          <div className="form-group">
            <label className="form-label">Inference Server URL</label>
            <input
              type="text"
              className="form-input"
              value={urlInput}
              onChange={(e) => setUrlInput(e.target.value)}
              placeholder="http://localhost:8080"
            />
            <span style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>
              The endpoint where the OmniMind local server or llama.cpp process is running.
            </span>
          </div>

          <div style={{ marginTop: '16px', padding: '14px', background: 'var(--bg-input)', borderRadius: 'var(--radius-md)' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '8px' }}>
              <div>
                <span style={{ fontWeight: 600, color: '#FFF' }}>LAN Access Mode</span>
                <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                  Allow devices on your local Wi-Fi / LAN to connect to this inference server.
                </p>
              </div>
              <input
                type="checkbox"
                checked={lanMode}
                onChange={(e) => setLanMode(e.target.checked)}
                style={{ width: '18px', height: '18px', accentColor: 'var(--accent-cyan)' }}
              />
            </div>

            {lanMode && (
              <div style={{ marginTop: '12px', borderTop: '1px solid var(--border-subtle)', paddingTop: '12px' }}>
                <div style={{ padding: '8px 12px', background: 'rgba(245, 158, 11, 0.1)', border: '1px solid rgba(245, 158, 11, 0.3)', borderRadius: 'var(--radius-sm)', color: 'var(--accent-amber)', fontSize: '0.8rem', marginBottom: '12px' }}>
                  ⚠️ <strong>Security Warning:</strong> Enabling LAN mode binds the server to 0.0.0.0. An authentication Bearer token is strictly required to prevent unauthorized network access.
                </div>

                <div className="form-group">
                  <label className="form-label">Authentication Token (Bearer)</label>
                  <input
                    type="password"
                    className="form-input"
                    placeholder="Enter secret auth token..."
                    value={authToken}
                    onChange={(e) => setAuthToken(e.target.value)}
                  />
                </div>
              </div>
            )}
          </div>
        </div>

        {/* Inference Defaults */}
        <div className="glass-card">
          <h3 style={{ fontFamily: 'var(--font-heading)', color: '#FFF', marginBottom: '14px' }}>
            Inference Defaults (Snapdragon / Desktop ARM64)
          </h3>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
            <div className="form-group">
              <label className="form-label">CPU Threads</label>
              <input
                type="number"
                min="1"
                max="32"
                className="form-input"
                value={threads}
                onChange={(e) => setThreads(parseInt(e.target.value, 10))}
              />
              <span style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>Recommended: 4 performance cores</span>
            </div>

            <div className="form-group">
              <label className="form-label">Context Size (tokens)</label>
              <select
                className="form-input"
                value={contextSize}
                onChange={(e) => setContextSize(parseInt(e.target.value, 10))}
              >
                <option value={2048}>2048 (Low RAM)</option>
                <option value={4096}>4096 (Standard)</option>
                <option value={8192}>8192 (Extended)</option>
                <option value={16384}>16384 (High RAM)</option>
              </select>
            </div>

            <div className="form-group">
              <label className="form-label">Default Temperature ({temperature})</label>
              <input
                type="range"
                min="0.1"
                max="1.5"
                step="0.05"
                className="slider-input"
                style={{ width: '100%' }}
                value={temperature}
                onChange={(e) => setTemperature(parseFloat(e.target.value))}
              />
            </div>

            <div className="form-group">
              <label className="form-label">Nucleus Sampling Top-P ({topP})</label>
              <input
                type="range"
                min="0.1"
                max="1.0"
                step="0.05"
                className="slider-input"
                style={{ width: '100%' }}
                value={topP}
                onChange={(e) => setTopP(parseFloat(e.target.value))}
              />
            </div>
          </div>
        </div>

        {saveStatus && (
          <div style={{ color: saveStatus.includes('Failed') ? 'var(--accent-rose)' : 'var(--accent-emerald)', fontSize: '0.9rem', fontWeight: 500 }}>
            {saveStatus}
          </div>
        )}

        <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
          <button type="submit" className="btn-send" style={{ padding: '10px 24px' }}>
            Save Settings
          </button>
        </div>
      </form>
    </div>
  );
};

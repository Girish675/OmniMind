import React, { useState } from 'react';
import { ModelMetadata } from '@shared/types';

interface ModelsViewProps {
  models: ModelMetadata[];
  activeModelId: string | null;
  onLoadModel: (id: string) => void;
  onUnloadModel: () => void;
  onDeleteModel: (id: string) => void;
  onImportModel: (sourcePath: string, fileName?: string) => Promise<void>;
}

export const ModelsView: React.FC<ModelsViewProps> = ({
  models,
  activeModelId,
  onLoadModel,
  onUnloadModel,
  onDeleteModel,
  onImportModel
}) => {
  const [showImportModal, setShowImportModal] = useState(false);
  const [importPath, setImportPath] = useState('');
  const [importError, setImportError] = useState<string | null>(null);
  const [isImporting, setIsImporting] = useState(false);
  const [selectedModel, setSelectedModel] = useState<ModelMetadata | null>(null);

  const formatBytes = (bytes: number): string => {
    const gb = bytes / (1024 * 1024 * 1024);
    if (gb >= 1.0) return `${gb.toFixed(2)} GB`;
    const mb = bytes / (1024 * 1024);
    if (mb >= 1.0) return `${mb.toFixed(1)} MB`;
    return `${(bytes / 1024).toFixed(0)} KB`;
  };

  const handleImportSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!importPath.trim()) return;
    setIsImporting(true);
    setImportError(null);
    try {
      await onImportModel(importPath.trim());
      setShowImportModal(false);
      setImportPath('');
    } catch (err: any) {
      setImportError(err.message || 'Failed to import GGUF model');
    } finally {
      setIsImporting(false);
    }
  };

  return (
    <div className="view-container">
      <div className="view-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <h1 className="view-title">Model Manager</h1>
          <p className="view-subtitle">Manage locally stored GGUF neural models. All weights remain strictly on-device.</p>
        </div>
        <button
          className="btn-new-chat"
          onClick={() => setShowImportModal(true)}
          style={{ background: 'linear-gradient(135deg, rgba(0, 229, 255, 0.2) 0%, rgba(99, 102, 241, 0.25) 100%)' }}
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
            <line x1="12" y1="5" x2="12" y2="19"></line>
            <line x1="5" y1="12" x2="19" y2="12"></line>
          </svg>
          <span>Import Model</span>
        </button>
      </div>

      <div className="card-grid">
        {models.length === 0 ? (
          <div className="glass-card" style={{ gridColumn: '1 / -1', textAlign: 'center', padding: '40px' }}>
            <p style={{ color: 'var(--text-secondary)', marginBottom: '14px' }}>
              No GGUF models detected in your local models directory.
            </p>
            <button className="btn-new-chat" style={{ margin: '0 auto' }} onClick={() => setShowImportModal(true)}>
              Import Reference Qwen3-4B GGUF
            </button>
          </div>
        ) : (
          models.map((m) => {
            const isLoaded = activeModelId === m.id;
            return (
              <div key={m.id} className="glass-card" style={{ display: 'flex', flexDirection: 'column', gap: '14px', position: 'relative' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                  <div>
                    <h3 style={{ fontFamily: 'var(--font-heading)', fontSize: '1.15rem', color: '#FFF' }}>{m.name}</h3>
                    <span style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>{m.fileName}</span>
                  </div>
                  {isLoaded ? (
                    <span className="badge badge-emerald">● LOADED</span>
                  ) : (
                    <span className="badge badge-indigo">READY</span>
                  )}
                </div>

                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
                  {m.quantization && <span className="badge badge-cyan">{m.quantization}</span>}
                  {m.architecture && <span className="badge badge-indigo">{m.architecture}</span>}
                  <span className="badge" style={{ background: 'rgba(255, 255, 255, 0.06)', color: 'var(--text-secondary)' }}>
                    {formatBytes(m.fileSize)}
                  </span>
                  {m.license && (
                    <span className="badge" style={{ background: 'rgba(255, 255, 255, 0.06)', color: 'var(--text-secondary)' }}>
                      {m.license}
                    </span>
                  )}
                </div>

                <div style={{ marginTop: 'auto', display: 'flex', gap: '8px', paddingTop: '10px', borderTop: '1px solid var(--border-subtle)' }}>
                  {isLoaded ? (
                    <button
                      className="btn-stop"
                      style={{ flex: 1, padding: '7px 12px', fontSize: '0.82rem' }}
                      onClick={onUnloadModel}
                    >
                      Unload
                    </button>
                  ) : (
                    <button
                      className="btn-new-chat"
                      style={{ flex: 1, padding: '7px 12px', fontSize: '0.82rem' }}
                      onClick={() => onLoadModel(m.id)}
                    >
                      Load Model
                    </button>
                  )}

                  <button
                    className="btn-icon"
                    style={{ border: '1px solid var(--border-subtle)', borderRadius: 'var(--radius-md)', padding: '6px 10px' }}
                    onClick={() => setSelectedModel(m)}
                    title="View Model Metadata"
                  >
                    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                      <circle cx="12" cy="12" r="10"></circle>
                      <line x1="12" y1="16" x2="12" y2="12"></line>
                      <line x1="12" y1="8" x2="12.01" y2="8"></line>
                    </svg>
                  </button>

                  <button
                    className="btn-icon"
                    style={{ border: '1px solid var(--border-subtle)', borderRadius: 'var(--radius-md)', padding: '6px 10px', opacity: isLoaded ? 0.3 : 1 }}
                    disabled={isLoaded}
                    onClick={() => onDeleteModel(m.id)}
                    title={isLoaded ? "Cannot delete loaded model" : "Delete model"}
                  >
                    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                      <polyline points="3 6 5 6 21 6"></polyline>
                      <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path>
                    </svg>
                  </button>
                </div>
              </div>
            );
          })
        )}
      </div>

      {/* Import Modal */}
      {showImportModal && (
        <div className="modal-overlay" onClick={() => setShowImportModal(false)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <h2 style={{ fontFamily: 'var(--font-heading)', marginBottom: '12px' }}>Import Local GGUF Model</h2>
            <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', marginBottom: '18px' }}>
              Enter the absolute or relative file path to a valid GGUF model file on your system.
            </p>

            <form onSubmit={handleImportSubmit}>
              <div className="form-group">
                <label className="form-label">Local File Path</label>
                <input
                  type="text"
                  className="form-input"
                  placeholder="e.g. C:\Downloads\Qwen3-4B-Q4_K_M.gguf"
                  value={importPath}
                  onChange={(e) => setImportPath(e.target.value)}
                  autoFocus
                />
              </div>

              {importError && (
                <div style={{ color: 'var(--accent-rose)', fontSize: '0.82rem', marginBottom: '14px' }}>
                  {importError}
                </div>
              )}

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '20px' }}>
                <button
                  type="button"
                  className="btn-icon"
                  style={{ border: '1px solid var(--border-subtle)', borderRadius: 'var(--radius-md)', padding: '8px 16px' }}
                  onClick={() => setShowImportModal(false)}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="btn-send"
                  disabled={isImporting || !importPath.trim()}
                >
                  {isImporting ? 'Validating...' : 'Import Model'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Metadata Inspector Modal */}
      {selectedModel && (
        <div className="modal-overlay" onClick={() => setSelectedModel(null)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()} style={{ maxWidth: '600px' }}>
            <h2 style={{ fontFamily: 'var(--font-heading)', marginBottom: '16px' }}>{selectedModel.name}</h2>
            
            <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', fontSize: '0.88rem' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid var(--border-subtle)', paddingBottom: '6px' }}>
                <span style={{ color: 'var(--text-muted)' }}>Architecture</span>
                <span>{selectedModel.architecture || 'Unknown'}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid var(--border-subtle)', paddingBottom: '6px' }}>
                <span style={{ color: 'var(--text-muted)' }}>Quantization</span>
                <span>{selectedModel.quantization || 'Unknown'}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid var(--border-subtle)', paddingBottom: '6px' }}>
                <span style={{ color: 'var(--text-muted)' }}>Context Length</span>
                <span>{selectedModel.contextLength || 4096} tokens</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid var(--border-subtle)', paddingBottom: '6px' }}>
                <span style={{ color: 'var(--text-muted)' }}>File Size</span>
                <span>{formatBytes(selectedModel.fileSize)}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid var(--border-subtle)', paddingBottom: '6px' }}>
                <span style={{ color: 'var(--text-muted)' }}>License</span>
                <span>{selectedModel.license || 'Unknown'}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid var(--border-subtle)', paddingBottom: '6px' }}>
                <span style={{ color: 'var(--text-muted)' }}>SHA-256</span>
                <span style={{ fontFamily: 'var(--font-mono)', fontSize: '0.75rem', maxWidth: '300px', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                  {selectedModel.sha256 || 'Pending check'}
                </span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid var(--border-subtle)', paddingBottom: '6px' }}>
                <span style={{ color: 'var(--text-muted)' }}>File Location</span>
                <span style={{ fontFamily: 'var(--font-mono)', fontSize: '0.75rem', maxWidth: '300px', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                  {selectedModel.filePath}
                </span>
              </div>
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '24px' }}>
              <button
                className="btn-new-chat"
                onClick={() => setSelectedModel(null)}
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

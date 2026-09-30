import React, { useState, useEffect } from 'react';

interface LogsViewProps {
  onFetchLogs: () => Promise<string[]>;
}

export const LogsView: React.FC<LogsViewProps> = ({ onFetchLogs }) => {
  const [logs, setLogs] = useState<string[]>([]);
  const [autoRefresh, setAutoRefresh] = useState(true);

  const refreshLogs = async () => {
    try {
      const data = await onFetchLogs();
      setLogs(data);
    } catch (_) {}
  };

  useEffect(() => {
    refreshLogs();
    if (!autoRefresh) return;
    const interval = setInterval(refreshLogs, 2000);
    return () => clearInterval(interval);
  }, [autoRefresh]);

  return (
    <div className="view-container">
      <div className="view-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <h1 className="view-title">Inference Engine Logs</h1>
          <p className="view-subtitle">Live output from the local llama.cpp child process and host API.</p>
        </div>
        <div style={{ display: 'flex', gap: '10px' }}>
          <button
            className={`btn-icon ${autoRefresh ? 'active' : ''}`}
            style={{ padding: '6px 12px', border: '1px solid var(--border-subtle)', borderRadius: 'var(--radius-md)' }}
            onClick={() => setAutoRefresh(!autoRefresh)}
          >
            {autoRefresh ? '● Live Refresh' : 'Paused'}
          </button>
          <button
            className="btn-new-chat"
            style={{ padding: '6px 14px', fontSize: '0.82rem' }}
            onClick={refreshLogs}
          >
            Refresh Now
          </button>
        </div>
      </div>

      <div
        className="glass-card"
        style={{
          fontFamily: 'var(--font-mono)',
          fontSize: '0.8rem',
          lineHeight: '1.6',
          height: 'calc(100vh - 210px)',
          overflowY: 'auto',
          background: '#06090F',
          padding: '16px',
          color: '#E2E8F0'
        }}
      >
        {logs.length === 0 ? (
          <span style={{ color: 'var(--text-muted)' }}>No logs captured yet. Load a model or send a message to generate inference logs.</span>
        ) : (
          logs.map((line, idx) => (
            <div key={idx} style={{ wordBreak: 'break-all', whiteSpace: 'pre-wrap' }}>
              {line}
            </div>
          ))
        )}
      </div>
    </div>
  );
};

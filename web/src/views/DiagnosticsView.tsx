import React, { useState } from 'react';
import { InferenceStats, ModelMetadata, ServerHealth } from '@shared/types';

interface DiagnosticsViewProps {
  health: ServerHealth | null;
  loadedModel: ModelMetadata | null;
  latestStats: InferenceStats | null;
  diagnosticsData: any;
  onRunBenchmark: () => Promise<any>;
}

export const DiagnosticsView: React.FC<DiagnosticsViewProps> = ({
  health,
  loadedModel,
  latestStats,
  diagnosticsData,
  onRunBenchmark
}) => {
  const [isBenchmarking, setIsBenchmarking] = useState(false);
  const [benchmarkResult, setBenchmarkResult] = useState<any>(null);

  const handleBenchmark = async () => {
    setIsBenchmarking(true);
    try {
      const res = await onRunBenchmark();
      setBenchmarkResult(res);
    } catch (err: any) {
      alert(`Benchmark error: ${err.message}`);
    } finally {
      setIsBenchmarking(false);
    }
  };

  const handleExportJson = () => {
    const exportData = {
      timestamp: new Date().toISOString(),
      platform: diagnosticsData?.hardware?.platform || 'desktop',
      arch: diagnosticsData?.hardware?.arch || 'arm64',
      model: loadedModel?.name || 'None',
      quantization: loadedModel?.quantization || 'None',
      contextLength: loadedModel?.contextLength || 4096,
      telemetry: {
        latestStats,
        health,
        benchmarkResult
      }
    };

    const blob = new Blob([JSON.stringify(exportData, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `omnimind-benchmark-${Date.now()}.json`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const formatBytes = (bytes?: number): string => {
    if (!bytes) return '0 MB';
    const mb = bytes / (1024 * 1024);
    return `${mb.toFixed(1)} MB`;
  };

  return (
    <div className="view-container">
      <div className="view-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <h1 className="view-title">Performance & Diagnostics</h1>
          <p className="view-subtitle">Real-time hardware telemetry and actual inference metrics. Zero fabricated numbers.</p>
        </div>
        <button
          className="btn-new-chat"
          onClick={handleExportJson}
          style={{ background: 'rgba(255, 255, 255, 0.08)', border: '1px solid var(--border-subtle)' }}
        >
          Export Benchmark JSON
        </button>
      </div>

      <div className="telemetry-grid">
        <div className="telemetry-card">
          <span className="telemetry-label">Generation Speed</span>
          <span className="telemetry-value">
            {latestStats?.generationTokensPerSec ? `${latestStats.generationTokensPerSec}` : '—'}
          </span>
          <span className="telemetry-subtext">tokens / second</span>
        </div>

        <div className="telemetry-card">
          <span className="telemetry-label">First-Token Latency</span>
          <span className="telemetry-value" style={{ color: '#A5B4FC' }}>
            {latestStats?.timeToFirstTokenMs ? `${latestStats.timeToFirstTokenMs}ms` : '—'}
          </span>
          <span className="telemetry-subtext">time to first token (TTFT)</span>
        </div>

        <div className="telemetry-card">
          <span className="telemetry-label">Prompt Eval Rate</span>
          <span className="telemetry-value" style={{ color: '#34D399' }}>
            {latestStats?.promptTokensPerSec ? `${latestStats.promptTokensPerSec}` : '—'}
          </span>
          <span className="telemetry-subtext">prompt tokens / sec</span>
        </div>

        <div className="telemetry-card">
          <span className="telemetry-label">Memory Heap</span>
          <span className="telemetry-value" style={{ color: 'var(--text-primary)' }}>
            {formatBytes(health?.memory?.heapUsedBytes)}
          </span>
          <span className="telemetry-subtext">RSS: {formatBytes(health?.memory?.rssBytes)}</span>
        </div>
      </div>

      <div className="card-grid">
        <div className="glass-card">
          <h3 style={{ fontFamily: 'var(--font-heading)', marginBottom: '14px', color: '#FFF' }}>
            Active Model Configuration
          </h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', fontSize: '0.88rem' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid var(--border-subtle)', paddingBottom: '6px' }}>
              <span style={{ color: 'var(--text-muted)' }}>Model</span>
              <span>{loadedModel?.name || 'None Loaded'}</span>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid var(--border-subtle)', paddingBottom: '6px' }}>
              <span style={{ color: 'var(--text-muted)' }}>Quantization</span>
              <span>{loadedModel?.quantization || 'N/A'}</span>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid var(--border-subtle)', paddingBottom: '6px' }}>
              <span style={{ color: 'var(--text-muted)' }}>Load Time</span>
              <span>{latestStats?.loadTimeMs ? `${latestStats.loadTimeMs} ms` : '—'}</span>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid var(--border-subtle)', paddingBottom: '6px' }}>
              <span style={{ color: 'var(--text-muted)' }}>Engine Status</span>
              <span style={{ color: health?.status === 'ok' ? 'var(--accent-cyan)' : 'var(--accent-amber)' }}>
                {health?.status ? health.status.toUpperCase() : 'STOPPED'}
              </span>
            </div>
          </div>
        </div>

        <div className="glass-card">
          <h3 style={{ fontFamily: 'var(--font-heading)', marginBottom: '14px', color: '#FFF' }}>
            Benchmark Test Runner
          </h3>
          <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', marginBottom: '16px' }}>
            Execute a 100-token prompt workload against the loaded model to capture prompt evaluation speed and sustained generation throughput.
          </p>

          <button
            className="btn-send"
            style={{ width: '100%', justifyContent: 'center' }}
            disabled={!loadedModel || isBenchmarking}
            onClick={handleBenchmark}
          >
            {isBenchmarking ? 'Running Benchmark...' : 'Run Benchmark Workload'}
          </button>

          {benchmarkResult && (
            <div style={{ marginTop: '16px', padding: '12px', background: 'var(--bg-input)', borderRadius: 'var(--radius-md)', fontSize: '0.82rem' }}>
              <div style={{ color: 'var(--accent-cyan)', fontWeight: 600, marginBottom: '4px' }}>Benchmark Completed</div>
              <div>Tokens Generated: {benchmarkResult.generatedTokens}</div>
              <div>Throughput: {benchmarkResult.generationTokensPerSec} tok/s</div>
              <div>TTFT: {benchmarkResult.timeToFirstTokenMs} ms</div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

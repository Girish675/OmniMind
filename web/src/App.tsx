import { useState, useEffect, useRef } from 'react';
import { OmniMindClient } from '@shared/client';
import { AppSettings, Conversation, InferenceStats, Message, ModelMetadata, ServerHealth } from '@shared/types';
import { Sidebar } from './components/Sidebar';
import { ChatView } from './views/ChatView';
import { ModelsView } from './views/ModelsView';
import { DiagnosticsView } from './views/DiagnosticsView';
import { SettingsView } from './views/SettingsView';
import { LogsView } from './views/LogsView';
import { AboutView } from './views/AboutView';

export function App() {
  const [activeTab, setActiveTab] = useState<'chat' | 'models' | 'diagnostics' | 'settings' | 'logs' | 'about'>('chat');
  const [serverUrl, setServerUrl] = useState(() => localStorage.getItem('omnimind_server_url') || 'http://localhost:8080');
  const [authToken, setAuthToken] = useState(() => localStorage.getItem('omnimind_auth_token') || '');

  const clientRef = useRef(new OmniMindClient(serverUrl, authToken));

  const [health, setHealth] = useState<ServerHealth | null>(null);
  const [models, setModels] = useState<ModelMetadata[]>([]);
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [activeConversationId, setActiveConversationId] = useState<string | null>(null);
  const [messages, setMessages] = useState<Message[]>([]);
  const [settings, setSettings] = useState<AppSettings | null>(null);
  const [diagnosticsData, setDiagnosticsData] = useState<any>(null);

  const [isGenerating, setIsGenerating] = useState(false);
  const [streamingText, setStreamingText] = useState('');
  const [activeStats, setActiveStats] = useState<InferenceStats | null>(null);
  const [searchQuery, setSearchQuery] = useState('');

  const cancelStreamRef = useRef<(() => void) | null>(null);

  // Update client when serverUrl or authToken changes
  useEffect(() => {
    clientRef.current = new OmniMindClient(serverUrl, authToken);
    localStorage.setItem('omnimind_server_url', serverUrl);
    if (authToken) localStorage.setItem('omnimind_auth_token', authToken);
  }, [serverUrl, authToken]);

  // Initial data fetch and polling health
  const refreshAll = async () => {
    try {
      const h = await clientRef.current.getHealth();
      setHealth(h);

      const m = await clientRef.current.getModels();
      setModels(m);

      const convs = await clientRef.current.getConversations();
      setConversations(convs);

      if (!activeConversationId && convs.length > 0) {
        setActiveConversationId(convs[0].id);
      }

      const s = await clientRef.current.getSettings();
      setSettings(s);

      const diag = await clientRef.current.getDiagnostics();
      setDiagnosticsData(diag);
    } catch (_) {
      setHealth(null);
    }
  };

  useEffect(() => {
    refreshAll();
    const interval = setInterval(async () => {
      try {
        const h = await clientRef.current.getHealth();
        setHealth(h);
      } catch (_) {
        setHealth(null);
      }
    }, 4000);
    return () => clearInterval(interval);
  }, [serverUrl, authToken]);

  // Load messages when active conversation changes
  useEffect(() => {
    if (!activeConversationId) {
      setMessages([]);
      return;
    }
    clientRef.current.getConversation(activeConversationId).then(data => {
      setMessages(data.messages || []);
    }).catch(() => {});
  }, [activeConversationId]);

  // Global Keyboard Shortcuts (Ctrl+N, Esc)
  useEffect(() => {
    const handleGlobalKey = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'n') {
        e.preventDefault();
        handleNewConversation();
      } else if (e.key === 'Escape' && isGenerating) {
        e.preventDefault();
        handleStopGeneration();
      }
    };
    window.addEventListener('keydown', handleGlobalKey);
    return () => window.removeEventListener('keydown', handleGlobalKey);
  }, [isGenerating]);

  const handleNewConversation = async () => {
    try {
      const newConv = await clientRef.current.createConversation('New Chat');
      setConversations(prev => [newConv, ...prev]);
      setActiveConversationId(newConv.id);
      setMessages([]);
      setActiveTab('chat');
    } catch (err: any) {
      alert(`Could not create conversation: ${err.message}`);
    }
  };

  const handleDeleteConversation = async (id: string, e: React.MouseEvent) => {
    e.stopPropagation();
    if (!confirm('Are you sure you want to delete this conversation?')) return;
    try {
      await clientRef.current.deleteConversation(id);
      setConversations(prev => prev.filter(c => c.id !== id));
      if (activeConversationId === id) {
        const remaining = conversations.filter(c => c.id !== id);
        setActiveConversationId(remaining.length > 0 ? remaining[0].id : null);
      }
    } catch (err: any) {
      alert(`Could not delete conversation: ${err.message}`);
    }
  };

  const handleSelectModel = async (id: string) => {
    try {
      await clientRef.current.loadModel(id);
      await refreshAll();
    } catch (err: any) {
      alert(`Failed to load model: ${err.message}`);
    }
  };

  const handleUnloadModel = async () => {
    try {
      await clientRef.current.unloadModel();
      await refreshAll();
    } catch (err: any) {
      alert(`Failed to unload model: ${err.message}`);
    }
  };

  const handleDeleteModel = async (id: string) => {
    if (!confirm('Are you sure you want to delete this model file from disk?')) return;
    try {
      await clientRef.current.deleteModel(id);
      await refreshAll();
    } catch (err: any) {
      alert(`Failed to delete model: ${err.message}`);
    }
  };

  const handleImportModel = async (sourcePath: string) => {
    const res = await fetch(`${serverUrl}/models/import`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(authToken ? { 'Authorization': `Bearer ${authToken}` } : {})
      },
      body: JSON.stringify({ sourceFilePath: sourcePath })
    });
    if (!res.ok) {
      const err = await res.json();
      throw new Error(err?.error?.message || 'Import failed');
    }
    await refreshAll();
  };

  const handleSendMessage = async (content: string, systemPrompt?: string, genSettings?: any) => {
    let convId = activeConversationId;
    if (!convId) {
      const newConv = await clientRef.current.createConversation(content.slice(0, 30));
      setConversations(prev => [newConv, ...prev]);
      setActiveConversationId(newConv.id);
      convId = newConv.id;
    }

    const userMessage: Message = {
      id: 'usr_' + Date.now(),
      conversationId: convId,
      role: 'user',
      content,
      createdAt: Date.now(),
      sequenceNumber: messages.length + 1,
      isPartial: false
    };

    setMessages(prev => [...prev, userMessage]);
    setIsGenerating(true);
    setStreamingText('');

    const chatHistory = [...messages, userMessage].map(m => ({
      role: m.role,
      content: m.content
    }));

    const cancel = clientRef.current.chatCompletionStream(
      {
        conversationId: convId,
        messages: chatHistory,
        systemPrompt,
        settings: genSettings
      },
      {
        onToken: (tok) => {
          setStreamingText(prev => prev + tok);
        },
        onStats: (stats) => {
          setActiveStats(stats);
        },
        onDone: (res) => {
          setIsGenerating(false);
          setStreamingText('');
          if (res && res.message) {
            setMessages(prev => [
              ...prev,
              {
                id: res.id,
                conversationId: convId!,
                role: 'assistant',
                content: res.message.content,
                createdAt: Date.now(),
                sequenceNumber: prev.length + 1,
                isPartial: false,
                generationStats: res.stats
              }
            ]);
          }
          refreshAll();
        },
        onError: (err) => {
          setIsGenerating(false);
          setStreamingText('');
          alert(`Inference error: ${err.message}`);
        }
      }
    );

    cancelStreamRef.current = cancel;
  };

  const handleStopGeneration = () => {
    if (cancelStreamRef.current) {
      cancelStreamRef.current();
      cancelStreamRef.current = null;
    }
    clientRef.current.stopChat(activeConversationId || undefined).catch(() => {});
    setIsGenerating(false);
  };

  const handleRunBenchmark = async () => {
    return clientRef.current.chatCompletion({
      messages: [{ role: 'user', content: 'Benchmark: Generate a 100 token response to test hardware inference throughput.' }],
      settings: { maxTokens: 100 }
    }).then(res => {
      setActiveStats(res.stats);
      return res.stats;
    });
  };

  const handleSaveSettings = async (newSettings: Partial<AppSettings>) => {
    if (newSettings.authToken !== undefined) {
      setAuthToken(newSettings.authToken);
    }
    await clientRef.current.updateSettings(newSettings);
    await refreshAll();
  };

  const activeConv = conversations.find(c => c.id === activeConversationId) || null;
  const loadedModel = models.find(m => m.id === health?.loadedModelId) || null;

  return (
    <div className="app-container">
      <Sidebar
        activeTab={activeTab}
        setActiveTab={setActiveTab}
        conversations={conversations}
        activeConversationId={activeConversationId}
        onSelectConversation={setActiveConversationId}
        onNewConversation={handleNewConversation}
        onDeleteConversation={handleDeleteConversation}
        models={models}
        health={health}
        searchQuery={searchQuery}
        setSearchQuery={setSearchQuery}
      />

      {activeTab === 'chat' && (
        <ChatView
          conversation={activeConv}
          messages={messages}
          models={models}
          activeModelId={health?.loadedModelId || (models[0]?.id ?? null)}
          onSelectModel={handleSelectModel}
          onSendMessage={handleSendMessage}
          onStopGeneration={handleStopGeneration}
          isGenerating={isGenerating}
          streamingText={streamingText}
          activeStats={activeStats}
          onClearConversation={() => setMessages([])}
        />
      )}

      {activeTab === 'models' && (
        <ModelsView
          models={models}
          activeModelId={health?.loadedModelId || null}
          onLoadModel={handleSelectModel}
          onUnloadModel={handleUnloadModel}
          onDeleteModel={handleDeleteModel}
          onImportModel={handleImportModel}
        />
      )}

      {activeTab === 'diagnostics' && (
        <DiagnosticsView
          health={health}
          loadedModel={loadedModel}
          latestStats={activeStats}
          diagnosticsData={diagnosticsData}
          onRunBenchmark={handleRunBenchmark}
        />
      )}

      {activeTab === 'settings' && (
        <SettingsView
          settings={settings}
          serverUrl={serverUrl}
          onUpdateServerUrl={setServerUrl}
          onSaveSettings={handleSaveSettings}
        />
      )}

      {activeTab === 'logs' && (
        <LogsView
          onFetchLogs={() => clientRef.current.getLogs().then(r => r.logs)}
        />
      )}

      {activeTab === 'about' && (
        <AboutView />
      )}
    </div>
  );
}
export default App;

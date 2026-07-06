import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import { Key, Plus, Trash2, Edit, RotateCw, HelpCircle, X, ArrowLeft, LogOut, Copy, Check, Info, ShieldAlert } from 'lucide-react';

export default function APIKeys({ onNavigate }) {
  const { logout, apiFetch } = useAuth();
  const [apiKeys, setApiKeys] = useState([]);
  const [credentials, setCredentials] = useState([]);
  
  // Form states
  const [name, setName] = useState('');
  const [selectedCreds, setSelectedCreds] = useState([]);
  const [generatedKey, setGeneratedKey] = useState('');
  const [copied, setCopied] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  
  // Modals state
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [isEditMode, setIsEditMode] = useState(false);
  const [editingKeyId, setEditingKeyId] = useState(null);
  
  // Help Modal states
  const [isHelpOpen, setIsHelpOpen] = useState(false);
  const [helpKeyPrefix, setHelpKeyPrefix] = useState('');

  useEffect(() => {
    fetchAPIKeys();
    fetchCredentials();
  }, []);

  const fetchAPIKeys = async () => {
    try {
      const response = await apiFetch('/api/apikeys');
      if (response.ok) {
        const data = await response.json();
        setApiKeys(data);
      } else {
        setError('Failed to fetch API keys');
      }
    } catch (err) {
      setError('Server connection error');
    }
  };

  const fetchCredentials = async () => {
    try {
      const response = await apiFetch('/api/credentials');
      if (response.ok) {
        const data = await response.json();
        setCredentials(data);
      }
    } catch (err) {
      console.error(err);
    }
  };

  const handleCreateOrUpdate = async (e) => {
    e.preventDefault();
    setError('');
    setGeneratedKey('');

    if (selectedCreds.length === 0) {
      setError('Please select at least one credential for authorization');
      return;
    }

    try {
      if (isEditMode) {
        const response = await apiFetch(`/api/apikeys/${editingKeyId}`, {
          method: 'PUT',
          body: JSON.stringify({
            name: name,
            credential_ids: selectedCreds
          })
        });

        if (response.ok) {
          setSuccess('API Key permissions updated successfully');
          setIsModalOpen(false);
          setName('');
          setSelectedCreds([]);
          fetchAPIKeys();
        } else {
          const text = await response.text();
          setError(text || 'Failed to update API Key');
        }
      } else {
        const response = await apiFetch('/api/apikeys', {
          method: 'POST',
          body: JSON.stringify({
            name: name,
            credential_ids: selectedCreds
          })
        });

        if (response.ok) {
          const data = await response.json();
          setGeneratedKey(data.key);
          setName('');
          setSelectedCreds([]);
          fetchAPIKeys();
        } else {
          const text = await response.text();
          setError(text || 'Failed to generate API Key');
        }
      }
    } catch (err) {
      setError('Connection to backend failed');
    }
  };

  const handleRotate = async (key) => {
    const confirmMsg = `Are you sure you want to rotate "${key.name}"?\n` +
      `The existing key will stop working immediately, and a new key will be generated with the same credentials permission mapping.`;
    
    if (!window.confirm(confirmMsg)) return;
    setError('');
    setSuccess('');
    setGeneratedKey('');

    try {
      const response = await apiFetch(`/api/apikeys/${key.id}/rotate`, {
        method: 'POST'
      });

      if (response.ok) {
        const data = await response.json();
        setGeneratedKey(data.key);
        setSuccess('API Key rotated successfully');
        setIsModalOpen(true);
        setIsEditMode(false);
        fetchAPIKeys();
      } else {
        setError('Failed to rotate API key');
      }
    } catch (err) {
      setError('Connection error');
    }
  };

  const handleDelete = async (id) => {
    if (!window.confirm('Are you sure you want to revoke this API Key? Credentials relying on it will lose access.')) return;
    setError('');
    setSuccess('');

    try {
      const response = await apiFetch(`/api/apikeys/${id}`, {
        method: 'DELETE'
      });

      if (response.ok) {
        setSuccess('API Key revoked');
        fetchAPIKeys();
      } else {
        setError('Failed to revoke API key');
      }
    } catch (err) {
      setError('Connection error');
    }
  };

  const openEditModal = (key) => {
    setError('');
    setGeneratedKey('');
    setName(key.name);
    setSelectedCreds(key.credential_ids);
    setEditingKeyId(key.id);
    setIsEditMode(true);
    setIsModalOpen(true);
  };

  const openCreateModal = () => {
    setError('');
    setGeneratedKey('');
    setName('');
    setSelectedCreds([]);
    setEditingKeyId(null);
    setIsEditMode(false);
    setIsModalOpen(true);
  };

  const openHelpModal = (key) => {
    setHelpKeyPrefix(key.prefix);
    setIsHelpOpen(true);
  };

  const toggleCredential = (id) => {
    if (selectedCreds.includes(id)) {
      setSelectedCreds(selectedCreds.filter(cid => cid !== id));
    } else {
      setSelectedCreds([...selectedCreds, id]);
    }
  };

  const copyKey = () => {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(generatedKey);
    } else {
      const textArea = document.createElement("textarea");
      textArea.value = generatedKey;
      textArea.style.position = "fixed";
      document.body.appendChild(textArea);
      textArea.focus();
      textArea.select();
      try {
        document.execCommand('copy');
      } catch (err) {
        console.error('Copy fallback failed', err);
      }
      document.body.removeChild(textArea);
    }
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="min-h-screen bg-darkBg text-textLight">
      {/* Top Navbar */}
      <nav className="glass-panel border-b border-gray-800 px-6 py-4 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <Key className="w-6 h-6 text-primaryNeon" />
          <span className="font-bold text-white text-lg tracking-wider uppercase">SecretSafe API Keys</span>
        </div>
        <div className="flex items-center gap-4">
          <button
            onClick={() => onNavigate('dashboard')}
            className="flex items-center gap-2 px-4 py-2 rounded-lg bg-gray-800 hover:bg-gray-700 text-white transition text-sm font-medium"
          >
            <ArrowLeft className="w-4 h-4" />
            Credentials Vault
          </button>
          <button
            onClick={logout}
            className="flex items-center gap-2 px-4 py-2 rounded-lg bg-red-600/10 hover:bg-red-600/20 text-red-400 border border-red-500/25 transition text-sm font-medium"
          >
            <LogOut className="w-4 h-4" />
            Sign Out
          </button>
        </div>
      </nav>

      {/* Main Content */}
      <div className="max-w-5xl mx-auto px-6 py-10">
        {error && (
          <div className="p-4 mb-6 bg-red-500/10 border border-red-500/25 text-red-400 rounded-xl text-sm">
            {error}
          </div>
        )}

        {success && (
          <div className="p-4 mb-6 bg-emerald-500/10 border border-emerald-500/25 text-emerald-400 rounded-xl text-sm">
            {success}
          </div>
        )}

        <div className="flex items-center justify-between mb-8">
          <div>
            <h1 className="text-3xl font-bold text-white tracking-wide">Developer Integrations</h1>
            <p className="text-sm text-gray-400 mt-1">Generate hashed API keys to query individual password credentials securely</p>
          </div>
          <button
            onClick={openCreateModal}
            className="flex items-center gap-2 bg-primaryNeon hover:opacity-95 text-darkBg font-bold px-5 py-3 rounded-xl text-sm transition shadow-lg shadow-primaryNeon/10"
          >
            <Plus className="w-5 h-5" />
            Generate API Key
          </button>
        </div>

        {/* API Keys Table */}
        {apiKeys.length === 0 ? (
          <div className="glass-panel p-12 rounded-2xl text-center border border-gray-800 flex flex-col items-center">
            <Key className="w-12 h-12 text-gray-600 mb-4" />
            <p className="text-gray-400 font-medium">No API Keys created yet.</p>
            <p className="text-sm text-gray-600 mt-1">Create an API Key to query passwords programmatically from other services.</p>
          </div>
        ) : (
          <div className="glass-panel rounded-2xl border border-gray-800 overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="text-xs uppercase bg-darkBg/60 text-gray-400 border-b border-gray-800">
                  <tr>
                    <th className="py-3.5 px-6">Name</th>
                    <th className="py-3.5 px-6">Identifier Prefix</th>
                    <th className="py-3.5 px-6">Allowed Credentials</th>
                    <th className="py-3.5 px-6">Created At</th>
                    <th className="py-3.5 px-6 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-800/50">
                  {apiKeys.map((key) => (
                    <tr key={key.id} className="hover:bg-gray-800/30 transition">
                      <td className="py-4 px-6 font-semibold text-white">{key.name}</td>
                      <td className="py-4 px-6 font-mono text-xs text-primaryNeon">{key.prefix}•••••</td>
                      <td className="py-4 px-6 max-w-xs">
                        <div className="flex flex-wrap gap-1.5">
                          {key.credential_ids.map(cid => {
                            const cred = credentials.find(c => c.id === cid);
                            return cred ? (
                              <span key={cid} className="px-2 py-0.5 rounded bg-gray-800 text-xs border border-gray-700/60 text-gray-300">
                                {cred.name}
                              </span>
                            ) : null;
                          })}
                        </div>
                      </td>
                      <td className="py-4 px-6 text-xs text-gray-500">
                        {new Date(key.created_at).toLocaleString()}
                      </td>
                      <td className="py-4 px-6 text-right">
                        <div className="flex items-center justify-end gap-2">
                          <button
                            onClick={() => openHelpModal(key)}
                            title="API Usage Documentation (?)"
                            className="p-2 rounded-lg bg-emerald-950/20 hover:bg-emerald-950/40 text-emerald-400 border border-emerald-500/10 hover:border-emerald-500/25 transition"
                          >
                            <HelpCircle className="w-4 h-4" />
                          </button>
                          <button
                            onClick={() => openEditModal(key)}
                            title="Edit Permissions"
                            className="p-2 rounded-lg bg-gray-800 hover:bg-gray-700 text-gray-400 hover:text-white transition border border-gray-700"
                          >
                            <Edit className="w-4 h-4" />
                          </button>
                          <button
                            onClick={() => handleRotate(key)}
                            title="Rotate Key"
                            className="p-2 rounded-lg bg-yellow-950/20 hover:bg-yellow-950/40 text-yellow-400 border border-yellow-500/10 hover:border-yellow-500/25 transition"
                          >
                            <RotateCw className="w-4 h-4" />
                          </button>
                          <button
                            onClick={() => handleDelete(key.id)}
                            title="Delete / Revoke Key"
                            className="p-2 rounded-lg bg-red-950/20 hover:bg-red-950/40 text-red-400 border border-red-500/10 hover:border-red-500/25 transition"
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </div>

      {/* Edit / Generate API Key Modal Dialog */}
      {isModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-4">
          <div className="w-full max-w-lg glass-panel p-6 rounded-2xl border border-gray-800 relative">
            <button
              onClick={() => setIsModalOpen(false)}
              className="absolute top-4 right-4 text-gray-500 hover:text-white"
            >
              <X className="w-5 h-5" />
            </button>

            {!generatedKey ? (
              <>
                <h3 className="text-xl font-bold text-white mb-6 flex items-center gap-2">
                  <Key className="w-5 h-5 text-primaryNeon" />
                  {isEditMode ? 'Edit API Key Permissions' : 'Generate API Key'}
                </h3>

                <form onSubmit={handleCreateOrUpdate} className="space-y-5">
                  <div>
                    <label className="block text-xs uppercase text-gray-400 mb-1.5 font-semibold">Key Identifier Name</label>
                    <input
                      type="text"
                      required
                      className="w-full bg-darkBg border border-gray-800 rounded-lg py-2.5 px-3 text-white text-sm focus:outline-none focus:border-primaryNeon"
                      placeholder="e.g. Browser Extension, CI Pipeline"
                      value={name}
                      onChange={e => setName(e.target.value)}
                    />
                  </div>

                  <div>
                    <label className="block text-xs uppercase text-gray-400 mb-2 font-semibold">Select Authorized Credentials</label>
                    <div className="max-h-48 overflow-y-auto border border-gray-800 rounded-lg p-3 bg-darkBg/50 divide-y divide-gray-800/40 space-y-1.5">
                      {credentials.map(cred => (
                        <div key={cred.id} className="flex items-center justify-between py-1.5">
                          <span className="text-sm font-medium text-gray-300">{cred.name} <span className="text-2xs text-gray-500 font-mono">({cred.username})</span></span>
                          <input
                            type="checkbox"
                            checked={selectedCreds.includes(cred.id)}
                            onChange={() => toggleCredential(cred.id)}
                            className="rounded border-gray-700 bg-darkBg text-primaryNeon focus:ring-primaryNeon w-4 h-4 cursor-pointer"
                          />
                        </div>
                      ))}
                    </div>
                  </div>

                  <button
                    type="submit"
                    className="w-full bg-primaryNeon text-darkBg font-bold py-3 rounded-lg text-sm mt-4 hover:opacity-95 transition"
                  >
                    {isEditMode ? 'Save Changes' : 'Generate Secure Key'}
                  </button>
                </form>
              </>
            ) : (
              <div className="text-center py-4 flex flex-col items-center">
                <div className="p-4 bg-primaryNeon/10 border border-primaryNeon/25 rounded-full text-primaryNeon mb-4">
                  <ShieldAlert className="w-12 h-12" />
                </div>
                <h3 className="text-2xl font-bold text-white mb-2">API Key Credentials Safe</h3>
                <p className="text-sm text-yellow-400 px-2 mb-6 font-semibold">
                  Warning: Copy this new API Key now. For security reasons, it is hashed on the server and you cannot view it again!
                </p>

                <div className="w-full bg-darkBg border border-gray-800 rounded-xl p-4 flex items-center justify-between mb-8">
                  <span className="text-sm font-mono text-primaryNeon select-all tracking-wide break-all text-left pr-3">{generatedKey}</span>
                  <button
                    onClick={copyKey}
                    className="p-2 rounded-lg bg-gray-800 hover:bg-gray-700 text-gray-400 hover:text-white transition flex-shrink-0"
                  >
                    {copied ? <Check className="w-5 h-5 text-primaryNeon" /> : <Copy className="w-5 h-5" />}
                  </button>
                </div>

                <button
                  onClick={() => setIsModalOpen(false)}
                  className="px-8 py-3 bg-primaryNeon text-darkBg font-bold rounded-xl text-sm transition hover:opacity-95"
                >
                  I've Copied the Key
                </button>
              </div>
            )}
          </div>
        </div>
      )}

      {/* API Help & Usage Documentation Modal */}
      {isHelpOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-4">
          <div className="w-full max-w-2xl glass-panel p-6 rounded-2xl border border-gray-800 relative">
            <button
              onClick={() => setIsHelpOpen(false)}
              className="absolute top-4 right-4 text-gray-500 hover:text-white"
            >
              <X className="w-5 h-5" />
            </button>

            <h3 className="text-xl font-bold text-white mb-6 flex items-center gap-2">
              <HelpCircle className="w-5 h-5 text-primaryNeon" />
              API Usage Documentation
            </h3>

            <div className="space-y-6 text-sm text-gray-300">
              <div>
                <p className="mb-2 font-semibold text-white">1. Authenticate Request Header</p>
                <p className="text-gray-400 mb-3 text-xs">
                  Authenticate by passing the API key in the <code className="px-1.5 py-0.5 rounded bg-gray-800 text-primaryNeon font-mono text-2xs">X-API-Key</code> request header.
                </p>
              </div>

              <div>
                <p className="mb-2 font-semibold text-white">2. Get Authorized Credentials List</p>
                <p className="text-gray-400 mb-3 text-xs">
                  List all credentials that this specific API key is authorized to access (returns IDs, names, usernames, and site addresses).
                </p>
                <pre className="bg-darkBg border border-gray-800 p-3 rounded-lg font-mono text-2xs text-primaryNeon overflow-x-auto select-all">
{`curl -X GET ${window.location.protocol}//${window.location.hostname}:8080/api/credentials \\
  -H "X-API-Key: ${helpKeyPrefix}••••••••"`}
                </pre>
              </div>

              <div>
                <p className="mb-2 font-semibold text-white">3. Retrieve Password (Requires Android Biometric Approval)</p>
                <p className="text-gray-400 mb-3 text-xs">
                  Retrieve the decrypted password for a specific credential. Calling this endpoint immediately pushes a confirmation alert to your Android app. The request blocks until approved.
                </p>
                <pre className="bg-darkBg border border-gray-800 p-3 rounded-lg font-mono text-2xs text-primaryNeon overflow-x-auto select-all">
{`curl -X GET ${window.location.protocol}//${window.location.hostname}:8080/api/credentials/{id}/retrieve \\
  -H "X-API-Key: ${helpKeyPrefix}••••••••"`}
                </pre>
              </div>
            </div>

            <button
              onClick={() => setIsHelpOpen(false)}
              className="w-full bg-primaryNeon text-darkBg font-bold py-3 rounded-lg text-sm mt-6 hover:opacity-95 transition"
            >
              Dismiss Documentation
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

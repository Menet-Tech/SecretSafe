import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import { config } from '../config';
import {
  Shield, Key, Plus, Globe, User, Eye, EyeOff, Trash2, Edit, Copy,
  LogOut, ShieldAlert, Check, X, Smartphone, AlertCircle, Loader, Settings
} from 'lucide-react';

export default function Dashboard({ onNavigate }) {
  const { user, logout, apiFetch, serverUrl, updateServerUrl } = useAuth();
  const [credentials, setCredentials] = useState([]);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  // Modals state
  const [isAddModalOpen, setIsAddModalOpen] = useState(false);
  const [isEditModalOpen, setIsEditModalOpen] = useState(false);
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);
  const [tempServerUrl, setTempServerUrl] = useState(serverUrl);
  const [selectedCred, setSelectedCred] = useState(null);

  const [serverStatus, setServerStatus] = useState('checking'); // 'checking' | 'connected' | 'disconnected'
  const [testStatus, setTestStatus] = useState(null); // null | 'testing' | 'success' | 'error'
  const [testError, setTestError] = useState('');

  useEffect(() => {
    const checkConnection = async () => {
      try {
        const response = await fetch(`${serverUrl}/api/health`);
        if (response.ok) {
          setServerStatus('connected');
        } else {
          setServerStatus('disconnected');
        }
      } catch (err) {
        setServerStatus('disconnected');
      }
    };

    checkConnection();
    const interval = setInterval(checkConnection, 5000);
    return () => clearInterval(interval);
  }, [serverUrl]);

  useEffect(() => {
    setTempServerUrl(serverUrl);
  }, [serverUrl]);

  const handleTestConnection = async () => {
    setTestStatus('testing');
    setTestError('');
    try {
      const cleanUrl = tempServerUrl.replace(/\/$/, '');
      const response = await fetch(`${cleanUrl}/api/health`, {
        method: 'GET',
        headers: { 'Accept': 'application/json' }
      });
      if (response.ok) {
        const data = await response.json();
        if (data.status === 'ok') {
          setTestStatus('success');
        } else {
          setTestStatus('error');
          setTestError('Invalid health response format');
        }
      } else {
        setTestStatus('error');
        setTestError(`Server returned status code: ${response.status}`);
      }
    } catch (err) {
      setTestStatus('error');
      setTestError(err.message || 'Network error / server unreachable. Make sure protocol and port are correct.');
    }
  };

  // Form states
  const [credName, setCredName] = useState('');
  const [credUsername, setCredUsername] = useState('');
  const [credPassword, setCredPassword] = useState('');
  const [credAddress, setCredAddress] = useState('');

  // Password retrieval states
  const [retrievingId, setRetrievingId] = useState(null);
  const [approvalModalOpen, setApprovalModalOpen] = useState(false);
  const [decryptedCred, setDecryptedCred] = useState(null);
  const [retrievalError, setRetrievalError] = useState('');
  const [copiedId, setCopiedId] = useState(null);

  useEffect(() => {
    fetchCredentials();
  }, []);

  const fetchCredentials = async () => {
    try {
      const response = await apiFetch('/api/credentials');
      if (response.ok) {
        const data = await response.json();
        setCredentials(data);
      } else {
        setError('Failed to fetch credentials list');
      }
    } catch (err) {
      setError('Connection to server failed');
    }
  };

  const handleAdd = async (e) => {
    e.preventDefault();
    setError('');
    setSuccess('');

    try {
      const response = await apiFetch('/api/credentials', {
        method: 'POST',
        body: JSON.stringify({
          name: credName,
          username: credUsername,
          password: credPassword,
          address: credAddress
        })
      });

      if (response.ok) {
        setSuccess('Credential added successfully!');
        setIsAddModalOpen(false);
        resetForm();
        fetchCredentials();
      } else {
        const text = await response.text();
        setError(text || 'Failed to add credential');
      }
    } catch (err) {
      setError('Server connection error');
    }
  };

  const handleEdit = async (e) => {
    e.preventDefault();
    setError('');
    setSuccess('');

    try {
      const response = await apiFetch(`/api/credentials/${selectedCred.id}`, {
        method: 'PUT',
        body: JSON.stringify({
          name: credName,
          username: credUsername,
          password: credPassword, // Optional: if empty, backend retains original
          address: credAddress
        })
      });

      if (response.ok) {
        setSuccess('Credential updated successfully!');
        setIsEditModalOpen(false);
        resetForm();
        fetchCredentials();
      } else {
        const text = await response.text();
        setError(text || 'Failed to update credential');
      }
    } catch (err) {
      setError('Server connection error');
    }
  };

  const handleDelete = async (id) => {
    if (!window.confirm('Are you sure you want to delete this credential?')) return;
    setError('');
    setSuccess('');

    try {
      const response = await apiFetch(`/api/credentials/${id}`, {
        method: 'DELETE'
      });

      if (response.ok) {
        setSuccess('Credential deleted!');
        fetchCredentials();
      } else {
        setError('Failed to delete credential');
      }
    } catch (err) {
      setError('Server connection error');
    }
  };

  const handleRetrieve = async (cred) => {
    setRetrievingId(cred.id);
    setRetrievalError('');
    setDecryptedCred(null);
    setApprovalModalOpen(true);

    try {
      // Trigger approval-blocked request
      const response = await apiFetch(`/api/credentials/${cred.id}/retrieve`);
      
      if (response.ok) {
        const data = await response.json();
        setDecryptedCred(data);
      } else {
        const msg = await response.text();
        setRetrievalError(msg || 'Failed to retrieve password');
      }
    } catch (err) {
      setRetrievalError('Communication with backend failed.');
    } finally {
      setRetrievingId(null);
    }
  };

  const resetForm = () => {
    setCredName('');
    setCredUsername('');
    setCredPassword('');
    setCredAddress('');
    setSelectedCred(null);
  };

  const openEditModal = (cred) => {
    setSelectedCred(cred);
    setCredName(cred.name);
    setCredUsername(cred.username);
    setCredPassword(''); // Clear for editing security
    setCredAddress(cred.address || '');
    setIsEditModalOpen(true);
  };

  const copyToClipboard = (text, id) => {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(text);
    } else {
      const textArea = document.createElement("textarea");
      textArea.value = text;
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
    setCopiedId(id);
    setTimeout(() => setCopiedId(null), 2000);
  };

  return (
    <div className="min-h-screen bg-darkBg text-textLight">
      {/* Navbar */}
      <nav className="glass-panel border-b border-gray-800 px-6 py-4 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <Shield className="w-6 h-6 text-primaryNeon animate-pulse" />
          <span className="font-bold text-white text-lg tracking-wider uppercase">SecretSafe</span>
          <span className={`inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-2xs font-semibold ${
            serverStatus === 'connected' ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20' :
            serverStatus === 'disconnected' ? 'bg-red-500/10 text-red-400 border border-red-500/20' :
            'bg-gray-500/10 text-gray-400 border border-gray-500/20'
          }`}>
            <span className={`w-1.5 h-1.5 rounded-full ${
              serverStatus === 'connected' ? 'bg-emerald-400 animate-pulse' :
              serverStatus === 'disconnected' ? 'bg-red-400' :
              'bg-gray-400'
            }`}></span>
            {serverStatus === 'connected' ? 'Connected' :
             serverStatus === 'disconnected' ? 'Offline' :
             'Checking...'}
          </span>
        </div>
        
        <div className="flex items-center gap-4">
          <div className="text-right">
            <span className="block text-sm font-semibold text-white">@{user?.username}</span>
            <span className="block text-xs text-gray-500 uppercase tracking-widest">{user?.role}</span>
          </div>

          {user?.role === 'admin' && (
            <button
              onClick={() => onNavigate('admin')}
              className="flex items-center gap-2 px-4 py-2 rounded-lg bg-purple-900/20 hover:bg-purple-900/40 text-purple-300 border border-purple-500/25 transition text-sm font-medium"
            >
              <ShieldAlert className="w-4 h-4" />
              Admin Panel
            </button>
          )}

          <button
            onClick={() => onNavigate('apikeys')}
            className="flex items-center gap-2 px-4 py-2 rounded-lg bg-gray-800 hover:bg-gray-700 text-white border border-gray-700 transition text-sm font-medium"
          >
            <Key className="w-4 h-4 text-primaryNeon" />
            API Keys
          </button>

          {user?.role === 'admin' && (
            <button
              onClick={() => setIsSettingsOpen(true)}
              className="flex items-center gap-2 px-4 py-2 rounded-lg bg-gray-800 hover:bg-gray-700 text-white border border-gray-700 transition text-sm font-medium"
            >
              <Settings className="w-4 h-4 text-primaryNeon" />
              Settings
            </button>
          )}

          <button
            onClick={logout}
            className="flex items-center gap-2 px-4 py-2 rounded-lg bg-red-600/10 hover:bg-red-600/20 text-red-400 border border-red-500/25 transition text-sm font-medium"
          >
            <LogOut className="w-4 h-4" />
            Sign Out
          </button>
        </div>
      </nav>

      {/* Main Container */}
      <div className="max-w-6xl mx-auto px-6 py-10">
        {/* Banner triggers */}
        {error && (
          <div className="flex items-center justify-between p-4 mb-6 bg-red-500/10 border border-red-500/25 text-red-400 rounded-xl text-sm">
            <div className="flex items-center gap-3">
              <AlertCircle className="w-5 h-5 flex-shrink-0" />
              <span>{error}</span>
            </div>
            <button onClick={() => setError('')}><X className="w-4 h-4" /></button>
          </div>
        )}

        {success && (
          <div className="flex items-center justify-between p-4 mb-6 bg-emerald-500/10 border border-emerald-500/25 text-emerald-400 rounded-xl text-sm">
            <div className="flex items-center gap-3">
              <Check className="w-5 h-5 flex-shrink-0" />
              <span>{success}</span>
            </div>
            <button onClick={() => setSuccess('')}><X className="w-4 h-4" /></button>
          </div>
        )}

        {/* Dashboard Header */}
        <div className="flex items-center justify-between mb-8">
          <div>
            <h1 className="text-3xl font-bold text-white tracking-wide">Credentials Vault</h1>
            <p className="text-sm text-gray-400 mt-1">Manage and access your stored secrets securely</p>
          </div>
          <button
            onClick={() => { resetForm(); setIsAddModalOpen(true); }}
            className="flex items-center gap-2 bg-primaryNeon hover:opacity-90 text-darkBg font-bold px-5 py-3 rounded-xl text-sm transition shadow-lg shadow-primaryNeon/10"
          >
            <Plus className="w-5 h-5" />
            New Credential
          </button>
        </div>

        {/* Credentials Grid */}
        {credentials.length === 0 ? (
          <div className="glass-panel p-12 rounded-2xl text-center border border-gray-800 flex flex-col items-center">
            <Key className="w-12 h-12 text-gray-600 mb-4" />
            <p className="text-gray-400 font-medium">No credentials saved yet.</p>
            <p className="text-sm text-gray-600 mt-1">Click the "New Credential" button to get started.</p>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {credentials.map((cred) => (
              <div key={cred.id} className="glass-panel p-6 rounded-2xl border border-gray-800 flex flex-col justify-between hover:border-primaryNeon/30 transition-all duration-300">
                <div>
                  <div className="flex items-start justify-between mb-4">
                    <h3 className="font-bold text-lg text-white truncate max-w-[80%]">{cred.name}</h3>
                    {cred.address ? (
                      <a href={cred.address.startsWith('http') ? cred.address : `https://${cred.address}`} target="_blank" rel="noopener noreferrer" className="p-1.5 rounded-lg bg-gray-800 hover:bg-gray-700 text-gray-400 hover:text-white transition">
                        <Globe className="w-4 h-4" />
                      </a>
                    ) : (
                      <span className="p-1.5 rounded-lg bg-gray-900/50 text-gray-700">
                        <Globe className="w-4 h-4" />
                      </span>
                    )}
                  </div>

                  <div className="space-y-2 mb-6">
                    <div className="flex items-center gap-2 text-sm">
                      <User className="w-4 h-4 text-gray-500" />
                      <span className="text-gray-300 truncate">{cred.username}</span>
                      <button onClick={() => copyToClipboard(cred.username, `user-${cred.id}`)} className="text-gray-500 hover:text-white ml-auto">
                        {copiedId === `user-${cred.id}` ? <Check className="w-3.5 h-3.5 text-primaryNeon" /> : <Copy className="w-3.5 h-3.5" />}
                      </button>
                    </div>

                    <div className="flex items-center gap-2 text-sm bg-darkBg/50 p-2 rounded-lg border border-gray-800">
                      <Key className="w-4 h-4 text-gray-500" />
                      <span className="text-gray-600 font-mono">••••••••••••</span>
                    </div>
                  </div>
                </div>

                <div className="flex items-center justify-between pt-4 border-t border-gray-800/60 mt-auto">
                  <button
                    onClick={() => handleRetrieve(cred)}
                    className="flex items-center gap-1.5 text-xs font-semibold text-primaryNeon bg-primaryNeon/10 hover:bg-primaryNeon/20 border border-primaryNeon/25 px-3 py-2 rounded-lg transition"
                  >
                    <Eye className="w-4 h-4" />
                    Reveal Password
                  </button>

                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => openEditModal(cred)}
                      className="p-2 rounded-lg bg-gray-800 hover:bg-gray-700 text-gray-400 hover:text-white transition"
                    >
                      <Edit className="w-4 h-4" />
                    </button>
                    <button
                      onClick={() => handleDelete(cred.id)}
                      className="p-2 rounded-lg bg-red-950/20 hover:bg-red-950/40 text-red-400 border border-red-500/10 hover:border-red-500/25 transition"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* 1. Approval Pending Modal */}
      {approvalModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-4">
          <div className="w-full max-w-md glass-panel p-8 rounded-2xl border border-primaryNeon/20 text-center relative neon-glow">
            <button
              onClick={() => setApprovalModalOpen(false)}
              className="absolute top-4 right-4 text-gray-500 hover:text-white"
            >
              <X className="w-5 h-5" />
            </button>

            {retrievingId !== null ? (
              <div className="flex flex-col items-center py-6">
                <div className="relative mb-6">
                  <Smartphone className="w-16 h-16 text-primaryNeon animate-pulse" />
                  <Loader className="w-6 h-6 text-primaryNeon animate-spin absolute bottom-0 right-0 bg-darkBg rounded-full p-0.5" />
                </div>
                <h3 className="text-xl font-bold text-white mb-2">Awaiting Approval</h3>
                <p className="text-sm text-gray-400 px-4">
                  We've pushed an authorization prompt to your registered Android device. Please approve it to reveal the password.
                </p>
                <div className="w-full bg-gray-900 border border-gray-800 rounded-lg p-3 mt-6 text-left text-xs space-y-1.5">
                  <div className="text-gray-500 font-semibold uppercase tracking-wider">Retrieval Context</div>
                  <div className="text-gray-300">
                    <span className="font-semibold text-white">Target:</span> {credentials.find(c => c.id === retrievingId)?.name}
                  </div>
                  <div className="text-gray-300">
                    <span className="font-semibold text-white">Requester Host:</span> Client Web Dashboard
                  </div>
                </div>
              </div>
            ) : retrievalError ? (
              <div className="flex flex-col items-center py-6">
                <div className="p-4 bg-red-500/10 border border-red-500/20 rounded-full text-red-500 mb-4">
                  <AlertCircle className="w-12 h-12" />
                </div>
                <h3 className="text-xl font-bold text-white mb-2">Access Denied</h3>
                <p className="text-sm text-red-400 px-4 mb-4">{retrievalError}</p>
                {retrievalError.includes('No active Android app') && (
                  <div className="text-xs bg-gray-900/50 border border-gray-800 p-3 rounded-lg text-gray-400 text-left">
                    <strong>Troubleshoot:</strong> Make sure you have opened the SecretSafe app on your Android device and verified it indicates a "CONNECTED" WebSocket state.
                  </div>
                )}
                <button
                  onClick={() => setApprovalModalOpen(false)}
                  className="mt-6 px-6 py-2 bg-gray-800 hover:bg-gray-700 text-white rounded-xl text-sm transition"
                >
                  Close
                </button>
              </div>
            ) : decryptedCred ? (
              <div className="flex flex-col items-center py-6">
                <div className="p-4 bg-emerald-500/10 border border-emerald-500/20 rounded-full text-emerald-400 mb-4 animate-bounce">
                  <Check className="w-12 h-12" />
                </div>
                <h3 className="text-xl font-bold text-white mb-2">Authorized</h3>
                <p className="text-xs text-gray-400 mb-6">Password decrypted successfully</p>

                <div className="w-full bg-darkBg border border-gray-800 rounded-xl p-4 space-y-4 text-left">
                  <div>
                    <label className="block text-2xs uppercase tracking-wider text-gray-500 mb-1">Username</label>
                    <div className="flex items-center justify-between text-sm bg-gray-900 px-3 py-2 rounded-lg border border-gray-800">
                      <span className="text-white select-all">{decryptedCred.username}</span>
                      <button onClick={() => copyToClipboard(decryptedCred.username, 'dec-user')} className="text-gray-400 hover:text-white">
                        {copiedId === 'dec-user' ? <Check className="w-4 h-4 text-primaryNeon" /> : <Copy className="w-4 h-4" />}
                      </button>
                    </div>
                  </div>

                  <div>
                    <label className="block text-2xs uppercase tracking-wider text-gray-500 mb-1">Password</label>
                    <div className="flex items-center justify-between text-sm bg-gray-900 px-3 py-2 rounded-lg border border-gray-800 font-mono">
                      <span className="text-primaryNeon select-all tracking-wide">{decryptedCred.password}</span>
                      <button onClick={() => copyToClipboard(decryptedCred.password, 'dec-pass')} className="text-gray-400 hover:text-white">
                        {copiedId === 'dec-pass' ? <Check className="w-4 h-4 text-primaryNeon" /> : <Copy className="w-4 h-4" />}
                      </button>
                    </div>
                  </div>
                </div>

                <button
                  onClick={() => setApprovalModalOpen(false)}
                  className="mt-6 px-6 py-2.5 bg-primaryNeon text-darkBg font-bold rounded-xl text-sm transition"
                >
                  Done
                </button>
              </div>
            ) : null}
          </div>
        </div>
      )}

      {/* 2. Add Credential Modal */}
      {isAddModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-4">
          <div className="w-full max-w-md glass-panel p-6 rounded-2xl border border-gray-800 relative">
            <button onClick={() => setIsAddModalOpen(false)} className="absolute top-4 right-4 text-gray-500 hover:text-white">
              <X className="w-5 h-5" />
            </button>
            <h3 className="text-xl font-bold text-white mb-6 flex items-center gap-2">
              <Plus className="w-5 h-5 text-primaryNeon" />
              Add Credential
            </h3>

            <form onSubmit={handleAdd} className="space-y-4">
              <div>
                <label className="block text-xs uppercase text-gray-400 mb-1">Name</label>
                <input type="text" required className="w-full bg-darkBg border border-gray-800 rounded-lg py-2 px-3 text-white text-sm focus:outline-none focus:border-primaryNeon" placeholder="e.g. Google Accounts" value={credName} onChange={e => setCredName(e.target.value)} />
              </div>
              <div>
                <label className="block text-xs uppercase text-gray-400 mb-1">Username / Email</label>
                <input type="text" required className="w-full bg-darkBg border border-gray-800 rounded-lg py-2 px-3 text-white text-sm focus:outline-none focus:border-primaryNeon" placeholder="e.g. user@gmail.com" value={credUsername} onChange={e => setCredUsername(e.target.value)} />
              </div>
              <div>
                <label className="block text-xs uppercase text-gray-400 mb-1">Password</label>
                <input type="password" required className="w-full bg-darkBg border border-gray-800 rounded-lg py-2 px-3 text-white text-sm focus:outline-none focus:border-primaryNeon" placeholder="Password value" value={credPassword} onChange={e => setCredPassword(e.target.value)} />
              </div>
              <div>
                <label className="block text-xs uppercase text-gray-400 mb-1">Website URL (Optional)</label>
                <input type="text" className="w-full bg-darkBg border border-gray-800 rounded-lg py-2 px-3 text-white text-sm focus:outline-none focus:border-primaryNeon" placeholder="e.g. accounts.google.com" value={credAddress} onChange={e => setCredAddress(e.target.value)} />
              </div>
              <button type="submit" className="w-full bg-primaryNeon text-darkBg font-bold py-2.5 rounded-lg text-sm mt-4 hover:opacity-95 transition">
                Save Credential
              </button>
            </form>
          </div>
        </div>
      )}

      {/* 3. Edit Credential Modal */}
      {isEditModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-4">
          <div className="w-full max-w-md glass-panel p-6 rounded-2xl border border-gray-800 relative">
            <button onClick={() => { setIsEditModalOpen(false); resetForm(); }} className="absolute top-4 right-4 text-gray-500 hover:text-white">
              <X className="w-5 h-5" />
            </button>
            <h3 className="text-xl font-bold text-white mb-6 flex items-center gap-2">
              <Edit className="w-5 h-5 text-primaryNeon" />
              Edit Credential
            </h3>

            <form onSubmit={handleEdit} className="space-y-4">
              <div>
                <label className="block text-xs uppercase text-gray-400 mb-1">Name</label>
                <input type="text" required className="w-full bg-darkBg border border-gray-800 rounded-lg py-2 px-3 text-white text-sm focus:outline-none focus:border-primaryNeon" placeholder="Name" value={credName} onChange={e => setCredName(e.target.value)} />
              </div>
              <div>
                <label className="block text-xs uppercase text-gray-400 mb-1">Username / Email</label>
                <input type="text" required className="w-full bg-darkBg border border-gray-800 rounded-lg py-2 px-3 text-white text-sm focus:outline-none focus:border-primaryNeon" placeholder="Username" value={credUsername} onChange={e => setCredUsername(e.target.value)} />
              </div>
              <div>
                <label className="block text-xs uppercase text-gray-400 mb-1">Password (Leave empty to keep current)</label>
                <input type="password" className="w-full bg-darkBg border border-gray-800 rounded-lg py-2 px-3 text-white text-sm focus:outline-none focus:border-primaryNeon" placeholder="New Password value (optional)" value={credPassword} onChange={e => setCredPassword(e.target.value)} />
              </div>
              <div>
                <label className="block text-xs uppercase text-gray-400 mb-1">Website URL (Optional)</label>
                <input type="text" className="w-full bg-darkBg border border-gray-800 rounded-lg py-2 px-3 text-white text-sm focus:outline-none focus:border-primaryNeon" placeholder="Website" value={credAddress} onChange={e => setCredAddress(e.target.value)} />
              </div>
              <button type="submit" className="w-full bg-primaryNeon text-darkBg font-bold py-2.5 rounded-lg text-sm mt-4 hover:opacity-95 transition">
                Save Changes
              </button>
            </form>
          </div>
        </div>
      )}

      {/* 4. Settings Modal */}
      {isSettingsOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-4">
          <div className="w-full max-w-md glass-panel p-6 rounded-2xl border border-gray-800 relative">
            <button
              onClick={() => {
                setIsSettingsOpen(false);
                setTestStatus(null);
                setTestError('');
              }}
              className="absolute top-4 right-4 text-gray-500 hover:text-white"
            >
              <X className="w-5 h-5" />
            </button>
            <h3 className="text-xl font-bold text-white mb-6 flex items-center gap-2">
              <Settings className="w-5 h-5 text-primaryNeon" />
              Settings
            </h3>

            <form
              onSubmit={(e) => {
                e.preventDefault();
                updateServerUrl(tempServerUrl);
                setSuccess('Server API address updated successfully!');
                setIsSettingsOpen(false);
                setTestStatus(null);
                setTestError('');
                setTimeout(() => setSuccess(''), 3000);
              }}
              className="space-y-4"
            >
              <div>
                <label className="block text-xs uppercase text-gray-400 mb-1 font-semibold">Server API Address</label>
                <div className="flex gap-2">
                  <input
                    type="text"
                    required
                    className="flex-1 bg-darkBg border border-gray-800 rounded-lg py-2.5 px-3 text-white text-sm focus:outline-none focus:border-primaryNeon"
                    placeholder={config.backendUrl}
                    value={tempServerUrl}
                    onChange={(e) => {
                      setTempServerUrl(e.target.value);
                      setTestStatus(null);
                      setTestError('');
                    }}
                  />
                  <button
                    type="button"
                    onClick={handleTestConnection}
                    disabled={testStatus === 'testing'}
                    className="px-4 py-2.5 bg-gray-800 hover:bg-gray-700 text-primaryNeon border border-gray-700/60 rounded-lg text-xs font-semibold transition"
                  >
                    {testStatus === 'testing' ? 'Testing...' : 'Test'}
                  </button>
                </div>
                <p className="text-2xs text-gray-500 mt-1.5 leading-relaxed">
                  Enter the API server endpoint protocol and host/port (e.g. <code>https://domain.example</code> or <code>https://192.168.1.37:8051</code>).
                </p>

                {/* Connection Test feedback */}
                {testStatus === 'success' && (
                  <div className="text-2xs text-emerald-400 mt-2 flex items-center gap-1.5">
                    <span className="w-1.5 h-1.5 rounded-full bg-emerald-400"></span>
                    Connection test successful! Server is online and responsive.
                  </div>
                )}
                {testStatus === 'error' && (
                  <div className="text-2xs text-red-400 mt-2 flex flex-col gap-1">
                    <div className="flex items-center gap-1.5 font-semibold">
                      <span className="w-1.5 h-1.5 rounded-full bg-red-400"></span>
                      Connection test failed
                    </div>
                    <span className="text-gray-500 italic block pl-3">{testError}</span>
                  </div>
                )}
              </div>

              <button
                type="submit"
                className="w-full bg-primaryNeon text-darkBg font-bold py-3 rounded-lg text-sm mt-4 hover:opacity-95 transition"
              >
                Save Settings
              </button>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

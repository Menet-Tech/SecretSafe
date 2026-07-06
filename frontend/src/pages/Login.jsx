import React, { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { Shield, Server, User, Lock, AlertCircle } from 'lucide-react';

export default function Login() {
  const { login } = useAuth();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [serverUrl, setServerUrl] = useState('http://localhost:8080');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      await login(username, password, serverUrl);
    } catch (err) {
      setError(err.message || 'Login failed. Please check your credentials and server URL.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-darkBg px-4 relative overflow-hidden">
      {/* Background glow effects */}
      <div className="absolute top-1/4 left-1/4 w-72 h-72 bg-primaryNeon/10 rounded-full blur-[100px] animate-pulse-slow"></div>
      <div className="absolute bottom-1/4 right-1/4 w-72 h-72 bg-purple-600/10 rounded-full blur-[100px] animate-pulse-slow"></div>

      <div className="w-full max-w-md glass-panel p-8 rounded-2xl relative z-10 neon-glow">
        <div className="flex flex-col items-center mb-8">
          <div className="p-4 bg-primaryNeon/15 rounded-full border border-primaryNeon/30 mb-3">
            <Shield className="w-8 h-8 text-primaryNeon" />
          </div>
          <h1 className="text-3xl font-bold text-white tracking-wide">SecretSafe</h1>
          <p className="text-sm text-gray-400 mt-1">Secure Multi-User Password Storage</p>
        </div>

        {error && (
          <div className="flex items-center gap-3 p-4 mb-6 bg-red-500/15 border border-red-500/30 rounded-lg text-red-400 text-sm">
            <AlertCircle className="w-5 h-5 flex-shrink-0" />
            <span>{error}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-5">
          <div>
            <label className="block text-xs font-semibold text-gray-300 uppercase tracking-wider mb-2">
              Server Host Address
            </label>
            <div className="relative">
              <span className="absolute inset-y-0 left-0 pl-3 flex items-center text-gray-400">
                <Server className="w-5 h-5" />
              </span>
              <input
                type="text"
                required
                className="w-full bg-darkBg/50 border border-gray-700/60 rounded-xl py-3 pl-10 pr-4 text-white placeholder-gray-500 focus:outline-none focus:border-primaryNeon transition"
                placeholder="http://localhost:8080"
                value={serverUrl}
                onChange={(e) => setServerUrl(e.target.value)}
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold text-gray-300 uppercase tracking-wider mb-2">
              Username
            </label>
            <div className="relative">
              <span className="absolute inset-y-0 left-0 pl-3 flex items-center text-gray-400">
                <User className="w-5 h-5" />
              </span>
              <input
                type="text"
                required
                className="w-full bg-darkBg/50 border border-gray-700/60 rounded-xl py-3 pl-10 pr-4 text-white placeholder-gray-500 focus:outline-none focus:border-primaryNeon transition"
                placeholder="Enter username"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold text-gray-300 uppercase tracking-wider mb-2">
              Master Password
            </label>
            <div className="relative">
              <span className="absolute inset-y-0 left-0 pl-3 flex items-center text-gray-400">
                <Lock className="w-5 h-5" />
              </span>
              <input
                type="password"
                required
                className="w-full bg-darkBg/50 border border-gray-700/60 rounded-xl py-3 pl-10 pr-4 text-white placeholder-gray-500 focus:outline-none focus:border-primaryNeon transition"
                placeholder="••••••••"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </div>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full bg-gradient-to-r from-primaryNeon to-secondaryNeon hover:opacity-90 text-darkBg font-bold py-3.5 px-4 rounded-xl focus:outline-none transition-all flex items-center justify-center gap-2 mt-4 shadow-lg shadow-primaryNeon/10"
          >
            {loading ? (
              <div className="w-5 h-5 border-2 border-darkBg border-t-transparent rounded-full animate-spin"></div>
            ) : (
              'Authenticate Securely'
            )}
          </button>
        </form>
      </div>
    </div>
  );
}

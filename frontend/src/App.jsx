import React, { useState } from 'react';
import { useAuth } from './context/AuthContext';
import Login from './pages/Login';
import Dashboard from './pages/Dashboard';
import Admin from './pages/Admin';
import APIKeys from './pages/APIKeys';

function AppContent() {
  const { token, user, loading } = useAuth();
  const [view, setView] = useState('dashboard');

  if (loading) {
    return (
      <div className="min-h-screen bg-darkBg flex items-center justify-center text-primaryNeon">
        <div className="flex flex-col items-center gap-3">
          <div className="w-10 h-10 border-4 border-primaryNeon border-t-transparent rounded-full animate-spin"></div>
          <span className="text-sm font-semibold tracking-wider">SecretSafe Loading...</span>
        </div>
      </div>
    );
  }

  if (!token || !user) {
    return <Login />;
  }

  if (view === 'admin' && user.role === 'admin') {
    return <Admin onNavigate={setView} />;
  }

  if (view === 'apikeys') {
    return <APIKeys onNavigate={setView} />;
  }

  return <Dashboard onNavigate={setView} />;
}

export default function App() {
  return (
    <AppContent />
  );
}

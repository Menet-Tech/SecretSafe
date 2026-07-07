import React, { createContext, useState, useEffect, useContext } from 'react';

const AuthContext = createContext(null);

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const getInitialServerUrl = () => {
    const stored = localStorage.getItem('serverUrl');
    if (stored) return stored;
    
    // Fallback: If local development, use https://localhost:8051
    if (window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1') {
      return 'https://localhost:8051';
    }
    
    // If accessed via default Vite dev port (8050), backend is on same host port 8051
    if (window.location.port === '8050') {
      return `https://${window.location.hostname}:8051`;
    }
    
    // In production (e.g. https://domain.example reverse proxy), use current origin
    return `${window.location.protocol}//${window.location.hostname}${window.location.port ? ':' + window.location.port : ''}`;
  };

  const [token, setToken] = useState(localStorage.getItem('token') || '');
  const [serverUrl, setServerUrl] = useState(getInitialServerUrl());
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (token) {
      fetchUserInfo();
    } else {
      setLoading(false);
    }
  }, [token]);

  const fetchUserInfo = async () => {
    try {
      const response = await fetch(`${serverUrl}/api/auth/me`, {
        headers: {
          'Authorization': `Bearer ${token}`,
        },
      });
      if (response.ok) {
        const data = await response.json();
        setUser(data);
      } else {
        // Token might be expired
        logout();
      }
    } catch (error) {
      console.error('Failed to verify user token', error);
      logout();
    } finally {
      setLoading(false);
    }
  };

  const login = async (username, password, customServerUrl) => {
    const targetUrl = customServerUrl.replace(/\/$/, ''); // Remove trailing slash
    const response = await fetch(`${targetUrl}/api/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password }),
    });

    if (!response.ok) {
      const errMsg = await response.text();
      throw new Error(errMsg || 'Login failed');
    }

    const data = await response.json();
    setToken(data.token);
    setServerUrl(targetUrl);
    localStorage.setItem('token', data.token);
    localStorage.setItem('serverUrl', targetUrl);
    
    // User info is fetched via the useEffect dependency or manual query
    const meResponse = await fetch(`${targetUrl}/api/auth/me`, {
      headers: { 'Authorization': `Bearer ${data.token}` },
    });
    if (meResponse.ok) {
      const meData = await meResponse.json();
      setUser(meData);
      return meData;
    }
    throw new Error('Failed to retrieve user profile after login');
  };

  const logout = () => {
    setUser(null);
    setToken('');
    localStorage.removeItem('token');
  };

  const apiFetch = async (endpoint, options = {}) => {
    const headers = {
      'Content-Type': 'application/json',
      ...options.headers,
    };
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    const cleanEndpoint = endpoint.startsWith('/') ? endpoint : `/${endpoint}`;
    return fetch(`${serverUrl}${cleanEndpoint}`, {
      ...options,
      headers,
    });
  };

  const updateServerUrl = (url) => {
    const cleanUrl = url.replace(/\/$/, '');
    setServerUrl(cleanUrl);
    localStorage.setItem('serverUrl', cleanUrl);
  };

  return (
    <AuthContext.Provider value={{ user, token, serverUrl, updateServerUrl, loading, login, logout, apiFetch }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => useContext(AuthContext);

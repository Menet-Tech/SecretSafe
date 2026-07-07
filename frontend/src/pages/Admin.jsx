import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import { ShieldAlert, UserPlus, Users, ArrowLeft, LogOut, CheckCircle, AlertTriangle, Edit, Trash2, X } from 'lucide-react';

export default function Admin({ onNavigate }) {
  const { user, logout, apiFetch } = useAuth();
  const [usersList, setUsersList] = useState([]);
  const [newUsername, setNewUsername] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [newRole, setNewRole] = useState('user');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [loading, setLoading] = useState(false);

  // Edit User State
  const [editingUser, setEditingUser] = useState(null);
  const [editUsername, setEditUsername] = useState('');
  const [editPassword, setEditPassword] = useState('');
  const [editRole, setEditRole] = useState('user');
  const [isEditModalOpen, setIsEditModalOpen] = useState(false);

  useEffect(() => {
    fetchUsers();
  }, []);

  const fetchUsers = async () => {
    try {
      const response = await apiFetch('/api/auth/users');
      if (response.ok) {
        const data = await response.json();
        setUsersList(data);
      } else {
        setError('Failed to load user list');
      }
    } catch (err) {
      setError('Connection to server failed');
    }
  };

  const handleRegister = async (e) => {
    e.preventDefault();
    setError('');
    setSuccess('');
    setLoading(true);

    try {
      const response = await apiFetch('/api/auth/register', {
        method: 'POST',
        body: JSON.stringify({
          username: newUsername,
          password: newPassword,
          role: newRole
        })
      });

      if (response.ok) {
        setSuccess(`User "${newUsername}" registered successfully!`);
        setNewUsername('');
        setNewPassword('');
        setNewRole('user');
        fetchUsers();
      } else {
        const text = await response.text();
        setError(text || 'Failed to register user');
      }
    } catch (err) {
      setError('Connection error occurred');
    } finally {
      setLoading(false);
    }
  };

  const openEditModal = (usr) => {
    setEditingUser(usr);
    setEditUsername(usr.username);
    setEditPassword('');
    setEditRole(usr.role);
    setIsEditModalOpen(true);
  };

  const handleEditUser = async (e) => {
    e.preventDefault();
    setError('');
    setSuccess('');
    setLoading(true);

    try {
      const response = await apiFetch(`/api/auth/users/${editingUser.id}`, {
        method: 'PUT',
        body: JSON.stringify({
          username: editUsername,
          password: editPassword,
          role: editRole
        })
      });

      if (response.ok) {
        setSuccess(`User "${editUsername}" updated successfully!`);
        setIsEditModalOpen(false);
        setEditingUser(null);
        fetchUsers();
      } else {
        const text = await response.text();
        setError(text || 'Failed to update user');
      }
    } catch (err) {
      setError('Connection error occurred');
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteUser = async (usr) => {
    if (usr.id === user?.id) {
      setError('You cannot delete your own account');
      return;
    }
    if (!window.confirm(`Are you sure you want to delete user "${usr.username}"?`)) return;
    setError('');
    setSuccess('');
    setLoading(true);

    try {
      const response = await apiFetch(`/api/auth/users/${usr.id}`, {
        method: 'DELETE'
      });

      if (response.ok) {
        setSuccess(`User "${usr.username}" deleted successfully!`);
        fetchUsers();
      } else {
        const text = await response.text();
        setError(text || 'Failed to delete user');
      }
    } catch (err) {
      setError('Connection error occurred');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-darkBg text-textLight">
      {/* Navigation Top bar */}
      <nav className="glass-panel border-b border-gray-800 px-6 py-4 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <ShieldAlert className="w-6 h-6 text-primaryNeon" />
          <span className="font-bold text-white text-lg tracking-wider uppercase">SecretSafe Admin</span>
        </div>
        <div className="flex items-center gap-4">
          <button
            onClick={() => onNavigate('dashboard')}
            className="flex items-center gap-2 px-4 py-2 rounded-lg bg-gray-800 hover:bg-gray-700 text-white transition text-sm font-medium"
          >
            <ArrowLeft className="w-4 h-4" />
            Credentials Manager
          </button>
          <button
            onClick={logout}
            className="flex items-center gap-2 px-4 py-2 rounded-lg bg-red-600/20 hover:bg-red-600/30 text-red-400 border border-red-500/25 transition text-sm font-medium"
          >
            <LogOut className="w-4 h-4" />
            Sign Out
          </button>
        </div>
      </nav>

      <div className="max-w-6xl mx-auto px-6 py-10 grid grid-cols-1 lg:grid-cols-3 gap-8">
        {/* Left Side: Create User Form */}
        <div className="lg:col-span-1 glass-panel p-6 rounded-xl border border-gray-800 h-fit">
          <div className="flex items-center gap-2 mb-6 border-b border-gray-800 pb-4">
            <UserPlus className="w-5 h-5 text-primaryNeon" />
            <h2 className="text-xl font-bold text-white">Create New Account</h2>
          </div>

          {error && (
            <div className="flex items-start gap-2 p-3.5 mb-5 bg-red-500/10 border border-red-500/20 text-red-400 rounded-lg text-sm">
              <AlertTriangle className="w-5 h-5 flex-shrink-0 mt-0.5" />
              <span>{error}</span>
            </div>
          )}

          {success && (
            <div className="flex items-start gap-2 p-3.5 mb-5 bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 rounded-lg text-sm">
              <CheckCircle className="w-5 h-5 flex-shrink-0 mt-0.5" />
              <span>{success}</span>
            </div>
          )}

          <form onSubmit={handleRegister} className="space-y-4">
            <div>
              <label className="block text-xs font-semibold uppercase text-gray-400 mb-1.5">Username</label>
              <input
                type="text"
                required
                className="w-full bg-darkBg border border-gray-800 rounded-lg py-2.5 px-3 text-white focus:outline-none focus:border-primaryNeon text-sm"
                placeholder="Unique username"
                value={newUsername}
                onChange={(e) => setNewUsername(e.target.value)}
              />
            </div>

            <div>
              <label className="block text-xs font-semibold uppercase text-gray-400 mb-1.5">Password</label>
              <input
                type="password"
                required
                className="w-full bg-darkBg border border-gray-800 rounded-lg py-2.5 px-3 text-white focus:outline-none focus:border-primaryNeon text-sm"
                placeholder="User password"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
              />
            </div>

            <div>
              <label className="block text-xs font-semibold uppercase text-gray-400 mb-1.5">System Role</label>
              <select
                className="w-full bg-darkBg border border-gray-800 rounded-lg py-2.5 px-3 text-white focus:outline-none focus:border-primaryNeon text-sm"
                value={newRole}
                onChange={(e) => setNewRole(e.target.value)}
              >
                <option value="user">Regular User (Access Web/APK)</option>
                <option value="admin">Optionally Admin (Web Management)</option>
              </select>
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full bg-primaryNeon hover:opacity-95 text-darkBg font-bold py-2.5 rounded-lg text-sm transition mt-2"
            >
              {loading ? 'Creating...' : 'Register User'}
            </button>
          </form>
        </div>

        {/* Right Side: Users List */}
        <div className="lg:col-span-2 glass-panel p-6 rounded-xl border border-gray-800">
          <div className="flex items-center gap-2 mb-6 border-b border-gray-800 pb-4">
            <Users className="w-5 h-5 text-primaryNeon" />
            <h2 className="text-xl font-bold text-white">Registered Users</h2>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="text-xs uppercase bg-darkBg/60 text-gray-400 border-b border-gray-800">
                <tr>
                  <th className="py-3 px-4">ID</th>
                  <th className="py-3 px-4">Username</th>
                  <th className="py-3 px-4">Role</th>
                  <th className="py-3 px-4">Registration Date</th>
                  <th className="py-3 px-4 text-center">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-800/50">
                {usersList.map((usr) => (
                  <tr key={usr.id} className="hover:bg-gray-800/30 transition">
                    <td className="py-3.5 px-4 font-mono text-gray-500">{usr.id}</td>
                    <td className="py-3.5 px-4 font-semibold text-white">{usr.username}</td>
                    <td className="py-3.5 px-4">
                      <span
                        className={`inline-block px-2.5 py-0.5 rounded text-xs font-semibold ${
                          usr.role === 'admin'
                            ? 'bg-purple-500/10 text-purple-400 border border-purple-500/20'
                            : 'bg-primaryNeon/10 text-primaryNeon border border-primaryNeon/20'
                        }`}
                      >
                        {usr.role}
                      </span>
                    </td>
                    <td className="py-3.5 px-4 text-xs text-gray-500">
                      {new Date(usr.created_at).toLocaleString()}
                    </td>
                    <td className="py-3.5 px-4">
                      <div className="flex items-center justify-center gap-2">
                        <button
                          onClick={() => openEditModal(usr)}
                          className="p-1.5 rounded-lg bg-gray-800 hover:bg-gray-700 text-gray-400 hover:text-white transition"
                          title="Edit User"
                        >
                          <Edit className="w-4 h-4" />
                        </button>
                        <button
                          onClick={() => handleDeleteUser(usr)}
                          disabled={usr.id === user?.id}
                          className={`p-1.5 rounded-lg transition ${
                            usr.id === user?.id
                              ? 'bg-gray-900 text-gray-700 cursor-not-allowed'
                              : 'bg-red-950/20 hover:bg-red-950/40 text-red-400 border border-red-500/10 hover:border-red-500/25'
                          }`}
                          title={usr.id === user?.id ? "You cannot delete yourself" : "Delete User"}
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
      </div>

      {/* Edit User Modal */}
      {isEditModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-4">
          <div className="w-full max-w-md glass-panel p-6 rounded-2xl border border-gray-800 relative">
            <button
              onClick={() => { setIsEditModalOpen(false); setEditingUser(null); }}
              className="absolute top-4 right-4 text-gray-500 hover:text-white"
            >
              <X className="w-5 h-5" />
            </button>
            <h3 className="text-xl font-bold text-white mb-6 flex items-center gap-2">
              <Edit className="w-5 h-5 text-primaryNeon" />
              Edit Account
            </h3>

            <form onSubmit={handleEditUser} className="space-y-4">
              <div>
                <label className="block text-xs uppercase text-gray-400 mb-1">Username</label>
                <input
                  type="text"
                  required
                  className="w-full bg-darkBg border border-gray-800 rounded-lg py-2 px-3 text-white text-sm focus:outline-none focus:border-primaryNeon"
                  value={editUsername}
                  onChange={e => setEditUsername(e.target.value)}
                />
              </div>
              <div>
                <label className="block text-xs uppercase text-gray-400 mb-1">Password (Leave empty to keep current)</label>
                <input
                  type="password"
                  className="w-full bg-darkBg border border-gray-800 rounded-lg py-2 px-3 text-white text-sm focus:outline-none focus:border-primaryNeon"
                  placeholder="New Password value (optional)"
                  value={editPassword}
                  onChange={e => setEditPassword(e.target.value)}
                />
              </div>
              <div>
                <label className="block text-xs uppercase text-gray-400 mb-1">System Role</label>
                <select
                  className="w-full bg-darkBg border border-gray-800 rounded-lg py-2 px-3 text-white text-sm focus:outline-none focus:border-primaryNeon"
                  value={editRole}
                  onChange={e => setEditRole(e.target.value)}
                  disabled={editingUser?.id === user?.id}
                >
                  <option value="user">Regular User (Access Web/APK)</option>
                  <option value="admin">Optionally Admin (Web Management)</option>
                </select>
                {editingUser?.id === user?.id && (
                  <p className="text-2xs text-gray-500 mt-1">You cannot change your own role to prevent lockout.</p>
                )}
              </div>
              <button
                type="submit"
                disabled={loading}
                className="w-full bg-primaryNeon text-darkBg font-bold py-2.5 rounded-lg text-sm mt-4 hover:opacity-95 transition"
              >
                {loading ? 'Saving...' : 'Save Changes'}
              </button>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

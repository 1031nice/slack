'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { devLogin, ApiError } from '@/lib/api';
import { saveAuthToken } from '@/lib/auth';

export default function DevLoginPage() {
  const router = useRouter();
  const [username, setUsername] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!username.trim()) {
      setError('Please enter a username');
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const response = await devLogin(username.trim());
      saveAuthToken(response.accessToken);
      router.push('/');
    } catch (err) {
      console.error('Dev login failed:', err);
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError('Login failed. Please try again.');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex items-center justify-center h-screen bg-gradient-to-br from-blue-500 to-purple-600">
      <div className="bg-white rounded-lg p-8 w-full max-w-md shadow-2xl">
        <div className="text-center mb-6">
          <h1 className="text-3xl font-bold text-gray-800 mb-2">Dev Login</h1>
          <p className="text-sm text-gray-600">
            Development mode - No authentication required
          </p>
        </div>

        <div className="bg-yellow-50 border border-yellow-200 rounded p-3 mb-6">
          <p className="text-xs text-yellow-800">
            ⚠️ This login method is only available in development mode.
            Just enter any username to get started!
          </p>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="mb-4">
            <label htmlFor="username" className="block text-sm font-medium text-gray-700 mb-2">
              Username
            </label>
            <input
              type="text"
              id="username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="Enter any username (e.g., alice, bob)"
              className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              disabled={loading}
            />
          </div>

          {error && (
            <div className="mb-4 text-sm text-red-500 bg-red-50 px-3 py-2 rounded">
              {error}
            </div>
          )}

          <button
            type="submit"
            disabled={loading}
            className="w-full bg-blue-600 text-white font-semibold py-3 px-4 rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            {loading ? 'Logging in...' : 'Login'}
          </button>
        </form>

        <div className="mt-6 text-center">
          <p className="text-xs text-gray-500">
            Suggested usernames: alice, bob, charlie, dave
          </p>
        </div>

        <div className="mt-6 pt-4 border-t border-gray-200">
          <p className="text-xs text-gray-500 text-center">
            Need OAuth2 login?{' '}
            <a href="/login" className="text-blue-600 hover:text-blue-800">
              Use production login
            </a>
          </p>
        </div>
      </div>
    </div>
  );
}

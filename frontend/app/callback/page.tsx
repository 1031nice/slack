'use client';

import { useEffect, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { exchangeToken, ApiError } from '@/lib/api';
import { setAuthToken } from '@/lib/auth';

export default function CallbackPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const code = searchParams.get('code');
    const errorParam = searchParams.get('error');

    if (errorParam) {
      setError(`Authentication failed: ${errorParam}`);
      setLoading(false);
      return;
    }

    if (!code) {
      setError('Authorization code not found.');
      setLoading(false);
      return;
    }

    const exchangeTokenAsync = async () => {
      try {
        const redirectUri = typeof window !== 'undefined' 
          ? `${window.location.origin}/callback` 
          : 'http://localhost:3000/callback';
        
        const response = await exchangeToken({
          code,
          redirectUri,
        });
        
        setAuthToken(response.accessToken);
        router.push('/');
      } catch (err) {
        if (err instanceof ApiError) {
          setError(err.message);
        } else {
          setError('An unexpected error occurred. Please try again.');
        }
        setLoading(false);
      }
    };

    exchangeTokenAsync();
  }, [searchParams, router]);

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-screen bg-gray-50">
        <div className="w-full max-w-md p-8 bg-white rounded-lg shadow-md">
          <h1 className="text-2xl font-bold text-center mb-6">Slack Clone</h1>
          <p className="text-center text-gray-600">Completing authentication...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="flex items-center justify-center min-h-screen bg-gray-50">
      <div className="w-full max-w-md p-8 bg-white rounded-lg shadow-md">
        <h1 className="text-2xl font-bold text-center mb-6">Slack Clone</h1>
        {error && (
          <div className="mb-4 p-3 bg-red-50 border border-red-200 rounded text-red-600 text-sm">
            {error}
          </div>
        )}
        <button
          onClick={() => router.push('/login')}
          className="w-full px-4 py-2 bg-blue-500 text-white rounded-lg hover:bg-blue-600 transition-colors font-medium"
        >
          Try Again
        </button>
      </div>
    </div>
  );
}


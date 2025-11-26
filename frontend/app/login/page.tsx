'use client';

import { useEffect } from 'react';

const AUTH_SERVER_URL = 'http://localhost:8081';
const CLIENT_ID = 'slack';
const REDIRECT_URI = typeof window !== 'undefined' ? `${window.location.origin}/callback` : 'http://localhost:3000/callback';
const SCOPE = 'read write';

export default function LoginPage() {
  useEffect(() => {
    // Authorization Code Flow: 인증 서버로 리다이렉트
    const authUrl = new URL(`${AUTH_SERVER_URL}/oauth2/authorize`);
    authUrl.searchParams.set('client_id', CLIENT_ID);
    authUrl.searchParams.set('response_type', 'code');
    authUrl.searchParams.set('redirect_uri', REDIRECT_URI);
    authUrl.searchParams.set('scope', SCOPE);
    
    window.location.href = authUrl.toString();
  }, []);

  return (
    <div className="flex items-center justify-center min-h-screen bg-gray-50">
      <div className="w-full max-w-md p-8 bg-white rounded-lg shadow-md">
        <h1 className="text-2xl font-bold text-center mb-6">Slack Clone</h1>
        <p className="text-center text-gray-600">Redirecting to authentication server...</p>
      </div>
    </div>
  );
}


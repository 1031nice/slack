'use client';

import { useEffect, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { acceptInvitation, ApiError } from '@/lib/api';
import { getAuthToken, isValidToken } from '@/lib/auth';

export default function AcceptInvitationPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [token, setToken] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [accepting, setAccepting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);
  const [workspaceId, setWorkspaceId] = useState<number | null>(null);

  useEffect(() => {
    const authToken = getAuthToken();
    if (authToken && isValidToken(authToken)) {
      setToken(authToken);
    } else {
      router.push('/login');
      return;
    }
    setLoading(false);
  }, [router]);

  const handleAccept = async () => {
    const invitationToken = searchParams.get('token');
    if (!invitationToken) {
      setError('Invitation token is missing');
      return;
    }

    if (!token) {
      setError('Authentication required');
      return;
    }

    setAccepting(true);
    setError(null);

    try {
      const workspaceId = await acceptInvitation({ token: invitationToken }, token);
      setWorkspaceId(workspaceId);
      setSuccess(true);
      
      // 2초 후 메인 페이지로 리다이렉트
      setTimeout(() => {
        router.push('/');
      }, 2000);
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError('Failed to accept invitation. Please try again.');
      }
    } finally {
      setAccepting(false);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-screen bg-gray-50">
        <div className="text-center">
          <div className="inline-block animate-spin rounded-full h-12 w-12 border-b-2 border-blue-500 mb-4"></div>
          <div className="text-gray-600">Loading...</div>
        </div>
      </div>
    );
  }

  const invitationToken = searchParams.get('token');

  return (
    <div className="flex items-center justify-center h-screen bg-gray-50">
      <div className="bg-white rounded-lg p-8 w-full max-w-md shadow-lg">
        <h1 className="text-2xl font-bold mb-4">Accept Workspace Invitation</h1>
        
        {success ? (
          <div className="text-center">
            <div className="text-green-500 text-4xl mb-4">✓</div>
            <p className="text-gray-700 mb-2">Successfully joined the workspace!</p>
            <p className="text-sm text-gray-500">Redirecting to workspace...</p>
          </div>
        ) : (
          <>
            {!invitationToken ? (
              <div className="text-red-500">
                <p>Invalid invitation link. Please check the link and try again.</p>
              </div>
            ) : (
              <>
                <p className="text-gray-700 mb-6">
                  You have been invited to join a workspace. Click the button below to accept the invitation.
                </p>

                {error && (
                  <div className="mb-4 text-sm text-red-500 bg-red-50 px-3 py-2 rounded">
                    {error}
                  </div>
                )}

                <button
                  onClick={handleAccept}
                  disabled={accepting}
                  className="w-full bg-blue-600 text-white font-semibold py-2 px-4 rounded hover:bg-blue-700 disabled:opacity-50"
                >
                  {accepting ? 'Accepting...' : 'Accept Invitation'}
                </button>
              </>
            )}
          </>
        )}
      </div>
    </div>
  );
}


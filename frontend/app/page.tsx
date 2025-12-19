'use client';

import { useEffect, useState, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import { useWebSocket } from '@/hooks/useWebSocket';
import { fetchChannels, fetchMessages, fetchWorkspaces, fetchUnreads, createChannel, Channel, Message, Workspace, UnreadMessage, ApiError } from '@/lib/api';
import { WebSocketMessage } from '@/lib/websocket';
import { getAuthToken, isValidToken, removeAuthToken } from '@/lib/auth';
import WorkspaceList from '@/components/WorkspaceList';
import ChannelList from '@/components/ChannelList';
import MessageList from '@/components/MessageList';
import MessageInput from '@/components/MessageInput';
import CreateWorkspaceModal from '@/components/CreateWorkspaceModal';
import CreateChannelModal from '@/components/CreateChannelModal';
import InviteUserModal from '@/components/InviteUserModal';

export default function Home() {
  const router = useRouter();
  const [token, setToken] = useState<string | null>(null);
  const [workspaces, setWorkspaces] = useState<Workspace[]>([]);
  const [selectedWorkspaceId, setSelectedWorkspaceId] = useState<number | null>(null);
  const [channels, setChannels] = useState<Channel[]>([]);
  const [selectedChannelId, setSelectedChannelId] = useState<number | null>(null);
  const [messages, setMessages] = useState<Message[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isCreateWorkspaceModalOpen, setIsCreateWorkspaceModalOpen] = useState(false);
  const [isCreateChannelModalOpen, setIsCreateChannelModalOpen] = useState(false);
  const [isInviteModalOpen, setIsInviteModalOpen] = useState(false);
  const [inviteWorkspaceId, setInviteWorkspaceId] = useState<number | null>(null);
  const [showUnreads, setShowUnreads] = useState(false);
  const [unreadMessages, setUnreadMessages] = useState<UnreadMessage[]>([]);
  const [unreadSort, setUnreadSort] = useState<'newest' | 'oldest' | 'channel'>('newest');

  const { isConnected, error: wsError, subscribeToChannel, sendMessage } = useWebSocket(token);

  // 토큰 초기화
  useEffect(() => {
    const authToken = getAuthToken();
    if (authToken && isValidToken(authToken)) {
      setToken(authToken);
    } else {
      // 토큰이 없으면 로그인 페이지로 리다이렉트
      router.push('/login');
      return;
    }
  }, [router]);

  // WebSocket 에러 처리
  useEffect(() => {
    if (wsError) {
      setError(`WebSocket: ${wsError.message}`);
    }
  }, [wsError]);

  // 워크스페이스 목록 로드
  useEffect(() => {
    if (!token) {
      return;
    }

    setLoading(true);
    setError(null);

    fetchWorkspaces(token)
      .then((workspaces) => {
        setWorkspaces(workspaces);
        if (workspaces.length > 0 && !selectedWorkspaceId) {
          setSelectedWorkspaceId(workspaces[0].id);
        }
        setLoading(false);
      })
      .catch((err) => {
        console.error('Failed to fetch workspaces:', err);
        if (err instanceof ApiError) {
          setError(err.message);
          if (err.statusCode === 401) {
            // 인증 실패 시 토큰 제거하고 로그인 페이지로 리다이렉트
            removeAuthToken();
            setToken(null);
            router.push('/login');
            return;
          } else if (err.statusCode === 404) {
            // 404 에러는 백엔드가 실행 중이 아니거나 엔드포인트가 없는 경우
            console.error('Backend endpoint not found. Please check if the backend is running.');
          }
        } else {
          setError('Failed to load workspaces. Please try again.');
        }
        setLoading(false);
      });
  }, [token]); // selectedWorkspaceId 제거 - 워크스페이스 목록은 토큰이 변경될 때만 로드

  // 선택된 워크스페이스의 채널 목록 로드
  useEffect(() => {
    if (!selectedWorkspaceId || !token) {
      setChannels([]);
      setSelectedChannelId(null);
      setMessages([]); // 워크스페이스가 없으면 메시지도 초기화
      return;
    }

    setError(null);
    setMessages([]); // 워크스페이스가 변경되면 메시지 초기화

    fetchChannels(selectedWorkspaceId, token)
      .then((channels) => {
        setChannels(channels);
        if (channels.length > 0) {
          setSelectedChannelId(channels[0].id);
        } else {
          setSelectedChannelId(null);
        }
      })
      .catch((err) => {
        console.error('Failed to fetch channels:', err);
        if (err instanceof ApiError) {
          setError(err.message);
          if (err.statusCode === 401) {
            // 인증 실패 시 토큰 제거하고 로그인 페이지로 리다이렉트
            removeAuthToken();
            setToken(null);
            router.push('/login');
            return;
          }
        } else {
          setError('Failed to load channels. Please try again.');
        }
        setChannels([]);
        setSelectedChannelId(null);
        setMessages([]); // 에러 발생 시 메시지도 초기화
      });
  }, [token, selectedWorkspaceId]);

  // 선택된 채널의 메시지 로드
  useEffect(() => {
    if (!selectedChannelId || !token || showUnreads || !selectedWorkspaceId) {
      return;
    }

    fetchMessages(selectedChannelId, token)
      .then((messages) => {
        setMessages(messages.reverse()); // 최신 메시지가 아래에 오도록
        setError(null);
        // 메시지를 가져온 후 채널 목록을 갱신하여 unreadCount 업데이트
        return fetchChannels(selectedWorkspaceId, token);
      })
      .then((updatedChannels) => {
        if (updatedChannels) {
          setChannels(updatedChannels);
        }
      })
      .catch((err) => {
        console.error('Failed to fetch messages:', err);
        if (err instanceof ApiError) {
          setError(err.message);
          // 인증 오류인 경우 토큰 제거하고 로그인 페이지로 리다이렉트
          if (err.statusCode === 401) {
            removeAuthToken();
            setToken(null);
            router.push('/login');
            return;
          }
        } else {
          setError('Failed to load messages. Please try again.');
        }
      });
  }, [selectedChannelId, token, showUnreads, selectedWorkspaceId]);

  // Unreads 로드
  useEffect(() => {
    if (!showUnreads || !token) {
      return;
    }

    fetchUnreads(token, unreadSort, 50)
      .then((response) => {
        setUnreadMessages(response.unreadMessages);
        setError(null);
      })
      .catch((err) => {
        console.error('Failed to fetch unreads:', err);
        if (err instanceof ApiError) {
          setError(err.message);
          if (err.statusCode === 401) {
            removeAuthToken();
            setToken(null);
            router.push('/login');
            return;
          }
        } else {
          setError('Failed to load unreads. Please try again.');
        }
      });
  }, [showUnreads, token, unreadSort]);

  // WebSocket으로 메시지 구독 (현재 선택된 채널만)
  useEffect(() => {
    if (!selectedChannelId || !isConnected || showUnreads) {
      return;
    }

    const unsubscribe = subscribeToChannel(selectedChannelId, (wsMessage: WebSocketMessage) => {
      if (wsMessage.type === 'MESSAGE' && wsMessage.messageId) {
        const newMessage: Message = {
          id: wsMessage.messageId,
          channelId: wsMessage.channelId!,
          userId: wsMessage.userId!,
          content: wsMessage.content!,
          parentMessageId: null,
          createdAt: wsMessage.createdAt || new Date().toISOString(),
          updatedAt: wsMessage.createdAt || new Date().toISOString(),
        };
        setMessages((prev) => [...prev, newMessage]);
      }
    });

    return unsubscribe;
  }, [selectedChannelId, isConnected, subscribeToChannel, showUnreads]);

  // 주기적으로 채널 목록을 갱신하여 unreadCount 업데이트
  useEffect(() => {
    if (!selectedWorkspaceId || !token) {
      return;
    }

    // 초기 로드 후 5초마다 채널 목록 갱신
    const interval = setInterval(() => {
      fetchChannels(selectedWorkspaceId, token)
        .then((updatedChannels) => {
          setChannels(updatedChannels);
        })
        .catch((err) => {
          console.error('Failed to refresh channels:', err);
        });
    }, 5000);

    return () => clearInterval(interval);
  }, [selectedWorkspaceId, token]);

  const handleSendMessage = useCallback(
    (content: string) => {
      if (!selectedChannelId || !isConnected || showUnreads) {
        setError('Cannot send message: not connected or no channel selected.');
        return;
      }
      const success = sendMessage(selectedChannelId, content);
      if (!success) {
        setError('Failed to send message. Please check your connection.');
      } else {
        // 메시지 전송 성공 시 에러 초기화
        setError(null);
      }
    },
    [selectedChannelId, isConnected, sendMessage, showUnreads]
  );

  const handleSelectChannel = useCallback((channelId: number) => {
    setShowUnreads(false);
    setSelectedChannelId(channelId);
  }, []);

  const handleSelectUnreads = useCallback(() => {
    setShowUnreads(true);
    setSelectedChannelId(null);
  }, []);

  const handleCreateChannel = useCallback(() => {
    if (!token || !selectedWorkspaceId) {
      setError('Please select a workspace first.');
      return;
    }
    setIsCreateChannelModalOpen(true);
  }, [token, selectedWorkspaceId]);

  const handleChannelCreated = useCallback((channel: Channel) => {
    setChannels((prev) => [...prev, channel]);
    setSelectedChannelId(channel.id);
    setShowUnreads(false);
  }, []);

  const handleCreateWorkspace = useCallback(() => {
    if (!token) {
      console.error('Cannot create workspace: no token');
      setError('Please log in to create a workspace.');
      return;
    }
    setIsCreateWorkspaceModalOpen(true);
  }, [token]);

  const handleWorkspaceCreated = useCallback((workspace: Workspace) => {
    setWorkspaces((prev) => [...prev, workspace]);
    setSelectedWorkspaceId(workspace.id);
  }, []);

  const handleInviteUser = useCallback((workspaceId: number) => {
    if (!token) {
      setError('Please log in to invite users.');
      return;
    }
    setInviteWorkspaceId(workspaceId);
    setIsInviteModalOpen(true);
  }, [token]);

  const handleInviteSuccess = useCallback((inviteResponse: { token: string; email: string }) => {
    // 초대 링크 생성
    const inviteLink = `${window.location.origin}/invitations/accept?token=${inviteResponse.token}`;
    
    // 클립보드에 복사
    navigator.clipboard.writeText(inviteLink).then(() => {
      alert(`Invitation link copied to clipboard!\n\nEmail: ${inviteResponse.email}\nLink: ${inviteLink}`);
    }).catch(() => {
      // 클립보드 복사 실패 시 링크만 표시
      alert(`Invitation created!\n\nEmail: ${inviteResponse.email}\nLink: ${inviteLink}`);
    });
  }, []);

  const handleLogout = useCallback(() => {
    removeAuthToken();
    router.push('/dev-login');
  }, [router]);

  const handleLogout = useCallback(() => {
    removeAuthToken();
    router.push('/dev-login');
  }, [router]);

  // 토큰이 없으면 아무것도 렌더링하지 않음 (리다이렉트 중)
  if (!token) {
    return null;
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center h-screen bg-gray-50">
        <div className="text-center">
          <div className="inline-block animate-spin rounded-full h-12 w-12 border-b-2 border-blue-500 mb-4"></div>
          <div className="text-gray-600">Loading workspaces...</div>
        </div>
      </div>
    );
  }

  if (!selectedWorkspaceId) {
    return (
      <>
        <div className="flex items-center justify-center h-screen bg-gray-50">
          <div className="text-center">
            <p className="text-gray-600 mb-4">No workspace selected</p>
            <button
              onClick={handleCreateWorkspace}
              className="bg-blue-600 hover:bg-blue-700 text-white font-semibold py-2 px-4 rounded"
            >
              Create Workspace
            </button>
          </div>
        </div>
        {token && (
          <CreateWorkspaceModal
            isOpen={isCreateWorkspaceModalOpen}
            onClose={() => setIsCreateWorkspaceModalOpen(false)}
            onSuccess={handleWorkspaceCreated}
            token={token}
          />
        )}
      </>
    );
  }

  return (
    <>
      <div className="flex h-screen">
        <WorkspaceList
          workspaces={workspaces}
          selectedWorkspaceId={selectedWorkspaceId}
          onSelectWorkspace={setSelectedWorkspaceId}
          onCreateWorkspace={handleCreateWorkspace}
          onInviteUser={handleInviteUser}
        />
        <ChannelList
          channels={channels}
          selectedChannelId={selectedChannelId}
          onSelectChannel={handleSelectChannel}
          onSelectUnreads={handleSelectUnreads}
          isUnreadsSelected={showUnreads}
          onCreateChannel={handleCreateChannel}
        />
        <div className="flex-1 flex flex-col">
          <div className="p-4 border-b bg-white">
            <div className="flex items-center justify-between">
              <div className="flex items-center space-x-4">
                {showUnreads ? (
                  <>
                    <h1 className="text-xl font-bold">Unreads</h1>
                    <div className="flex space-x-2">
                      <button
                        onClick={() => setUnreadSort('newest')}
                        className={`px-3 py-1 text-sm rounded ${
                          unreadSort === 'newest'
                            ? 'bg-blue-500 text-white'
                            : 'bg-gray-200 text-gray-700 hover:bg-gray-300'
                        }`}
                      >
                        Newest
                      </button>
                      <button
                        onClick={() => setUnreadSort('oldest')}
                        className={`px-3 py-1 text-sm rounded ${
                          unreadSort === 'oldest'
                            ? 'bg-blue-500 text-white'
                            : 'bg-gray-200 text-gray-700 hover:bg-gray-300'
                        }`}
                      >
                        Oldest
                      </button>
                      <button
                        onClick={() => setUnreadSort('channel')}
                        className={`px-3 py-1 text-sm rounded ${
                          unreadSort === 'channel'
                            ? 'bg-blue-500 text-white'
                            : 'bg-gray-200 text-gray-700 hover:bg-gray-300'
                        }`}
                      >
                        By Channel
                      </button>
                    </div>
                  </>
                ) : (
                  <h1 className="text-xl font-bold">
                    {channels.find((c) => c.id === selectedChannelId)?.name || 'Select a channel'}
                  </h1>
                )}
              </div>
              <div className="flex items-center space-x-2">
                {error && (
                  <div className="text-sm text-red-500 bg-red-50 px-3 py-1 rounded">
                    {error}
                  </div>
                )}
                <div className="text-sm text-gray-500">
                  {isConnected ? (
                    <span className="text-green-500">● Connected</span>
                  ) : (
                    <span className="text-red-500">● Disconnected</span>
                  )}
                </div>
                <button
                  onClick={handleLogout}
                  className="text-sm px-3 py-1 text-gray-600 hover:text-gray-800 hover:bg-gray-100 rounded"
                  title="Logout"
                >
                  Logout
                </button>
              </div>
            </div>
          </div>
          {showUnreads ? (
            <div className="flex-1 overflow-y-auto p-4 space-y-4">
              {unreadMessages.length === 0 ? (
                <div className="text-gray-500 text-center mt-8">
                  <p className="text-lg mb-2">No unread messages</p>
                  <p className="text-sm">You're all caught up!</p>
                </div>
              ) : (
                <>
                  {unreadMessages.map((message) => (
                    <div key={`${message.channelId}-${message.messageId}`} className="mb-4 border-b pb-4 last:border-b-0">
                      <div className="flex items-start space-x-2">
                        <div className="w-8 h-8 bg-blue-500 rounded-full flex items-center justify-center text-white text-sm font-bold">
                          {message.userId}
                        </div>
                        <div className="flex-1">
                          <div className="flex items-center space-x-2 mb-1">
                            <span className="font-semibold text-blue-600">#{message.channelName}</span>
                            <span className="font-semibold">User {message.userId}</span>
                            <span className="text-xs text-gray-500">
                              {new Date(message.createdAt).toLocaleString()}
                            </span>
                          </div>
                          <div className="text-gray-800">{message.content}</div>
                        </div>
                      </div>
                    </div>
                  ))}
                </>
              )}
            </div>
          ) : (
            <>
              <MessageList messages={messages} />
              <MessageInput onSendMessage={handleSendMessage} disabled={!isConnected} />
            </>
          )}
        </div>
      </div>
      {token && (
        <>
          <CreateWorkspaceModal
            isOpen={isCreateWorkspaceModalOpen}
            onClose={() => setIsCreateWorkspaceModalOpen(false)}
            onSuccess={handleWorkspaceCreated}
            token={token}
          />
          {selectedWorkspaceId && (
            <CreateChannelModal
              isOpen={isCreateChannelModalOpen}
              onClose={() => setIsCreateChannelModalOpen(false)}
              onSuccess={handleChannelCreated}
              workspaceId={selectedWorkspaceId}
              token={token}
            />
          )}
          {inviteWorkspaceId && (
            <InviteUserModal
              isOpen={isInviteModalOpen}
              onClose={() => {
                setIsInviteModalOpen(false);
                setInviteWorkspaceId(null);
              }}
              onSuccess={handleInviteSuccess}
              workspaceId={inviteWorkspaceId}
              token={token}
            />
          )}
        </>
      )}
    </>
  );
}

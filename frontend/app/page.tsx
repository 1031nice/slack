'use client';

import { useEffect, useState, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import { useWebSocket } from '@/hooks/useWebSocket';
import { fetchChannels, fetchMessages, Channel, Message, ApiError } from '@/lib/api';
import { WebSocketMessage } from '@/lib/websocket';
import { getAuthToken, isValidToken } from '@/lib/auth';
import ChannelList from '@/components/ChannelList';
import MessageList from '@/components/MessageList';
import MessageInput from '@/components/MessageInput';

export default function Home() {
  const router = useRouter();
  const [token, setToken] = useState<string | null>(null);
  const [channels, setChannels] = useState<Channel[]>([]);
  const [selectedChannelId, setSelectedChannelId] = useState<number | null>(null);
  const [messages, setMessages] = useState<Message[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const { isConnected, error: wsError, subscribeToChannel, sendMessage } = useWebSocket(token);

  // 기본 workspace ID (v0.1에서는 단일 workspace 사용)
  const workspaceId = 1;

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

  useEffect(() => {
    if (!token) {
      return;
    }

    setLoading(true);
    setError(null);

    // 채널 목록 로드
    fetchChannels(workspaceId, token)
      .then((channels) => {
        setChannels(channels);
        if (channels.length > 0) {
          setSelectedChannelId(channels[0].id);
        }
        setLoading(false);
      })
      .catch((err) => {
        console.error('Failed to fetch channels:', err);
        if (err instanceof ApiError) {
          setError(err.message);
          // 인증 오류인 경우 토큰 제거
          if (err.statusCode === 401) {
            setToken(null);
          }
        } else {
          setError('Failed to load channels. Please try again.');
        }
        setLoading(false);
      });
  }, [token, workspaceId]);

  // 선택된 채널의 메시지 로드
  useEffect(() => {
    if (!selectedChannelId || !token) {
      return;
    }

    fetchMessages(selectedChannelId, token)
      .then((messages) => {
        setMessages(messages.reverse()); // 최신 메시지가 아래에 오도록
        setError(null);
      })
      .catch((err) => {
        console.error('Failed to fetch messages:', err);
        if (err instanceof ApiError) {
          setError(err.message);
          // 인증 오류인 경우 토큰 제거
          if (err.statusCode === 401) {
            setToken(null);
          }
        } else {
          setError('Failed to load messages. Please try again.');
        }
      });
  }, [selectedChannelId, token]);

  // WebSocket으로 메시지 구독
  useEffect(() => {
    if (!selectedChannelId || !isConnected) {
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
  }, [selectedChannelId, isConnected, subscribeToChannel]);

  const handleSendMessage = useCallback(
    (content: string) => {
      if (!selectedChannelId || !isConnected) {
        return;
      }
      const success = sendMessage(selectedChannelId, content);
      if (!success) {
        setError('Failed to send message. Please check your connection.');
      }
    },
    [selectedChannelId, isConnected, sendMessage]
  );

  // 토큰이 없으면 아무것도 렌더링하지 않음 (리다이렉트 중)
  if (!token) {
    return null;
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center h-screen bg-gray-50">
        <div className="text-center">
          <div className="inline-block animate-spin rounded-full h-12 w-12 border-b-2 border-blue-500 mb-4"></div>
          <div className="text-gray-600">Loading channels...</div>
        </div>
      </div>
    );
  }


  return (
    <div className="flex h-screen">
      <ChannelList
        channels={channels}
        selectedChannelId={selectedChannelId}
        onSelectChannel={setSelectedChannelId}
      />
      <div className="flex-1 flex flex-col">
        <div className="p-4 border-b bg-white">
          <div className="flex items-center justify-between">
            <h1 className="text-xl font-bold">
              {channels.find((c) => c.id === selectedChannelId)?.name || 'Select a channel'}
            </h1>
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
            </div>
          </div>
        </div>
        <MessageList messages={messages} />
        <MessageInput onSendMessage={handleSendMessage} disabled={!isConnected} />
      </div>
    </div>
  );
}

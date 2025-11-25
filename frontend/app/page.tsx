'use client';

import { useEffect, useState, useCallback } from 'react';
import { useWebSocket } from '@/hooks/useWebSocket';
import { fetchChannels, fetchMessages, Channel, Message } from '@/lib/api';
import { WebSocketMessage } from '@/lib/websocket';
import ChannelList from '@/components/ChannelList';
import MessageList from '@/components/MessageList';
import MessageInput from '@/components/MessageInput';

// TODO: 실제 인증 시스템과 연동 필요
// 임시로 토큰을 로컬 스토리지나 환경 변수에서 가져오도록 설정
const TEMP_TOKEN = process.env.NEXT_PUBLIC_AUTH_TOKEN || '';

export default function Home() {
  const [token] = useState<string | null>(TEMP_TOKEN);
  const [channels, setChannels] = useState<Channel[]>([]);
  const [selectedChannelId, setSelectedChannelId] = useState<number | null>(null);
  const [messages, setMessages] = useState<Message[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const { isConnected, subscribeToChannel, sendMessage } = useWebSocket(token);

  // 기본 workspace ID (v0.1에서는 단일 workspace 사용)
  const workspaceId = 1;

  useEffect(() => {
    if (!token) {
      setError('Authentication token is required');
      setLoading(false);
      return;
    }

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
        setError('Failed to load channels');
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
      })
      .catch((err) => {
        console.error('Failed to fetch messages:', err);
        setError('Failed to load messages');
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
      if (!selectedChannelId) {
        return;
      }
      sendMessage(selectedChannelId, content);
    },
    [selectedChannelId, sendMessage]
  );

  if (loading) {
    return (
      <div className="flex items-center justify-center h-screen">
        <div>Loading...</div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex items-center justify-center h-screen">
        <div className="text-red-500">Error: {error}</div>
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
          <h1 className="text-xl font-bold">
            {channels.find((c) => c.id === selectedChannelId)?.name || 'Select a channel'}
          </h1>
          <div className="text-sm text-gray-500 mt-1">
            {isConnected ? (
              <span className="text-green-500">● Connected</span>
            ) : (
              <span className="text-red-500">● Disconnected</span>
            )}
          </div>
        </div>
        <MessageList messages={messages} />
        <MessageInput onSendMessage={handleSendMessage} disabled={!isConnected} />
      </div>
    </div>
  );
}

'use client';

import { useEffect, useRef, useState, useCallback } from 'react';
import { WebSocketClient, WebSocketMessage } from '@/lib/websocket';

export function useWebSocket(token: string | null) {
  const [isConnected, setIsConnected] = useState(false);
  const [error, setError] = useState<any>(null);
  const clientRef = useRef<WebSocketClient | null>(null);

  useEffect(() => {
    if (!token) {
      return;
    }

    const client = new WebSocketClient();
    clientRef.current = client;

    client.connect(
      token,
      () => {
        setIsConnected(true);
        setError(null);
      },
      (err) => {
        setIsConnected(false);
        setError(err);
      }
    );

    return () => {
      client.disconnect();
      clientRef.current = null;
    };
  }, [token]);

  const subscribeToChannel = useCallback(
    (channelId: number, callback: (message: WebSocketMessage) => void) => {
      if (!clientRef.current) {
        return () => {};
      }
      return clientRef.current.subscribeToChannel(channelId, callback);
    },
    []
  );

  const sendMessage = useCallback((channelId: number, content: string) => {
    if (!clientRef.current) {
      console.error('WebSocket client not initialized');
      return;
    }
    clientRef.current.sendMessage(channelId, content);
  }, []);

  return {
    isConnected,
    error,
    subscribeToChannel,
    sendMessage,
  };
}


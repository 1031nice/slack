import { useState, useEffect, useRef, useCallback } from 'react';
import { Message } from '@/lib/api';

interface BufferedMessage extends Message {
  bufferedAt: number; // Timestamp when message was added to buffer
}

/**
 * Phase 2: Client-Side Ordering with Time Buffer (v0.5)
 *
 * This hook implements:
 * - 2-second buffer for late-arriving messages
 * - Deduplication using timestampId
 * - Sorting by createdAt timestamp
 *
 * Trade-off: 2-second display latency for correct ordering
 */
export function useMessageBuffer(initialMessages: Message[] = []) {
  // Display messages (sorted and flushed from buffer)
  const [displayMessages, setDisplayMessages] = useState<Message[]>(initialMessages);

  // Buffer for incoming messages (waiting to be flushed)
  const [buffer, setBuffer] = useState<BufferedMessage[]>([]);

  // Set to track seen timestampIds for deduplication
  const seenTimestampIds = useRef<Set<string>>(new Set());

  // Timer ref for buffer flush
  const flushTimerRef = useRef<NodeJS.Timeout | null>(null);

  // Initialize seenTimestampIds from initial messages
  useEffect(() => {
    initialMessages.forEach(msg => {
      if (msg.timestampId) {
        seenTimestampIds.current.add(msg.timestampId);
      }
    });
    setDisplayMessages(sortMessagesByTimestamp(initialMessages));
  }, []);

  /**
   * Sort messages by createdAt timestamp (ascending order - oldest first)
   */
  const sortMessagesByTimestamp = useCallback((messages: Message[]): Message[] => {
    return [...messages].sort((a, b) => {
      const timeA = new Date(a.createdAt).getTime();
      const timeB = new Date(b.createdAt).getTime();
      return timeA - timeB;
    });
  }, []);

  /**
   * Flush buffer: move messages from buffer to display
   */
  const flushBuffer = useCallback(() => {
    const now = Date.now();
    const BUFFER_DURATION_MS = 2000; // 2 seconds

    setBuffer(currentBuffer => {
      // Find messages that have been in buffer for >= 2 seconds
      const messagesToFlush = currentBuffer.filter(
        msg => now - msg.bufferedAt >= BUFFER_DURATION_MS
      );

      // Keep messages that are still within buffer time
      const remainingBuffer = currentBuffer.filter(
        msg => now - msg.bufferedAt < BUFFER_DURATION_MS
      );

      if (messagesToFlush.length > 0) {
        // Remove bufferedAt property and add to display
        const newMessages = messagesToFlush.map(({ bufferedAt, ...msg }) => msg);

        setDisplayMessages(prevDisplay => {
          const combined = [...prevDisplay, ...newMessages];
          return sortMessagesByTimestamp(combined);
        });
      }

      return remainingBuffer;
    });
  }, [sortMessagesByTimestamp]);

  /**
   * Start or reset flush timer
   */
  useEffect(() => {
    if (buffer.length === 0) {
      if (flushTimerRef.current) {
        clearInterval(flushTimerRef.current);
        flushTimerRef.current = null;
      }
      return;
    }

    // Set up interval to check buffer every 100ms
    if (!flushTimerRef.current) {
      flushTimerRef.current = setInterval(() => {
        flushBuffer();
      }, 100);
    }

    return () => {
      if (flushTimerRef.current) {
        clearInterval(flushTimerRef.current);
        flushTimerRef.current = null;
      }
    };
  }, [buffer.length, flushBuffer]);

  /**
   * Add a new message to the buffer
   * Handles deduplication and buffering
   */
  const addMessage = useCallback((message: Message) => {
    // Deduplication: check if we've seen this timestampId
    if (message.timestampId && seenTimestampIds.current.has(message.timestampId)) {
      console.log(`[MessageBuffer] Duplicate message detected: ${message.timestampId}`);
      return;
    }

    // Add to seen set
    if (message.timestampId) {
      seenTimestampIds.current.add(message.timestampId);
    }

    // Add to buffer with current timestamp
    const bufferedMessage: BufferedMessage = {
      ...message,
      bufferedAt: Date.now(),
    };

    setBuffer(prev => [...prev, bufferedMessage]);
    console.log(`[MessageBuffer] Message added to buffer: ${message.timestampId || message.id}`);
  }, []);

  /**
   * Replace all messages (used when loading initial messages from API)
   */
  const setMessages = useCallback((messages: Message[]) => {
    // Clear buffer and seen set
    setBuffer([]);
    seenTimestampIds.current.clear();

    // Add all messages to seen set
    messages.forEach(msg => {
      if (msg.timestampId) {
        seenTimestampIds.current.add(msg.timestampId);
      }
    });

    // Set display messages (sorted)
    setDisplayMessages(sortMessagesByTimestamp(messages));
  }, [sortMessagesByTimestamp]);

  return {
    messages: displayMessages,
    addMessage,
    setMessages,
  };
}

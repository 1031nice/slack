'use client';

import { useEffect, useState } from 'react';
import { fetchUnreads, UnreadMessage, ApiError } from '@/lib/api';
import { getAuthToken } from '@/lib/auth';

type SortOption = 'newest' | 'oldest' | 'channel';

export default function UnreadsView() {
  const [unreadMessages, setUnreadMessages] = useState<UnreadMessage[]>([]);
  const [totalCount, setTotalCount] = useState(0);
  const [sort, setSort] = useState<SortOption>('newest');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const token = getAuthToken();
    if (!token) {
      setError('Please log in to view unreads.');
      setLoading(false);
      return;
    }

    setLoading(true);
    setError(null);

    fetchUnreads(token, sort, 50)
      .then((response) => {
        setUnreadMessages(response.unreadMessages);
        setTotalCount(response.totalCount);
        setLoading(false);
      })
      .catch((err) => {
        console.error('Failed to fetch unreads:', err);
        if (err instanceof ApiError) {
          setError(err.message);
        } else {
          setError('Failed to load unreads. Please try again.');
        }
        setLoading(false);
      });
  }, [sort]);

  const handleSortChange = (newSort: SortOption) => {
    setSort(newSort);
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-screen bg-gray-50">
        <div className="text-center">
          <div className="inline-block animate-spin rounded-full h-12 w-12 border-b-2 border-blue-500 mb-4"></div>
          <div className="text-gray-600">Loading unreads...</div>
        </div>
      </div>
    );
  }

  return (
    <div className="flex-1 flex flex-col h-screen bg-white">
      <div className="p-4 border-b bg-white">
        <div className="flex items-center justify-between mb-4">
          <h1 className="text-xl font-bold">Unreads</h1>
          <div className="text-sm text-gray-500">
            {totalCount} unread message{totalCount !== 1 ? 's' : ''}
          </div>
        </div>
        <div className="flex space-x-2">
          <button
            onClick={() => handleSortChange('newest')}
            className={`px-4 py-2 rounded ${
              sort === 'newest'
                ? 'bg-blue-500 text-white'
                : 'bg-gray-200 text-gray-700 hover:bg-gray-300'
            }`}
          >
            Newest First
          </button>
          <button
            onClick={() => handleSortChange('oldest')}
            className={`px-4 py-2 rounded ${
              sort === 'oldest'
                ? 'bg-blue-500 text-white'
                : 'bg-gray-200 text-gray-700 hover:bg-gray-300'
            }`}
          >
            Oldest First
          </button>
          <button
            onClick={() => handleSortChange('channel')}
            className={`px-4 py-2 rounded ${
              sort === 'channel'
                ? 'bg-blue-500 text-white'
                : 'bg-gray-200 text-gray-700 hover:bg-gray-300'
            }`}
          >
            By Channel
          </button>
        </div>
      </div>

      {error && (
        <div className="p-4 bg-red-50 border-b border-red-200">
          <div className="text-sm text-red-600">{error}</div>
        </div>
      )}

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
    </div>
  );
}


'use client';

import { useState } from 'react';
import { createChannel, Channel, ApiError } from '@/lib/api';

interface CreateChannelModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: (channel: Channel) => void;
  workspaceId: number;
  token: string;
}

export default function CreateChannelModal({
  isOpen,
  onClose,
  onSuccess,
  workspaceId,
  token,
}: CreateChannelModalProps) {
  const [name, setName] = useState('');
  const [type, setType] = useState<'PUBLIC' | 'PRIVATE'>('PUBLIC');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) {
      setError('Channel name is required');
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const channel = await createChannel(workspaceId, { name: name.trim(), type }, token);
      onSuccess(channel);
      setName('');
      setType('PUBLIC');
      onClose();
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError('Failed to create channel. Please try again.');
      }
    } finally {
      setLoading(false);
    }
  };

  const handleClose = () => {
    if (!loading) {
      setName('');
      setType('PUBLIC');
      setError(null);
      onClose();
    }
  };

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg p-6 w-full max-w-md">
        <h2 className="text-xl font-bold mb-4">Create New Channel</h2>
        
        <form onSubmit={handleSubmit}>
          <div className="mb-4">
            <label htmlFor="channel-name" className="block text-sm font-medium text-gray-700 mb-2">
              Channel Name
            </label>
            <input
              id="channel-name"
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Enter channel name"
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
              disabled={loading}
              autoFocus
            />
          </div>

          <div className="mb-4">
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Channel Type
            </label>
            <div className="space-y-2">
              <label className="flex items-center">
                <input
                  type="radio"
                  value="PUBLIC"
                  checked={type === 'PUBLIC'}
                  onChange={(e) => setType(e.target.value as 'PUBLIC' | 'PRIVATE')}
                  disabled={loading}
                  className="mr-2"
                />
                <span>Public - Anyone in the workspace can join</span>
              </label>
              <label className="flex items-center">
                <input
                  type="radio"
                  value="PRIVATE"
                  checked={type === 'PRIVATE'}
                  onChange={(e) => setType(e.target.value as 'PUBLIC' | 'PRIVATE')}
                  disabled={loading}
                  className="mr-2"
                />
                <span>Private - Only invited members can join</span>
              </label>
            </div>
          </div>

          {error && (
            <div className="mb-4 text-sm text-red-500 bg-red-50 px-3 py-2 rounded">
              {error}
            </div>
          )}

          <div className="flex justify-end space-x-2">
            <button
              type="button"
              onClick={handleClose}
              disabled={loading}
              className="px-4 py-2 text-gray-700 bg-gray-200 rounded hover:bg-gray-300 disabled:opacity-50"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={loading || !name.trim()}
              className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-50"
            >
              {loading ? 'Creating...' : 'Create'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}


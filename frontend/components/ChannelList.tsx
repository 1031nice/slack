'use client';

import { Channel } from '@/lib/api';

interface ChannelListProps {
  channels: Channel[];
  selectedChannelId: number | null;
  onSelectChannel: (channelId: number) => void;
  onSelectUnreads?: () => void;
  isUnreadsSelected?: boolean;
  onCreateChannel?: () => void;
}

export default function ChannelList({
  channels,
  selectedChannelId,
  onSelectChannel,
  onSelectUnreads,
  isUnreadsSelected = false,
  onCreateChannel,
}: ChannelListProps) {
  return (
    <div className="w-64 bg-gray-100 p-4 flex flex-col">
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-lg font-bold">Channels</h2>
        {onCreateChannel && (
          <button
            onClick={onCreateChannel}
            className="text-xl font-bold text-gray-600 hover:text-gray-800"
            title="Create channel"
          >
            +
          </button>
        )}
      </div>
      <ul className="flex-1 space-y-2 overflow-y-auto">
        {onSelectUnreads && (
          <li
            onClick={onSelectUnreads}
            className={`p-2 rounded cursor-pointer ${
              isUnreadsSelected
                ? 'bg-blue-500 text-white'
                : 'bg-white hover:bg-gray-200'
            }`}
          >
            📬 Unreads
          </li>
        )}
        {channels.map((channel) => (
          <li
            key={channel.id}
            onClick={() => onSelectChannel(channel.id)}
            className={`p-2 rounded cursor-pointer ${
              selectedChannelId === channel.id && !isUnreadsSelected
                ? 'bg-blue-500 text-white'
                : 'bg-white hover:bg-gray-200'
            }`}
          >
            #{channel.name}
            {channel.unreadCount !== undefined && channel.unreadCount > 0 && (
              <span className="ml-2 text-xs bg-red-500 text-white rounded-full px-2 py-0.5">
                {channel.unreadCount}
              </span>
            )}
          </li>
        ))}
      </ul>
    </div>
  );
}


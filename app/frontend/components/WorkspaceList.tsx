'use client';

import { Workspace } from '@/lib/api';

interface WorkspaceListProps {
  workspaces: Workspace[];
  selectedWorkspaceId: number | null;
  onSelectWorkspace: (workspaceId: number) => void;
  onCreateWorkspace: () => void;
  onInviteUser: (workspaceId: number) => void;
}

export default function WorkspaceList({
  workspaces,
  selectedWorkspaceId,
  onSelectWorkspace,
  onCreateWorkspace,
  onInviteUser,
}: WorkspaceListProps) {
  const handleWorkspaceClick = (workspaceId: number, e: React.MouseEvent) => {
    // 초대 버튼 클릭이 아닌 경우에만 워크스페이스 선택
    if ((e.target as HTMLElement).closest('.invite-button')) {
      e.stopPropagation();
      return;
    }
    onSelectWorkspace(workspaceId);
  };

  const handleInviteClick = (workspaceId: number, e: React.MouseEvent) => {
    e.stopPropagation();
    onInviteUser(workspaceId);
  };

  return (
    <div className="w-64 bg-gray-800 text-white p-4 flex flex-col">
      <div className="mb-4">
        <h2 className="text-lg font-bold mb-2">Workspaces</h2>
        <button
          onClick={onCreateWorkspace}
          className="w-full bg-blue-600 hover:bg-blue-700 text-white font-semibold py-2 px-4 rounded text-sm"
        >
          + New Workspace
        </button>
      </div>
      <ul className="flex-1 space-y-1 overflow-y-auto">
        {workspaces.map((workspace) => (
          <li
            key={workspace.id}
            onClick={(e) => handleWorkspaceClick(workspace.id, e)}
            className={`p-2 rounded cursor-pointer ${
              selectedWorkspaceId === workspace.id
                ? 'bg-blue-600 text-white'
                : 'hover:bg-gray-700'
            }`}
          >
            <div className="flex items-center justify-between">
              <div className="flex-1">
                <div className="font-semibold">{workspace.name}</div>
                <div className="text-xs text-gray-400 mt-1">
                  {new Date(workspace.createdAt).toLocaleDateString()}
                </div>
              </div>
              <button
                onClick={(e) => handleInviteClick(workspace.id, e)}
                className="invite-button ml-2 px-2 py-1 text-xs bg-gray-700 hover:bg-gray-600 rounded"
                title="Invite user to workspace"
              >
                +
              </button>
            </div>
          </li>
        ))}
      </ul>
    </div>
  );
}


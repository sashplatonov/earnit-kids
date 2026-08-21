export type ParentTab = 'home' | 'tasks' | 'rewards' | 'family';
export type ChildTab = 'tasks' | 'rewards' | 'activity';
export type ActivityTab = 'history' | 'requests';

export type TelegramWorkspaceContext = { parentTab: ParentTab; childTab: ChildTab; activityTab: ActivityTab };

export function parseTelegramWorkspaceContext(value: string): TelegramWorkspaceContext {
    return {
        parentTab: value === 'tasks' || value === 'rewards' || value === 'family' ? value : 'home',
        childTab: value === 'rewards' ? 'rewards' : value === 'activity' || value === 'history' || value === 'requests' ? 'activity' : 'tasks',
        activityTab: value === 'requests' ? 'requests' : 'history',
    };
}

export interface AppConfig {
    backendOrigin: string;
    publicOrigin: string;
    telegramMiniAppUrl: string | null;
    sessionPath: string;
    wsPath: string;
    devPort: number;
    previewPort: number;
}


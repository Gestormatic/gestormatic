import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

export interface AppConfig {
  SUPABASE_URL: string;
  SUPABASE_SERVICE_ROLE_KEY: string;
  DEV_HOST: string;
}

@Injectable({
  providedIn: 'root',
})
export class ConfigService {
  private _config!: AppConfig;
  private http = inject(HttpClient);

  get config(): AppConfig {
    return this._config;
  }

  loadConfig(): Promise<void> {
    return firstValueFrom(this.http.get<AppConfig>('/config.json'))
      .then((config) => {
        this._config = config;
      })
      .catch((err) => {
        console.error('Error loading config.json, using defaults.', err);
        this._config = {
          SUPABASE_URL: 'http://localhost:8000',
          SUPABASE_SERVICE_ROLE_KEY: 'service-role-key',
          DEV_HOST: 'http://localhost:3000',
        };
      });
  }
}

export function initializeApp(configService: ConfigService) {
  return () => configService.loadConfig();
}

import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { ConfigService } from './config.service';

@Injectable({
  providedIn: 'root',
})
export class Auth {

  private http = inject(HttpClient);
  private configService = inject(ConfigService);

  signIn(email: string, password: string): Promise<void> {
    return new Promise((resolve, reject) => {
      const environment = this.configService.config;

      const headers = {
        'Content-Type': 'application/json',
        'apikey': environment.SUPABASE_SERVICE_ROLE_KEY,
      };

      this.http.post(`${environment.SUPABASE_URL}/auth/v1/token?grant_type=password`, { email, password }, { headers }).subscribe({
        next: (response) => {
          console.log('Respuesta del servidor:', response);
          resolve();
        },
        error: (error) => {
          reject(error);
        }
      });
    });
  }

  signUp(email: string, password: string, displayName: string): Promise<void> {
    return new Promise((resolve, reject) => {
      const environment = this.configService.config;

      this.http.post(`${environment.DEV_HOST}/api/auth/signup`, {
        email,
        password,
        display_name: displayName,
        tenant_id: 'default'
      }).subscribe({
        next: (response) => {
          console.log('Respuesta del servidor (signup):', response);
          resolve();
        },
        error: (error) => {
          reject(error);
        }
      });
    });
  }
}

import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';

@Injectable({
  providedIn: 'root',
})
export class Auth {

  private http = inject(HttpClient);

  signIn(email: string, password: string): Promise<void> {
    return new Promise((resolve, reject) => {
      console.log('import.meta.env:', import.meta.env);

      const environment = {
        SUPABASE_URL: import.meta.env['VITE_SUPABASE_URL'] || 'http://localhost:8000',
        SUPABASE_SERVICE_ROLE_KEY: import.meta.env['VITE_SUPABASE_SERVICE_ROLE_KEY'] || 'service-role-key',
      };

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
}

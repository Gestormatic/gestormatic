import { Route } from '@angular/router';

export const appRoutes: Route[] = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'auth/signin',
  },
  {
    path: 'auth',
    children: [
      {
        path: 'signin',
        loadComponent: () =>
          import('./core/components/auth/signin/signin').then((m) => m.Signin),
      },
      {
        path: 'signup',
        loadComponent: () =>
          import('./core/components/auth/signup/signup').then((m) => m.Signup),
      }
    ]
  },
  {
    path: 'main',
    children: [
      {
        path: 'home',
        loadComponent: () =>
          import('./core/components/layout/layout').then((m) => m.Layout),
      }
    ]
  }
];

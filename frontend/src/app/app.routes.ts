import { Routes } from '@angular/router';
import { guest, signedIn, signedOut } from './auth/auth';

const authPage = () => import('./pages/auth/auth-page').then((m) => m.AuthPage);

export const routes: Routes = [
  { path: 'signin', loadComponent: authPage, canActivate: [signedOut], data: { mode: 'signin' }, title: 'Sign in' },
  { path: 'signup', loadComponent: authPage, canActivate: [signedOut], data: { mode: 'signup' }, title: 'Create account' },
  {
    path: '',
    pathMatch: 'full',
    canMatch: [guest],
    loadComponent: () => import('./pages/home/home').then((m) => m.Home),
    title: 'Lampwatch: website monitoring with a public status page',
  },
  { path: 's/:slug', loadComponent: () => import('./pages/public-status/public-status').then((m) => m.PublicStatus), title: 'Status' },
  {
    path: '',
    canActivate: [signedIn],
    children: [
      { path: '', loadComponent: () => import('./pages/monitor-list/monitor-list').then((m) => m.MonitorList), title: 'Monitors' },
      { path: 'monitors/new', loadComponent: () => import('./pages/monitor-form/monitor-form').then((m) => m.MonitorForm), title: 'Add monitor' },
      { path: 'monitors/:id/edit', loadComponent: () => import('./pages/monitor-form/monitor-form').then((m) => m.MonitorForm), title: 'Edit monitor' },
      { path: 'status-page', loadComponent: () => import('./pages/status-settings/status-settings').then((m) => m.StatusSettings), title: 'Status page' },
      { path: 'alerts', loadComponent: () => import('./pages/alerts/alerts-page').then((m) => m.AlertsPage), title: 'Alerts' },
      { path: 'monitors/:id', loadComponent: () => import('./pages/monitor-detail/monitor-detail').then((m) => m.MonitorDetail), title: 'Monitor' },
    ],
  },
  { path: '**', redirectTo: '' },
];

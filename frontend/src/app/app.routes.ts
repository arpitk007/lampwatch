import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', loadComponent: () => import('./pages/monitor-list/monitor-list').then((m) => m.MonitorList), title: 'Monitors' },
  { path: 'monitors/new', loadComponent: () => import('./pages/monitor-form/monitor-form').then((m) => m.MonitorForm), title: 'Add monitor' },
  { path: 'monitors/:id/edit', loadComponent: () => import('./pages/monitor-form/monitor-form').then((m) => m.MonitorForm), title: 'Edit monitor' },
  { path: 'monitors/:id', loadComponent: () => import('./pages/monitor-detail/monitor-detail').then((m) => m.MonitorDetail), title: 'Monitor' },
  { path: '**', redirectTo: '' },
];

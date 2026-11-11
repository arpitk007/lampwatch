import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export type ChannelType = 'EMAIL' | 'TELEGRAM';

export interface Channel {
  id: number;
  type: ChannelType;
  target: string;
  lastDelivery: {
    subject: string;
    status: 'PENDING' | 'SENT' | 'FAILED';
    at: string;
    error: string | null;
  } | null;
}

@Injectable({ providedIn: 'root' })
export class AlertsApi {
  private readonly http = inject(HttpClient);

  list(): Observable<Channel[]> {
    return this.http.get<Channel[]>('/api/channels');
  }

  add(type: ChannelType, target: string): Observable<Channel> {
    return this.http.post<Channel>('/api/channels', { type, target });
  }

  remove(id: number): Observable<void> {
    return this.http.delete<void>(`/api/channels/${id}`);
  }

  sendTest(id: number): Observable<void> {
    return this.http.post<void>(`/api/channels/${id}/test`, null);
  }
}

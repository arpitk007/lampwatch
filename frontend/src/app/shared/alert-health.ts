import { computed, inject, Injectable, signal } from '@angular/core';
import { AlertsApi, Channel } from '../api/alerts-api';

/**
 * Whether alerts are actually reaching anyone. A silent channel is the worst failure a monitor can have,
 * so the shell and the monitor list show it, not only the Alerts page.
 */
@Injectable({ providedIn: 'root' })
export class AlertHealth {
  private readonly api = inject(AlertsApi);
  private readonly channels = signal<Channel[] | null>(null);

  /** Channels whose latest delivery failed, or is being retried after an error. */
  readonly broken = computed(() => (this.channels() ?? []).filter(isFailing));

  /** "Alerts to Telegram chat 123 aren’t being delivered." */
  readonly summary = computed(() => {
    const broken = this.broken();
    if (broken.length === 0) return null;
    const who = broken.length === 1 ? channelName(broken[0]) : `${broken.length} channels`;
    return `Alerts to ${who} aren’t being delivered.`;
  });

  refresh(): void {
    this.api.list().subscribe({ next: (list) => this.channels.set(list), error: () => undefined });
  }

  /** For pages that already loaded the channels. */
  set(list: Channel[]): void {
    this.channels.set(list);
  }
}

function isFailing(c: Channel): boolean {
  const d = c.lastDelivery;
  return !!d && (d.status === 'FAILED' || (d.status === 'PENDING' && !!d.error));
}

export function channelName(c: Channel): string {
  return c.type === 'TELEGRAM' ? `Telegram chat ${c.target}` : c.target;
}

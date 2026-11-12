import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { CheckResult, Monitor, MonitorApi, problemOf } from '../../api/monitor-api';
import { secondsLabel, StatusRulesStore } from '../../api/status-rules';
import { HeartbeatStrip } from '../../shared/heartbeat-strip';
import { SeaChart } from '../../shared/sea-chart';
import { poll } from '../../shared/polling';
import { StatusBadge } from '../../shared/status-badge';
import { clockTime, durationLabel, intervalLabel, timeAgo } from '../../shared/time';

@Component({
  selector: 'app-monitor-list',
  imports: [RouterLink, HeartbeatStrip, StatusBadge, SeaChart],
  templateUrl: './monitor-list.html',
  styleUrl: './monitor-list.scss',
})
export class MonitorList {
  private readonly api = inject(MonitorApi);
  private readonly rulesStore = inject(StatusRulesStore);
  protected readonly slowAfterMs = computed(() => this.rulesStore.get()?.slowAfterMs ?? null);

  protected readonly monitors = signal<Monitor[] | null>(null);
  protected readonly heartbeats = signal<Record<string, CheckResult[]>>({});
  protected readonly error = signal<string | null>(null);
  protected readonly updatedAt = signal<Date | null>(null);

  protected readonly counts = computed(() => {
    const list = this.monitors() ?? [];
    const active = list.filter((m) => !m.paused);
    return {
      up: active.filter((m) => m.status === 'UP').length,
      slow: active.filter((m) => m.status === 'DEGRADED').length,
      down: active.filter((m) => m.status === 'DOWN').length,
      paused: list.length - active.length,
    };
  });

  /** The single answer the page leads with: is anything broken right now? */
  protected readonly overall = computed(() => {
    const { up, slow, down } = this.counts();
    const plural = (n: number, one: string, many: string) => `${n} ${n === 1 ? one : many}`;
    if (down > 0) {
      const names = (this.monitors() ?? []).filter((m) => !m.paused && m.status === 'DOWN').map((m) => m.name);
      return { tone: 'down', headline: `${plural(down, 'monitor is', 'monitors are')} down`, detail: names.join(', ') };
    }
    if (slow > 0) return { tone: 'slow', headline: `${plural(slow, 'monitor is', 'monitors are')} slow`, detail: this.slowAfterMs() ? `Passing, but taking over ${secondsLabel(this.slowAfterMs()!)} to respond.` : 'Passing, but responding slowly.' };
    if (up > 0) return { tone: 'up', headline: `All ${plural(up, 'monitor is', 'monitors are')} up`, detail: 'Every check in the last round passed.' };
    return { tone: 'waiting', headline: 'Waiting for the first checks', detail: 'Results appear here within a few seconds.' };
  });


  protected readonly timeAgo = timeAgo;
  protected readonly intervalLabel = intervalLabel;
  protected readonly clockTime = clockTime;
  protected readonly durationLabel = durationLabel;

  protected downFor(since: string): string {
    return durationLabel(this.now() - Date.parse(since));
  }

  constructor() {
    poll(() => this.load(), 15_000);
  }

  protected beatsFor(id: number): CheckResult[] {
    return this.heartbeats()[id] ?? [];
  }

  protected latestMs(id: number): number | null {
    const beats = this.beatsFor(id);
    const last = beats[beats.length - 1];
    return last?.success ? last.responseTimeMs : null;
  }

  protected now(): number {
    return this.updatedAt()?.getTime() ?? Date.now();
  }

  private load(): void {
    forkJoin({ monitors: this.api.list(), heartbeats: this.api.heartbeats(30) }).subscribe({
      next: ({ monitors, heartbeats }) => {
        this.monitors.set([...monitors].sort((a, b) => a.name.localeCompare(b.name)));
        this.heartbeats.set(heartbeats);
        this.error.set(null);
        this.updatedAt.set(new Date());
      },
      error: (e) => this.error.set(problemOf(e).detail ?? 'Couldn’t load monitors.'),
    });
  }
}

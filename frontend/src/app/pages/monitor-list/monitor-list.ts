import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { CheckResult, Monitor, MonitorApi, problemOf } from '../../api/monitor-api';
import { HeartbeatStrip } from '../../shared/heartbeat-strip';
import { poll } from '../../shared/polling';
import { StatusBadge } from '../../shared/status-badge';
import { clockTime, durationLabel, intervalLabel, timeAgo } from '../../shared/time';

@Component({
  selector: 'app-monitor-list',
  imports: [RouterLink, HeartbeatStrip, StatusBadge],
  templateUrl: './monitor-list.html',
  styleUrl: './monitor-list.scss',
})
export class MonitorList {
  private readonly api = inject(MonitorApi);

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
    };
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

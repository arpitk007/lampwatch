import { Component, computed, DestroyRef, inject, input, numberAttribute, OnInit, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { forkJoin, Observable } from 'rxjs';
import { CheckResult, HourPoint, Incident, Monitor, MonitorApi, problemOf } from '../../api/monitor-api';
import { HeartbeatStrip } from '../../shared/heartbeat-strip';
import { ResponseChart } from '../../shared/response-chart';
import { poll } from '../../shared/polling';
import { StatusBadge } from '../../shared/status-badge';
import { clockTime, durationLabel, intervalLabel, timeAgo } from '../../shared/time';

@Component({
  selector: 'app-monitor-detail',
  imports: [RouterLink, HeartbeatStrip, StatusBadge, ResponseChart],
  templateUrl: './monitor-detail.html',
  styleUrl: './monitor-detail.scss',
})
export class MonitorDetail implements OnInit {
  private readonly api = inject(MonitorApi);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly id = input.required({ transform: numberAttribute });

  protected readonly monitor = signal<Monitor | null>(null);
  /** Newest first, as the API returns them. */
  protected readonly checks = signal<CheckResult[]>([]);
  protected readonly incidents = signal<Incident[]>([]);
  protected readonly range = signal<24 | 168>(24);
  protected readonly hourly = signal<HourPoint[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly updatedAt = signal<Date | null>(null);

  protected readonly oldestFirst = computed(() => [...this.checks()].reverse());
  protected readonly stats = computed(() => {
    const checks = this.checks();
    if (checks.length === 0) return null;
    const passed = checks.filter((c) => c.success);
    const times = passed.map((c) => c.responseTimeMs ?? 0);
    return {
      uptime: ((passed.length / checks.length) * 100).toFixed(passed.length === checks.length ? 0 : 1),
      average: times.length ? Math.round(times.reduce((a, b) => a + b, 0) / times.length) : null,
      count: checks.length,
    };
  });

  protected readonly timeAgo = timeAgo;
  protected readonly intervalLabel = intervalLabel;
  protected readonly clockTime = clockTime;

  protected lasted(i: Incident): string {
    return durationLabel((i.resolvedAt ? Date.parse(i.resolvedAt) : this.now()) - Date.parse(i.startedAt));
  }

  protected startedLabel(i: Incident): string {
    return new Date(i.startedAt).toLocaleString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
  }

  ngOnInit(): void {
    // Not in the constructor: the route id input is only set by now.
    poll(() => this.load(), 15_000, this.destroyRef);
  }

  protected now(): number {
    return this.updatedAt()?.getTime() ?? Date.now();
  }

  protected setRange(hours: 24 | 168): void {
    this.range.set(hours);
    this.loadHourly();
  }

  protected hourLabel(p: HourPoint): string {
    return new Date(p.hour).toLocaleString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
  }

  private loadHourly(): void {
    this.api.responseTimes(this.id(), this.range()).subscribe({ next: (points) => this.hourly.set(points) });
  }

  protected togglePause(m: Monitor): void {
    this.act(m.paused ? this.api.resume(m.id) : this.api.pause(m.id), (updated) => this.monitor.set(updated));
  }

  protected remove(m: Monitor): void {
    if (!confirm(`Delete “${m.name}” and all of its check history? This can’t be undone.`)) return;
    this.act(this.api.delete(m.id), () => this.router.navigate(['/']));
  }

  private act<T>(request: Observable<T>, done: (value: T) => void): void {
    this.busy.set(true);
    request.subscribe({
      next: (value) => {
        this.busy.set(false);
        done(value);
      },
      error: (e) => {
        this.busy.set(false);
        this.error.set(problemOf(e).detail ?? 'That didn’t work. Try again.');
      },
    });
  }

  private load(): void {
    forkJoin({
      monitor: this.api.get(this.id()),
      checks: this.api.checks(this.id(), 90),
      incidents: this.api.incidents(this.id(), 10),
    }).subscribe({
      next: ({ monitor, checks, incidents }) => {
        this.monitor.set(monitor);
        this.checks.set(checks);
        this.incidents.set(incidents);
        this.loadHourly();
        this.error.set(null);
        this.updatedAt.set(new Date());
      },
      error: (e) => this.error.set(problemOf(e).detail ?? 'Couldn’t load this monitor.'),
    });
  }
}

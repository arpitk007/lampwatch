import { Component, ElementRef, OnDestroy, afterNextRender, computed, inject, input, signal } from '@angular/core';
import { HourPoint } from '../api/monitor-api';

interface Slot {
  start: number; // epoch ms of the hour
  point: HourPoint | null;
}

const HOUR = 3_600_000;
const HEIGHT = 180;
const PAD = { top: 12, right: 12, bottom: 28, left: 56 };

/**
 * Hourly average response time as a single 2px line. Hours without checks break the
 * line rather than being bridged. A crosshair snaps to the nearest hour on hover or
 * with the arrow keys; hours with failed checks get a red tick under the axis.
 */
@Component({
  selector: 'app-response-chart',
  template: `
    <div class="frame" tabindex="0" role="group"
         [attr.aria-label]="summary() + '. Use the arrow keys to read each hour.'"
         (keydown)="onKey($event)" (focus)="focusLast()" (blur)="active.set(null)">
      <svg [attr.width]="width()" [attr.height]="height" [attr.viewBox]="'0 0 ' + width() + ' ' + height"
           (pointermove)="onPointer($event)" (pointerleave)="active.set(null)" aria-hidden="true">
        @for (t of yTicks(); track t.value) {
          <line class="grid" [attr.x1]="pad.left" [attr.x2]="width() - pad.right" [attr.y1]="t.y" [attr.y2]="t.y" />
          <text class="axis" [attr.x]="pad.left - 8" [attr.y]="t.y + 4" text-anchor="end">{{ t.label }}</text>
        }
        @for (t of xTicks(); track t.x) {
          <text class="axis" [attr.x]="t.x" [attr.y]="height - 8" text-anchor="middle">{{ t.label }}</text>
        }
        @for (f of failureTicks(); track f) {
          <rect class="fail" [attr.x]="f - 1.5" [attr.y]="height - pad.bottom + 3" width="3" height="6" rx="1" />
        }
        @for (d of paths(); track $index) {
          <path class="line" [attr.d]="d" />
        }
        @if (activeSlot(); as a) {
          <line class="crosshair" [attr.x1]="a.x" [attr.x2]="a.x" [attr.y1]="pad.top" [attr.y2]="height - pad.bottom" />
          @if (a.y !== null) { <circle class="dot" [attr.cx]="a.x" [attr.cy]="a.y" r="4.5" /> }
        }
      </svg>
      @if (activeSlot(); as a) {
        <div class="tip" [style.left.px]="a.tipX">{{ a.label }}</div>
      }
    </div>
    <span class="visually-hidden" aria-live="polite">{{ activeSlot()?.label ?? '' }}</span>
  `,
  styles: `
    :host { display: block; }
    .frame { position: relative; }
    svg { display: block; overflow: visible; }
    .grid { stroke: var(--grid); stroke-width: 1; }
    .axis { fill: var(--muted); font-size: 12px; font-family: inherit; }
    .line { fill: none; stroke: var(--series-1); stroke-width: 2; stroke-linejoin: round; stroke-linecap: round; }
    .fail { fill: var(--down); }
    .crosshair { stroke: var(--muted); stroke-width: 1; }
    .dot { fill: var(--series-1); stroke: var(--surface); stroke-width: 2; }
    .tip {
      position: absolute;
      top: -6px;
      transform: translate(-50%, -100%);
      padding: 0.35rem 0.6rem;
      background: var(--tooltip-bg);
      color: var(--tooltip-ink);
      font-size: var(--text-xs);
      white-space: nowrap;
      border-radius: 4px;
      pointer-events: none;
      z-index: 5;
    }
    .frame:focus-visible { outline-offset: 6px; }
  `,
})
export class ResponseChart implements OnDestroy {
  readonly points = input.required<HourPoint[]>();
  readonly hours = input(24);

  protected readonly height = HEIGHT;
  protected readonly pad = PAD;
  protected readonly width = signal(600);
  protected readonly active = signal<number | null>(null);

  private readonly host = inject(ElementRef<HTMLElement>);
  private observer?: ResizeObserver;

  constructor() {
    afterNextRender(() => {
      this.observer = new ResizeObserver(([entry]) => this.width.set(Math.max(280, Math.floor(entry.contentRect.width))));
      this.observer.observe(this.host.nativeElement);
    });
  }

  ngOnDestroy(): void {
    this.observer?.disconnect();
  }

  /** Every hour in the range, oldest first, with its data or null. */
  private readonly slots = computed<Slot[]>(() => {
    const byHour = new Map(this.points().map((p) => [Date.parse(p.hour), p]));
    const last = Math.floor(Date.now() / HOUR) * HOUR;
    return Array.from({ length: this.hours() }, (_, i) => {
      const start = last - (this.hours() - 1 - i) * HOUR;
      return { start, point: byHour.get(start) ?? null };
    });
  });

  private readonly yMax = computed(() => niceCeiling(Math.max(100, ...this.points().map((p) => p.avgMs ?? 0))));

  private x(i: number): number {
    const inner = this.width() - PAD.left - PAD.right;
    return PAD.left + (this.hours() === 1 ? inner / 2 : (i / (this.hours() - 1)) * inner);
  }

  private y(ms: number): number {
    return PAD.top + (1 - ms / this.yMax()) * (HEIGHT - PAD.top - PAD.bottom);
  }

  protected readonly yTicks = computed(() => {
    const max = this.yMax();
    return [0, max / 2, max].map((value) => ({ value, y: this.y(value), label: msLabel(value) }));
  });

  protected readonly xTicks = computed(() => {
    const slots = this.slots();
    const every = this.hours() <= 24 ? 6 : 24;
    const ticks: { x: number; label: string }[] = [];
    slots.forEach((s, i) => {
      const d = new Date(s.start);
      if (d.getHours() % every === 0 && (every === 6 || d.getHours() === 0)) {
        ticks.push({ x: this.x(i), label: every === 6 ? hourLabel(d) : d.toLocaleDateString([], { weekday: 'short', day: 'numeric' }) });
      }
    });
    // Drop labels that would collide on narrow screens.
    const spaced: typeof ticks = [];
    for (const t of ticks) {
      if (spaced.length === 0 || t.x - spaced[spaced.length - 1].x >= 56) spaced.push(t);
    }
    return spaced;
  });

  /** One path per run of consecutive hours with a value, so gaps aren't bridged. */
  protected readonly paths = computed(() => {
    const out: string[] = [];
    let d = '';
    this.slots().forEach((s, i) => {
      const ms = s.point?.avgMs;
      if (ms === null || ms === undefined) {
        if (d) out.push(d);
        d = '';
        return;
      }
      d += `${d ? 'L' : 'M'}${this.x(i).toFixed(1)},${this.y(ms).toFixed(1)}`;
    });
    if (d) out.push(d);
    return out;
  });

  protected readonly failureTicks = computed(() =>
    this.slots().flatMap((s, i) => (s.point && s.point.failures > 0 ? [this.x(i)] : [])),
  );

  protected readonly activeSlot = computed(() => {
    const i = this.active();
    if (i === null) return null;
    const s = this.slots()[i];
    const x = this.x(i);
    const ms = s.point?.avgMs ?? null;
    return {
      x,
      y: ms === null ? null : this.y(ms),
      tipX: Math.min(Math.max(x, 110), this.width() - 110),
      label: slotLabel(s, this.hours()),
    };
  });

  protected readonly summary = computed(() => {
    const values = this.points().map((p) => p.avgMs).filter((v): v is number => v !== null);
    if (values.length === 0) return `No response times in the last ${rangeLabel(this.hours())}`;
    const avg = Math.round(values.reduce((a, b) => a + b, 0) / values.length);
    return `Average response time ${avg} ms over the last ${rangeLabel(this.hours())}, slowest hour ${Math.max(...values)} ms`;
  });

  protected onPointer(event: PointerEvent): void {
    const svg = event.currentTarget as SVGSVGElement;
    const px = event.clientX - svg.getBoundingClientRect().left;
    const inner = this.width() - PAD.left - PAD.right;
    const i = Math.round(((px - PAD.left) / inner) * (this.hours() - 1));
    this.active.set(Math.min(this.hours() - 1, Math.max(0, i)));
  }

  protected focusLast(): void {
    this.active.set(this.hours() - 1);
  }

  protected onKey(event: KeyboardEvent): void {
    const last = this.hours() - 1;
    const current = this.active() ?? last;
    const next = { ArrowLeft: current - 1, ArrowRight: current + 1, Home: 0, End: last }[event.key];
    if (next === undefined) return;
    event.preventDefault();
    this.active.set(Math.min(last, Math.max(0, next)));
  }
}

/** Rounds up to 1, 2 or 5 times a power of ten, so axis labels stay clean. */
export function niceCeiling(value: number): number {
  const power = 10 ** Math.floor(Math.log10(value));
  for (const step of [1, 2, 5, 10]) {
    if (value <= step * power) return step * power;
  }
  return 10 * power;
}

export function msLabel(ms: number): string {
  return ms >= 1000 ? `${(ms / 1000).toFixed(ms % 1000 === 0 ? 0 : 1)} s` : `${Math.round(ms)} ms`;
}

function hourLabel(d: Date): string {
  return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

function rangeLabel(hours: number): string {
  return hours <= 24 ? `${hours} hours` : `${Math.round(hours / 24)} days`;
}

function slotLabel(s: Slot, hours: number): string {
  const d = new Date(s.start);
  const when = hours <= 24 ? hourLabel(d) : `${d.toLocaleDateString([], { month: 'short', day: 'numeric' })} ${hourLabel(d)}`;
  if (!s.point) return `${when}: no checks`;
  const avg = s.point.avgMs === null ? 'no successful checks' : `average ${s.point.avgMs} ms`;
  const failed = s.point.failures > 0 ? `, ${s.point.failures} of ${s.point.checks} failed` : `, ${s.point.checks} checks`;
  return `${when}: ${avg}${failed}`;
}

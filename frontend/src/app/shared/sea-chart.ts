import { afterNextRender, Component, computed, DestroyRef, ElementRef, inject, input, output, signal } from '@angular/core';

export type LightState = 'up' | 'slow' | 'down' | 'off';

/** Monitor and public-page statuses as chart lights. */
export const LIGHT_FOR_STATUS: Record<string, LightState> = { UP: 'up', DEGRADED: 'slow', DOWN: 'down', PAUSED: 'off', UNKNOWN: 'off' };

export interface ChartLight {
  name: string;
  state: LightState;
}

interface Placed extends ChartLight {
  x: number;
  y: number;
  /** The chart's light characteristic, e.g. "Fl G 5s": how this light flashes, by state. */
  characteristic: string;
  labelLeft: boolean;
}


/** Nautical-chart notation for each state: green flashing every 5 s, yellow double flash, quick red, unlit. */
const CHARACTERISTIC: Record<LightState, string> = {
  up: 'Fl G 5s',
  slow: 'Fl(2) Y 8s',
  down: 'Q R',
  off: 'Unlit',
};

/** Small deterministic PRNG so the coastline is the same on every load and every server render. */
function rng(seed: number): () => number {
  let s = seed >>> 0;
  return () => {
    s = (s + 0x6d2b79f5) >>> 0;
    let t = s;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/** A smooth closed path through points, as cubic Béziers (Catmull-Rom). */
function smooth(points: [number, number][], closed: boolean): string {
  const p = closed ? [points[points.length - 1], ...points, points[0], points[1]] : [points[0], ...points, points[points.length - 1]];
  let d = `M${p[1][0].toFixed(1)} ${p[1][1].toFixed(1)}`;
  for (let i = 1; i < p.length - 2; i++) {
    const [x0, y0] = p[i - 1];
    const [x1, y1] = p[i];
    const [x2, y2] = p[i + 1];
    const [x3, y3] = p[i + 2];
    const c1 = [x1 + (x2 - x0) / 6, y1 + (y2 - y0) / 6];
    const c2 = [x2 - (x3 - x1) / 6, y2 - (y3 - y1) / 6];
    d += `C${c1[0].toFixed(1)} ${c1[1].toFixed(1)} ${c2[0].toFixed(1)} ${c2[1].toFixed(1)} ${x2.toFixed(1)} ${y2.toFixed(1)}`;
  }
  return closed ? d + 'Z' : d;
}

/** A coastline running down the left edge and along the bottom, plus contours offset out to sea. */
function coast(W: number, H: number, offset: number): [number, number][] {
  const r = rng(7);
  const pts: [number, number][] = [];
  const shoreX = Math.min(130, W * 0.14);
  const shoreY = H - Math.min(120, H * 0.2);
  for (let i = 0; i <= 8; i++) {
    const y = -20 + i * ((shoreY + 20) / 8);
    pts.push([shoreX + Math.sin(i * 1.3) * 30 + r() * 26 + offset, y]);
  }
  for (let i = 1; i <= 12; i++) {
    const x = shoreX + 30 + i * ((W - shoreX + 10) / 12);
    pts.push([x, shoreY + Math.cos(i * 1.1) * 24 + r() * 22 - offset * 0.8]);
  }
  pts.push([W + 30, shoreY - offset * 0.8]);
  return pts;
}

/**
 * A nautical chart of the given services as lights at sea. The geography is decorative but deterministic;
 * the lights, their colours and how they flash come from live status. Everything here is aria-hidden:
 * the page carries the same information as text (the bulletin), which this chart only illustrates.
 */
@Component({
  selector: 'app-sea-chart',
  template: `
    <svg [attr.viewBox]="'0 0 ' + w() + ' ' + h()" preserveAspectRatio="none" aria-hidden="true">
      <defs>
        <clipPath id="sea-frame"><rect x="14" y="14" [attr.width]="w() - 28" [attr.height]="h() - 28" /></clipPath>
      </defs>
      <rect class="water" [attr.width]="w()" [attr.height]="h()" />
      <g clip-path="url(#sea-frame)">
        <path class="shallow" [attr.d]="geo().shallow" />
        @for (c of geo().contours; track $index) { <path class="contour" [class.far]="$index > 0" [attr.d]="c" /> }
        <path class="land" [attr.d]="geo().land" />
        @for (s of soundings(); track $index) {
          <text class="sounding" [attr.x]="s.x" [attr.y]="s.y">{{ s.v }}</text>
        }
        @if (showRose()) {
        <g class="rose" [attr.transform]="'translate(' + rose().x + ' ' + rose().y + ')'">
          <circle r="62" /><circle r="52" />
          @for (t of roseTicks; track $index) {
            <line [attr.x1]="t.x1" [attr.y1]="t.y1" [attr.x2]="t.x2" [attr.y2]="t.y2" [class.major]="t.major" />
          }
          <path class="needle" d="M0 -46 L5 0 L0 46 L-5 0 Z" />
          <text class="rose-n" y="-68">N</text>
        </g>
        }
        @for (l of placed(); track l.name) {
          <g class="light" [class]="'light ' + l.state" [class.lit]="active() === l.name"
             [attr.transform]="'translate(' + l.x + ' ' + l.y + ') scale(' + lightScale() + ')'"
             (pointerenter)="hover.emit(l.name)" (pointerleave)="hover.emit(null)">
            <circle class="hit" r="34" />
            @if (showSectors()) { <path class="sector" d="M-20.8 -40.8 A46 46 0 0 1 43.2 -15.7" /> }
            <path class="flare" d="M2.6 -4.4C5.6 -13.6 13 -21.6 22.4 -25c2-.8 3.6.9 2.6 2.9C21 -13 13 -6 5.1 -2.2z" />
            <circle class="pos" r="4.4" />
 @if (!compact()) {
            <text class="name" [attr.x]="l.labelLeft ? -12 : 14" [attr.text-anchor]="l.labelLeft ? 'end' : 'start'" y="20">{{ l.name }}</text>
            <text class="char" [attr.x]="l.labelLeft ? -12 : 14" [attr.text-anchor]="l.labelLeft ? 'end' : 'start'" y="35">{{ l.characteristic }}</text>
            }
          </g>
        }
      </g>
      @if (showCartouche()) {
        <g class="cartouche" transform="translate(34 34)">
          <rect width="236" height="86" />
          <rect x="4" y="4" width="228" height="78" class="inner" />
          <text class="c-title" x="118" y="34">{{ title() || 'Lampwatch' }}</text>
          <text class="c-sub" x="118" y="54">{{ subtitle() }}</text>
          @if (readAt(); as at) { <text class="c-sub" x="118" y="70">Read {{ readLabel(at) }}</text> }
        </g>
      }
      <!-- the chart border: alternating ink and white bars, like a graduated chart edge -->
      <rect class="edge" x="6" y="6" [attr.width]="w() - 12" [attr.height]="h() - 12" />
      @for (b of borderBars(); track $index) { <rect class="bar" [attr.x]="b.x" [attr.y]="b.y" [attr.width]="b.w" [attr.height]="b.h" /> }
      <rect class="edge inner" x="14" y="14" [attr.width]="w() - 28" [attr.height]="h() - 28" />
      @for (g of graduations(); track $index) {
        <text class="grad" [attr.x]="g.x" [attr.y]="g.y" [attr.text-anchor]="g.anchor">{{ g.label }}</text>
      }
    </svg>
  `,
  styles: `
    :host { display: block; position: relative; overflow: hidden; }
    svg { display: block; width: 100%; height: 100%; }
    .water { fill: var(--chart-water); }
    .shallow { fill: var(--chart-shallow); }
    .contour { fill: none; stroke: var(--chart-contour); stroke-width: 1.2; }
    .contour.far { stroke-dasharray: 2 5; stroke-width: 1; }
    .land { fill: var(--chart-land); stroke: var(--chart-land-edge); stroke-width: 1.2; }
    .sounding { fill: var(--ink-600); font: italic 11px var(--font-chart); opacity: 0.75; }
    .rose circle { fill: none; stroke: var(--accent); stroke-width: 0.9; opacity: 0.7; }
    .rose line { stroke: var(--accent); stroke-width: 0.8; opacity: 0.7; }
    .rose line.major { stroke-width: 1.4; }
    .rose .needle { fill: var(--accent); opacity: 0.55; }
    .rose-n { fill: var(--accent); font: 600 12px var(--font-ui); text-anchor: middle; opacity: 0.8; }
    .edge { fill: none; stroke: var(--ink); stroke-width: 1; }
    .grad { fill: var(--ink-600); font: 500 9px var(--font-ui); letter-spacing: 0.02em; }
    .cartouche rect { fill: var(--surface); stroke: var(--ink); stroke-width: 1.2; }
    .cartouche rect.inner { fill: none; stroke-width: 0.6; }
    .c-title { fill: var(--ink); font: 400 19px var(--font-chart); text-anchor: middle; }
    .c-sub { fill: var(--ink-600); font: italic 11.5px var(--font-chart); text-anchor: middle; }
    .sector { fill: none; stroke-width: 2.2; stroke-dasharray: 1 4; stroke-linecap: round; opacity: 0.75; }
    .up .sector { stroke: var(--up); }
    .slow .sector { stroke: var(--slow-mark); }
    .down .sector { stroke: var(--down); stroke-dasharray: none; stroke-width: 1.6; opacity: 0.55; }
    .off .sector { display: none; }
    .bar { fill: var(--ink); }

    .light { cursor: default; }
    .hit { fill: transparent; }
    .pos { fill: var(--ink); stroke: var(--chart-water); stroke-width: 2; }
    .name { fill: var(--ink); font: italic 17px var(--font-chart); paint-order: stroke; stroke: var(--chart-water); stroke-width: 5px; stroke-linejoin: round; }
    .char { fill: var(--ink-600); font: 500 11px var(--font-ui); letter-spacing: 0.02em; paint-order: stroke; stroke: var(--chart-water); stroke-width: 4px; }
    .flare { transform-origin: 0 0; transition: transform 200ms cubic-bezier(0.16, 1, 0.3, 1); }
    .up .flare { fill: var(--up); animation: fl-single 5s infinite; }
    .slow .flare { fill: var(--slow-mark); animation: fl-double 8s infinite; }
    .down .flare { fill: var(--down); animation: fl-quick 1s infinite; }
    .down .char { fill: var(--down); font-weight: 600; }
    .off .flare { fill: none; stroke: var(--muted); stroke-width: 1.4; stroke-dasharray: 3 3; }
    .lit .flare { transform: scale(1.25); }
    .lit .name { text-decoration: underline; text-decoration-thickness: 1px; text-underline-offset: 4px; }

    /* Each state flashes its characteristic: one flash in 5 s, two in 8 s, or a quick, continuous flash. */
    @keyframes fl-single { 0%, 14% { opacity: 1; } 18%, 100% { opacity: 0.18; } }
    @keyframes fl-double { 0%, 5% { opacity: 1; } 8%, 13% { opacity: 0.18; } 15%, 20% { opacity: 1; } 23%, 100% { opacity: 0.18; } }
    @keyframes fl-quick { 0%, 45% { opacity: 1; } 55%, 100% { opacity: 0.2; } }
    @media (prefers-reduced-motion: reduce) { .flare { animation: none !important; } }
  `,
})
export class SeaChart {
  readonly lights = input.required<ChartLight[]>();
  /** The light to emphasise, e.g. while its bulletin line is hovered. */
  readonly active = input<string | null>(null);
  readonly hover = output<string | null>();
  /** The chart's title, set in its cartouche, e.g. the status page's name. */
  readonly title = input('');
  readonly readAt = input<Date | null>(null);
  /** Off where the page already puts its own brand over the chart's top-left corner. */
  readonly cartouche = input(true);
  /** Second line of the cartouche. */
  readonly subtitle = input('Lights show live status');

  protected readLabel(at: Date): string {
    return at.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  }

  /** The chart is drawn in its container's own pixels, so nothing is cropped or stretched at any size. */
  protected readonly w = signal(720);
  protected readonly h = signal(520);

  constructor() {
    const host = inject(ElementRef<HTMLElement>).nativeElement as HTMLElement;
    const destroyRef = inject(DestroyRef);
    afterNextRender(() => {
      const measure = () => {
        const r = host.getBoundingClientRect();
        if (r.width >= 160 && r.height >= 160) { // ignore transient sizes mid-layout
          this.w.set(Math.round(r.width));
          this.h.set(Math.round(r.height));
        }
      };
      measure();
      const observer = new ResizeObserver(measure);
      observer.observe(host);
      destroyRef.onDestroy(() => observer.disconnect());
    });
  }

  protected readonly geo = computed(() => {
    const W = this.w();
    const H = this.h();
    const frame = (o: number) => [[-40, -40] as [number, number], ...coast(W, H, o), [W + 40, H + 40] as [number, number], [-40, H + 40] as [number, number]];
    return {
      land: smooth(frame(0), true),
      shallow: smooth(frame(38), true),
      contours: [smooth(coast(W, H, 38), false), smooth(coast(W, H, 84), false), smooth(coast(W, H, 140), false)],
    };
  });

  /** Lights and their labels grow with the chart, from 1x on a phone to 1.4x on a wide screen. */
  protected readonly lightScale = computed(() => Math.min(1.4, Math.max(1, this.w() / 700)).toFixed(2));

  /** The cartouche sits over the land, top left, where a chart puts its title; narrow charts leave it out. */
  protected readonly showCartouche = computed(() => this.cartouche() && this.w() >= 640);

  /** Minute figures inside the graduated border, every other bar, like a chart's latitude and longitude scale. */
  protected readonly graduations = computed(() => {
    const W = this.w();
    const H = this.h();
    const out: { x: number; y: number; label: string; anchor: string }[] = [];
    let n = 0;
    for (let x = 6 + 80; x < W - 60; x += 80) out.push({ x, y: 26, label: `${(n += 10) % 60}′`, anchor: 'middle' });
    n = 0;
    for (let y = 6 + 80; y < H - 40; y += 80) out.push({ x: 20, y: y + 3, label: `${(n += 10) % 60}′`, anchor: 'start' });
    return out.filter((g) => !(this.showCartouche() && g.x < 290 && g.y < 140));
  });

  /** Sector arcs need room around each light; a narrow chart's stacked labels don't leave it. */
  protected readonly showSectors = computed(() => this.w() >= 560 && !this.compact());

  /** A short strip (e.g. above a phone sign-in form): lights in one row, no labels. */
  protected readonly compact = computed(() => this.h() < 260);

  /** On a narrow chart there's no room for the rose beside the lights. */
  protected readonly showRose = computed(() => this.w() >= 560);

  /** Top right, small enough to stay clear of the lights. */
  protected readonly rose = computed(() => ({ x: this.w() - 96, y: 104 }));

  protected readonly roseTicks = Array.from({ length: 36 }, (_, i) => {
    const a = (i * 10 * Math.PI) / 180;
    const major = i % 9 === 0;
    const r1 = 52;
    const r2 = major ? 40 : 46;
    return { x1: Math.sin(a) * r1, y1: -Math.cos(a) * r1, x2: Math.sin(a) * r2, y2: -Math.cos(a) * r2, major };
  });

  protected readonly borderBars = computed(() => {
    const W = this.w();
    const H = this.h();
    const bars: { x: number; y: number; w: number; h: number }[] = [];
    const step = 40;
    for (let x = 6; x < W - 6; x += step * 2) {
      const bw = Math.max(0, Math.min(step, W - 14 - x));
      bars.push({ x, y: 6, w: bw, h: 8 }, { x, y: H - 14, w: bw, h: 8 });
    }
    for (let y = 6; y < H - 6; y += step * 2) {
      const bh = Math.max(0, Math.min(step, H - 14 - y));
      bars.push({ x: 6, y, w: 8, h: bh }, { x: W - 14, y, w: 8, h: bh });
    }
    return bars;
  });

  /**
   * Lights sit offshore, spread across the open water in rows, clear of the coast on the left and
   * bottom and of the compass rose top right. Up to 20 on a status page.
   */
  protected readonly placed = computed<Placed[]>(() => {
    const W = this.w();
    const H = this.h();
    const lights = this.lights();
    const n = lights.length;
    const compact = H < 260;
    const narrow = W < 560 && !compact;
    const perRow = compact ? n : narrow ? 1 : n <= 4 ? Math.min(n, 3) : 4;
    const rows = Math.ceil(n / Math.max(1, perRow));
    const left = Math.min(W * 0.3, 260);
    const right = W - (narrow ? 70 : 190);
    // Below the cartouche, whose box ends 120 px down, plus room for each light's sector arc.
    const top = narrow ? 70 : Math.max(this.showCartouche() ? 170 : 96, H * 0.2);
    const bottom = narrow ? H - 150 : H - Math.min(200, H * 0.34); // narrow: room for the key overlay
    return lights.map((l, i) => {
      const row = Math.floor(i / perRow);
      const col = i % perRow;
      const inRow = Math.min(perRow, n - row * perRow);
      const t = inRow === 1 ? 0.5 : col / (inRow - 1);
      if (compact) {
        const x = W * 0.3 + (n === 1 ? 0.5 : i / (n - 1)) * W * 0.55;
        return { ...l, x, y: H * 0.56 + (i % 2) * 14, characteristic: CHARACTERISTIC[l.state], labelLeft: false };
      }
      const x = narrow
        ? left + (row % 2 ? 0.42 : 0.12) * (right - left)
        : left + t * (right - left) * (inRow === 1 ? 1 : 0.86) + (row % 2) * (right - left) * 0.1;
      const y = rows === 1 ? top + (bottom - top) * 0.45 - Math.sin(t * Math.PI) * 50 : top + (row / (rows - 1)) * (bottom - top) + (col % 2) * 26;
      return { ...l, x, y, characteristic: CHARACTERISTIC[l.state], labelLeft: !narrow && x > W * 0.62 };
    });
  });

  /** Depth soundings scattered over open water, skipping anywhere a light, its label or the rose sits. */
  protected readonly soundings = computed(() => {
    const W = this.w();
    const H = this.h();
    const r = rng(42);
    const keepOut = [...this.placed().map((p) => ({ x: p.x + (p.labelLeft ? -70 : 70), y: p.y + 12, rx: 120, ry: 44 })), ...(this.showRose() ? [{ x: this.rose().x, y: this.rose().y, rx: 90, ry: 90 }] : [])];
    const out: { x: number; y: number; v: string }[] = [];
    if (this.compact()) return out; // a short strip has no room for soundings beside its lights
    for (let i = 0; i < 40 && out.length < 16; i++) {
      const x = Math.round(W * 0.28 + r() * W * 0.66);
      const y = Math.round(50 + r() * (H * 0.62));
      const v = String(Math.round(8 + r() * 40));
      if (keepOut.some((k) => ((x - k.x) / k.rx) ** 2 + ((y - k.y) / k.ry) ** 2 < 1)) continue;
      if (out.some((o) => Math.abs(o.x - x) < 34 && Math.abs(o.y - y) < 18)) continue; // soundings never touch
      out.push({ x, y, v });
    }
    return out;
  });
}

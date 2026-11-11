import { TestBed } from '@angular/core/testing';
import { HourPoint } from '../api/monitor-api';
import { msLabel, niceCeiling, ResponseChart } from './response-chart';

describe('chart scales', () => {
  it('rounds the axis maximum to 1, 2 or 5 times a power of ten', () => {
    expect(niceCeiling(87)).toBe(100);
    expect(niceCeiling(340)).toBe(500);
    expect(niceCeiling(1224)).toBe(2000);
  });

  it('labels milliseconds and seconds', () => {
    expect(msLabel(250)).toBe('250 ms');
    expect(msLabel(1000)).toBe('1 s');
    expect(msLabel(1500)).toBe('1.5 s');
  });
});

describe('ResponseChart', () => {
  it('breaks the line over hours without checks and marks hours with failures', async () => {
    const hour = (ago: number) => new Date(Math.floor(Date.now() / 3_600_000) * 3_600_000 - ago * 3_600_000).toISOString();
    const points: HourPoint[] = [
      { hour: hour(5), avgMs: 200, checks: 60, failures: 0 },
      { hour: hour(4), avgMs: 220, checks: 60, failures: 0 },
      // hour 3 has no checks
      { hour: hour(2), avgMs: 240, checks: 60, failures: 2 },
      { hour: hour(1), avgMs: 210, checks: 60, failures: 0 },
    ];
    const fixture = TestBed.createComponent(ResponseChart);
    fixture.componentRef.setInput('points', points);
    fixture.componentRef.setInput('hours', 6);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;

    expect(el.querySelectorAll('path.line').length).toBe(2);
    expect(el.querySelectorAll('rect.fail').length).toBe(1);
    expect(el.querySelector('[role=group]')?.getAttribute('aria-label')).toContain('Average response time 218 ms');
  });
});

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CheckResult } from '../api/monitor-api';
import { HeartbeatStrip } from './heartbeat-strip';

function check(success: boolean, ms: number | null, minutesAgo: number): CheckResult {
  return {
    checkedAt: new Date(Date.now() - minutesAgo * 60_000).toISOString(),
    success,
    statusCode: success ? 200 : 503,
    responseTimeMs: ms,
    errorMessage: success ? null : 'HTTP 503',
  };
}

describe('HeartbeatStrip', () => {
  let fixture: ComponentFixture<HeartbeatStrip>;
  const el = () => fixture.nativeElement as HTMLElement;

  async function render(checks: CheckResult[], slots = 5, interactive = false) {
    fixture = TestBed.createComponent(HeartbeatStrip);
    fixture.componentRef.setInput('checks', checks);
    fixture.componentRef.setInput('slots', slots);
    fixture.componentRef.setInput('interactive', interactive);
    await fixture.whenStable();
  }

  it('pads missing checks on the left so the newest bar is always on the right', async () => {
    await render([check(true, 100, 2), check(false, null, 1)]);
    const kinds = Array.from(el().querySelectorAll('.bar')).map((b) => b.classList[1]);
    expect(kinds).toEqual(['empty', 'empty', 'empty', 'up', 'down']);
  });

  it('summarises pass rate for screen readers', async () => {
    await render([check(true, 100, 3), check(true, 200, 2), check(false, null, 1)]);
    expect(el().querySelector('.strip')?.getAttribute('aria-label')).toBe('2 of the last 3 checks passed');
  });

  it('keeps only the most recent checks when there are more than slots', async () => {
    const checks = Array.from({ length: 8 }, (_, i) => check(i < 6, 100, 8 - i));
    await render(checks, 5);
    expect(el().querySelectorAll('.bar.down').length).toBe(2);
    expect(el().querySelectorAll('.bar').length).toBe(5);
  });

  it('reads checks with the arrow keys when interactive', async () => {
    await render([check(true, 120, 2), check(false, null, 1)], 5, true);
    const strip = el().querySelector('.strip') as HTMLElement;
    const live = () => el().querySelector('[aria-live]')?.textContent ?? '';

    strip.dispatchEvent(new FocusEvent('focus'));
    await fixture.whenStable();
    expect(live()).toContain('failed, HTTP 503');

    strip.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft' }));
    await fixture.whenStable();
    expect(live()).toContain('passed in 120 ms');

    strip.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft' }));
    await fixture.whenStable();
    expect(live()).toContain('passed in 120 ms'); // can't move onto empty padding
  });
});

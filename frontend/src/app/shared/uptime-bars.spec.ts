import { TestBed } from '@angular/core/testing';
import { tone, UptimeBars, uptimeLabel } from './uptime-bars';

describe('uptime bands', () => {
  it('maps uptime to the four legend bands', () => {
    expect(tone(null)).toBe('none');
    expect(tone(100)).toBe('full');
    expect(tone(99.5)).toBe('minor');
    expect(tone(98.9)).toBe('major');
  });

  it('never rounds real downtime up to 100%', () => {
    expect(uptimeLabel(99.99)).toBe('99.99% uptime');
    expect(uptimeLabel(100)).toBe('100% uptime');
    expect(uptimeLabel(null)).toBe('no data');
  });
});

describe('UptimeBars', () => {
  it('summarises downtime days for screen readers and reads days by keyboard', async () => {
    const fixture = TestBed.createComponent(UptimeBars);
    fixture.componentRef.setInput('days', [
      { date: '2026-11-09', uptime: 100 },
      { date: '2026-11-10', uptime: 97.5 },
      { date: '2026-11-11', uptime: null },
    ]);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const group = el.querySelector('[role=group]') as HTMLElement;
    expect(group.getAttribute('aria-label')).toContain('Downtime on 1 of the last 3 days');
    expect(Array.from(el.querySelectorAll('.bar')).map((b) => b.classList[1])).toEqual(['full', 'major', 'none']);

    group.dispatchEvent(new FocusEvent('focus'));
    group.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft' }));
    await fixture.whenStable();
    expect(el.querySelector('[aria-live]')?.textContent).toContain('97.50% uptime');
  });
});

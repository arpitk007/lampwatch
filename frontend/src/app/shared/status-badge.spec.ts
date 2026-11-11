import { TestBed } from '@angular/core/testing';
import { MonitorStatus } from '../api/monitor-api';
import { StatusBadge } from './status-badge';

describe('StatusBadge', () => {
  async function label(status: MonitorStatus, paused = false): Promise<string> {
    const fixture = TestBed.createComponent(StatusBadge);
    fixture.componentRef.setInput('status', status);
    fixture.componentRef.setInput('paused', paused);
    await fixture.whenStable();
    return (fixture.nativeElement as HTMLElement).textContent?.trim() ?? '';
  }

  it('names every status in words, never by color alone', async () => {
    expect(await label('UP')).toBe('Up');
    expect(await label('DEGRADED')).toBe('Slow');
    expect(await label('DOWN')).toBe('Down');
    expect(await label('UNKNOWN')).toBe('Waiting');
    expect(await label('DOWN', true)).toBe('Paused');
  });
});

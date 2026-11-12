import { PublicStatusPage } from '../api/status-page-api';
import { durationLabel } from './time';

export interface StatusHeadline {
  tone: 'up' | 'slow' | 'down';
  text: string;
}

/** The one sentence a status page leads with, naming the affected services so visitors don't have to scroll. */
export function statusHeadline(page: PublicStatusPage | null): StatusHeadline {
  const monitors = page?.monitors ?? [];
  const named = (status: string, verb: string) => {
    const names = monitors.filter((m) => m.status === status).map((m) => m.name);
    if (names.length === 0) return `Some services are ${verb}`;
    if (names.length === 1) return `${names[0]} is ${verb}`;
    if (names.length <= 3) return `${names.slice(0, -1).join(', ')} and ${names[names.length - 1]} are ${verb}`;
    return `${names.length} services are ${verb}`;
  };
  switch (page?.overall) {
    case 'MAJOR_OUTAGE':
      return { tone: 'down', text: monitors.length > 1 && monitors.every((m) => m.status === 'DOWN') ? 'All services are down' : named('DOWN', 'down') };
    case 'PARTIAL_OUTAGE':
      return { tone: 'down', text: named('DOWN', 'down') };
    case 'DEGRADED':
      return { tone: 'slow', text: named('DEGRADED', 'slow') };
    default:
      return { tone: 'up', text: 'All systems operational' };
  }
}

/** One fixed-form bulletin reading per service, the way a forecast reads each sea area: "Down for 1 d. 99.66% over 30 days." */
export function serviceReading(m: PublicStatusPage['monitors'][number], page: PublicStatusPage, now = Date.now()): string {
  const month = m.uptime30d === null ? '' : ` ${m.uptime30d >= 100 ? '100' : m.uptime30d.toFixed(2)}% over 30 days.`;
  switch (m.status) {
    case 'DOWN': {
      const open = page.incidents.find((i) => i.monitorName === m.name && !i.resolvedAt);
      const length = open ? ` for ${durationLabel(now - Date.parse(open.startedAt))}` : '';
      return `Down${length}.${month}`;
    }
    case 'DEGRADED':
      return `Slower than usual.${month}`;
    case 'UP':
      return `Good.${month}`;
    default:
      return 'Not being checked.';
  }
}

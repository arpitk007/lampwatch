import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AlertsApi, Channel, ChannelType } from '../../api/alerts-api';
import { problemOf } from '../../api/monitor-api';
import { timeAgo } from '../../shared/time';

@Component({
  selector: 'app-alerts-page',
  imports: [ReactiveFormsModule],
  templateUrl: './alerts-page.html',
  styleUrl: './alerts-page.scss',
})
export class AlertsPage {
  private readonly api = inject(AlertsApi);

  protected readonly channels = signal<Channel[] | null>(null);
  protected readonly error = signal<string | null>(null);
  /** Per-channel feedback after "Send test" or a failed action. */
  protected readonly notes = signal<Record<number, { text: string; ok: boolean }>>({});
  protected readonly busy = signal(false);
  protected readonly formError = signal<string | null>(null);

  protected readonly form = inject(NonNullableFormBuilder).group({
    type: ['EMAIL' as ChannelType],
    target: ['', [Validators.required, Validators.maxLength(254)]],
  });
  private readonly type = toSignal(this.form.controls.type.valueChanges, { initialValue: this.form.controls.type.value });
  protected readonly isTelegram = computed(() => this.type() === 'TELEGRAM');

  protected readonly typeLabel: Record<ChannelType, string> = { EMAIL: 'Email', TELEGRAM: 'Telegram' };
  protected readonly timeAgo = timeAgo;
  protected readonly now = () => Date.now();

  constructor() {
    this.load();
  }

  protected deliveryLine(c: Channel): string {
    const d = c.lastDelivery;
    if (!d) return 'Nothing sent yet';
    const when = timeAgo(d.at, Date.now());
    switch (d.status) {
      case 'SENT':
        return `Last sent ${when}: ${d.subject}`;
      case 'PENDING':
        return d.error ? `Retrying: ${d.error}` : `Sending: ${d.subject}`;
      case 'FAILED':
        return `Couldn’t deliver “${d.subject}”: ${d.error ?? 'unknown error'}`;
    }
  }

  protected sendTest(c: Channel): void {
    this.api.sendTest(c.id).subscribe({
      next: () => {
        this.note(c.id, 'Test sent. It should arrive within a few seconds.', true);
        setTimeout(() => this.load(), 7000);
      },
      error: (e) => this.note(c.id, problemOf(e).detail ?? 'Couldn’t send a test.', false),
    });
  }

  protected remove(c: Channel): void {
    if (!confirm(`Stop sending alerts to ${c.target}?`)) return;
    this.api.remove(c.id).subscribe({
      next: () => this.load(),
      error: (e) => this.note(c.id, problemOf(e).detail ?? 'Couldn’t remove this channel.', false),
    });
  }

  protected add(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    const { type, target } = this.form.getRawValue();
    this.busy.set(true);
    this.formError.set(null);
    this.api.add(type, target.trim()).subscribe({
      next: () => {
        this.busy.set(false);
        this.form.controls.target.reset('');
        this.load();
      },
      error: (e) => {
        this.busy.set(false);
        this.formError.set(problemOf(e).detail ?? 'Couldn’t add this channel.');
      },
    });
  }

  private note(id: number, text: string, ok: boolean): void {
    this.notes.update((n) => ({ ...n, [id]: { text, ok } }));
  }

  private load(): void {
    this.api.list().subscribe({
      next: (list) => {
        this.channels.set(list);
        this.error.set(null);
      },
      error: (e) => this.error.set(problemOf(e).detail ?? 'Couldn’t load alert channels.'),
    });
  }
}

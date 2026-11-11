import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { Monitor, MonitorApi, problemOf } from '../../api/monitor-api';
import { StatusPageApi, StatusPageSettings } from '../../api/status-page-api';

@Component({
  selector: 'app-status-settings',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './status-settings.html',
  styleUrl: './status-settings.scss',
})
export class StatusSettings {
  private readonly api = inject(StatusPageApi);
  private readonly monitorsApi = inject(MonitorApi);

  protected readonly origin = window.location.origin;
  protected readonly monitors = signal<Monitor[] | null>(null);
  /** Saved settings, or null when the user has no page yet. */
  protected readonly saved = signal<StatusPageSettings | null>(null);
  /** Monitors on the page, in display order. */
  protected readonly selected = signal<number[]>([]);
  protected readonly busy = signal(false);
  protected readonly message = signal<{ text: string; ok: boolean } | null>(null);
  protected readonly serverErrors = signal<Record<string, string>>({});

  protected readonly form = inject(NonNullableFormBuilder).group({
    title: ['', [Validators.required, Validators.maxLength(80)]],
    slug: ['', [Validators.required, Validators.pattern(/^[a-z0-9](?:[a-z0-9-]{1,38}[a-z0-9])$/)]],
  });

  constructor() {
    forkJoin({
      monitors: this.monitorsApi.list(),
      page: this.api.mine().pipe(catchError(() => of(null))),
    }).subscribe(({ monitors, page }) => {
      this.monitors.set(monitors);
      this.saved.set(page);
      if (page) {
        this.form.setValue({ title: page.title, slug: page.slug });
        this.selected.set(page.monitorIds);
      } else {
        this.selected.set(monitors.map((m) => m.id));
      }
    });
  }

  protected isSelected(id: number): boolean {
    return this.selected().includes(id);
  }

  protected toggle(id: number, on: boolean): void {
    this.selected.update((ids) => (on ? [...ids, id] : ids.filter((x) => x !== id)));
  }

  protected slugError(): string | null {
    const server = this.serverErrors()['slug'];
    if (server) return `Address ${server}.`;
    const c = this.form.controls.slug;
    if (!c.touched || c.valid) return null;
    return c.hasError('required') ? 'Choose an address.' : 'Use 3 to 40 lowercase letters, digits or hyphens.';
  }

  protected save(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    const { title, slug } = this.form.getRawValue();
    // Keep the order monitors appear in the list, which is how the page shows them.
    const order = (this.monitors() ?? []).map((m) => m.id).filter((id) => this.selected().includes(id));
    this.busy.set(true);
    this.message.set(null);
    this.serverErrors.set({});
    this.api.save({ title: title.trim(), slug: slug.trim().toLowerCase(), monitorIds: order }).subscribe({
      next: (page) => {
        this.busy.set(false);
        this.saved.set(page);
        this.message.set({ text: 'Saved. Your page is live.', ok: true });
      },
      error: (e) => {
        this.busy.set(false);
        const problem = problemOf(e);
        this.serverErrors.set(problem.errors ?? {});
        this.message.set({ text: problem.errors ? 'Fix the highlighted field.' : (problem.detail ?? 'Couldn’t save.'), ok: false });
      },
    });
  }

  protected remove(): void {
    if (!confirm('Take your status page offline? Anyone with the link will see “No status page here”.')) return;
    this.api.remove().subscribe({
      next: () => {
        this.saved.set(null);
        this.message.set({ text: 'Your status page is offline.', ok: true });
      },
      error: (e) => this.message.set({ text: problemOf(e).detail ?? 'Couldn’t remove it.', ok: false }),
    });
  }
}

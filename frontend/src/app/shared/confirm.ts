import { Component, effect, ElementRef, inject, Injectable, signal, viewChild } from '@angular/core';

export interface ConfirmRequest {
  title: string;
  body: string;
  /** Names the action, e.g. "Delete monitor"; the toast or result should reuse the same verb. */
  confirmLabel: string;
}

/** In-app replacement for window.confirm(): same promise shape, but styled, and Cancel holds the default focus. */
@Injectable({ providedIn: 'root' })
export class Confirm {
  readonly pending = signal<(ConfirmRequest & { resolve: (ok: boolean) => void }) | null>(null);

  ask(request: ConfirmRequest): Promise<boolean> {
    this.pending()?.resolve(false);
    return new Promise((resolve) => this.pending.set({ ...request, resolve }));
  }

  settle(ok: boolean): void {
    this.pending()?.resolve(ok);
    this.pending.set(null);
  }
}

/** Rendered once in the app root. A native <dialog> gives focus trapping, Esc and the backdrop for free. */
@Component({
  selector: 'app-confirm-dialog',
  template: `
    <dialog #dialog aria-labelledby="confirm-title" aria-describedby="confirm-body" (cancel)="confirm.settle(false)" (click)="onBackdrop($event)">
      @if (confirm.pending(); as r) {
        <div class="body">
        <h2 id="confirm-title">{{ r.title }}</h2>
        <p id="confirm-body">{{ r.body }}</p>
        <div class="actions">
          <button class="button" type="button" autofocus (click)="confirm.settle(false)">Cancel</button>
          <button class="button destructive" type="button" (click)="confirm.settle(true)">{{ r.confirmLabel }}</button>
        </div>
        </div>
      }
    </dialog>
  `,
  styles: `
    dialog {
      width: min(28rem, calc(100vw - 2rem));
      padding: 0;
      border: 1px solid var(--line);
      border-radius: var(--radius);
      background: var(--surface);
      color: var(--ink);
      box-shadow: 0 12px 32px color-mix(in srgb, var(--ink) 18%, transparent), 0 2px 6px color-mix(in srgb, var(--ink) 8%, transparent);
    }
    .body { padding: var(--space-6); }
    dialog::backdrop { background: color-mix(in srgb, var(--ink) 45%, transparent); }
    dialog[open] { animation: rise 160ms cubic-bezier(0.16, 1, 0.3, 1); }
    @keyframes rise { from { opacity: 0; transform: translateY(6px); } }
    h2 { margin: 0; font-family: var(--font-chart); font-weight: 400; font-size: 1.375rem; line-height: 1.25; }
    p { margin: var(--space-2) 0 var(--space-6); color: var(--ink-soft); font-size: var(--text-md); line-height: 1.55; }
    .actions { display: flex; justify-content: end; gap: var(--space-2); }
    .destructive { background: var(--down); border-color: var(--down); color: var(--on-ink); }
    .destructive:hover { background: var(--red-900); border-color: var(--red-900); }
    @media (max-width: 480px) {
      .actions { flex-direction: column-reverse; }
      .actions .button { width: 100%; }
    }
  `,
})
export class ConfirmDialog {
  protected readonly confirm = inject(Confirm);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  constructor() {
    effect(() => {
      const el = this.dialog().nativeElement;
      if (this.confirm.pending() && !el.open) {
        el.showModal();
        // The buttons render in the same change detection pass, after this runs; focus the safe choice once they exist.
        setTimeout(() => el.querySelector<HTMLButtonElement>('[autofocus]')?.focus());
      }
      else if (!this.confirm.pending() && el.open) el.close();
    });
  }

  /** A click on the backdrop lands on the <dialog> itself; treat it as Cancel. */
  protected onBackdrop(event: MouseEvent): void {
    if (event.target === this.dialog().nativeElement) this.confirm.settle(false);
  }
}

import { Component, computed, inject, input, numberAttribute, OnInit, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { CheckType, MonitorApi, MonitorInput, problemOf } from '../../api/monitor-api';
import { NotFound } from '../not-found/not-found';

@Component({
  selector: 'app-monitor-form',
  imports: [ReactiveFormsModule, RouterLink, NotFound],
  templateUrl: './monitor-form.html',
  styleUrl: './monitor-form.scss',
})
export class MonitorForm implements OnInit {
  private readonly api = inject(MonitorApi);
  private readonly router = inject(Router);

  /** Route param; absent when adding a new monitor. */
  readonly id = input<number | undefined, unknown>(undefined, {
    transform: (v: unknown) => (v == null ? undefined : numberAttribute(v)),
  });

  protected readonly intervals = [
    { seconds: 60, label: 'Every minute' },
    { seconds: 300, label: 'Every 5 minutes' },
    { seconds: 600, label: 'Every 10 minutes' },
    { seconds: 1800, label: 'Every 30 minutes' },
    { seconds: 3600, label: 'Every hour' },
  ];
  protected readonly timeouts = [5000, 10000, 15000, 30000];

  protected readonly form = inject(NonNullableFormBuilder).group({
    name: ['', [Validators.required, Validators.maxLength(100)]],
    url: ['https://', [Validators.required, Validators.maxLength(2048), Validators.pattern(/^https?:\/\/\S+$/)]],
    checkType: ['HTTP' as CheckType],
    keyword: ['', Validators.maxLength(255)],
    intervalSeconds: [300],
    timeoutMs: [10000],
  });

  private readonly checkType = toSignal(this.form.controls.checkType.valueChanges, {
    initialValue: this.form.controls.checkType.value,
  });
  protected readonly needsKeyword = computed(() => this.checkType() === 'KEYWORD');
  protected readonly editing = computed(() => this.id() !== undefined);

  protected readonly missing = signal(false);
  protected readonly saving = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly serverErrors = signal<Record<string, string>>({});

  ngOnInit(): void {
    const id = this.id();
    if (id !== undefined) {
      this.api.get(id).subscribe({
        next: (m) =>
          this.form.setValue({
            name: m.name,
            url: m.url,
            checkType: m.checkType,
            keyword: m.keyword ?? '',
            intervalSeconds: m.intervalSeconds,
            timeoutMs: m.timeoutMs,
          }),
        error: (e) => {
          if (e?.status === 404) this.missing.set(true);
          else this.formError.set(problemOf(e).detail ?? 'Couldn’t load this monitor.');
        },
      });
    }
  }

  protected fieldError(field: 'name' | 'url' | 'keyword'): string | null {
    const server = this.serverErrors()[field];
    if (server) return server;
    const control = this.form.controls[field];
    if (!control.touched || control.valid) return null;
    if (control.hasError('required')) return field === 'name' ? 'Enter a name.' : 'Enter a URL.';
    if (control.hasError('pattern')) return 'Use a full address starting with http:// or https://.';
    if (control.hasError('maxlength')) return 'This is too long.';
    return null;
  }

  protected keywordError(): string | null {
    const control = this.form.controls.keyword;
    if (this.serverErrors()['keyword']) return this.serverErrors()['keyword'];
    if (this.needsKeyword() && control.touched && control.value.trim() === '') {
      return 'Enter the text the page should contain.';
    }
    return null;
  }

  protected save(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.keywordError()) return;

    const value = this.form.getRawValue();
    const input: MonitorInput = {
      ...value,
      name: value.name.trim(),
      url: value.url.trim(),
      keyword: value.checkType === 'KEYWORD' ? value.keyword.trim() : null,
    };

    this.saving.set(true);
    this.formError.set(null);
    this.serverErrors.set({});
    const id = this.id();
    const request = id === undefined ? this.api.create(input) : this.api.update(id, input);
    request.subscribe({
      next: (m) => this.router.navigate(['/monitors', m.id]),
      error: (e) => {
        const problem = problemOf(e);
        this.serverErrors.set(problem.errors ?? {});
        this.formError.set(problem.errors ? 'Fix the highlighted fields.' : (problem.detail ?? 'Couldn’t save.'));
        this.saving.set(false);
      },
    });
  }
}

import { HttpErrorResponse, HttpInterceptorFn, HttpResponse } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { tap } from 'rxjs';

/**
 * Whether the API answers at all. Pages show their own errors for a request that failed; this is the
 * one app-wide fact that the server itself can't be reached, so it is said once instead of on every panel.
 */
@Injectable({ providedIn: 'root' })
export class ServerHealth {
  readonly unreachable = signal(false);
}

/** No answer (status 0) or a gateway saying the app behind it is down. */
const UNREACHABLE = new Set([0, 502, 503, 504]);

export const watchServer: HttpInterceptorFn = (req, next) => {
  const health = inject(ServerHealth);
  return next(req).pipe(
    tap({
      next: (event) => {
        if (event instanceof HttpResponse) health.unreachable.set(false);
      },
      error: (error: unknown) => {
        if (error instanceof HttpErrorResponse) health.unreachable.set(UNREACHABLE.has(error.status));
      },
    }),
  );
};

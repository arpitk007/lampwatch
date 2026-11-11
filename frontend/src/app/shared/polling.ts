import { DestroyRef, inject } from '@angular/core';

/**
 * Runs `load` now and every `ms` while the tab is visible; refreshes immediately
 * when the tab becomes visible again. Stops when the calling component is destroyed.
 * Call it from a constructor, or pass `destroyRef` when calling from ngOnInit (after inputs are set).
 */
export function poll(load: () => void, ms: number, destroyRef: DestroyRef = inject(DestroyRef)): void {
  let timer: ReturnType<typeof setInterval> | undefined;
  const start = () => {
    stop();
    load();
    timer = setInterval(load, ms);
  };
  const stop = () => {
    if (timer !== undefined) clearInterval(timer);
    timer = undefined;
  };
  const onVisibility = () => (document.hidden ? stop() : start());

  document.addEventListener('visibilitychange', onVisibility);
  destroyRef.onDestroy(() => {
    stop();
    document.removeEventListener('visibilitychange', onVisibility);
  });
  if (!document.hidden) start();
}

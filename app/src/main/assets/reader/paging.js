// Shared contract with ReaderPageMotion.kt: dp/CSS px velocity, one page per gesture.
export const PAGE_TURN_MS = 300;
export const pageTurnEase = fraction => {
  const value = Math.max(0, Math.min(1, fraction));
  return 1 - (1 - value) * (1 - value);
};
export function pageTurnDelta(displacement, velocity) {
  const drag = Number.isFinite(displacement) ? Math.max(-1, Math.min(1, displacement)) : 0;
  const speed = Number.isFinite(velocity) ? velocity : 0;
  return Math.max(-1, Math.min(1, Math.floor(drag + speed / 1000 + 0.5)));
}

const dateTime = new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" });
const dateOnly = new Intl.DateTimeFormat(undefined, { dateStyle: "medium" });

export function formatDateTime(value?: string | null): string {
  return value ? dateTime.format(new Date(value)) : "—";
}

/** A calendar date (yyyy-mm-dd) shown without shifting it through a time zone. */
export function formatDate(value?: string | null): string {
  if (!value) return "—";
  const [y, m, d] = value.split("-").map(Number);
  return dateOnly.format(new Date(y ?? 1970, (m ?? 1) - 1, d ?? 1));
}

export function humanize(value?: string | null): string {
  if (!value) return "—";
  const words = value.toLowerCase().replaceAll("_", " ");
  return words.charAt(0).toUpperCase() + words.slice(1);
}

/** Today's date in the browser's time zone as yyyy-mm-dd. */
export function today(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`;
}

export function newIdempotencyKey(): string {
  return `ui-${crypto.randomUUID()}`;
}

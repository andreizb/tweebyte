/**
 * Tiny class-merge helper (the shadcn `cn`), dependency-free.
 *
 * Joins truthy class fragments and de-duplicates whitespace. We deliberately avoid
 * pulling clsx + tailwind-merge to keep the bundle lean; conflicting Tailwind classes
 * should be resolved at the call site (compose intentionally), not magically merged.
 */
export type ClassValue = string | number | null | false | undefined | ClassValue[];

export function cn(...inputs: ClassValue[]): string {
  const out: string[] = [];
  const walk = (v: ClassValue): void => {
    if (!v && v !== 0) {
      return;
    }
    if (Array.isArray(v)) {
      v.forEach(walk);
    } else {
      out.push(String(v));
    }
  };
  inputs.forEach(walk);
  return out.join(' ').replace(/\s+/g, ' ').trim();
}

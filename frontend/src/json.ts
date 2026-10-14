const integers = new Set(['id', 'userId', 'ownerId', 'version', 'inputRevision', 'calendarRevision', 'totalElements']);
const maxLong = 9223372036854775807n;

/** Preserve server long values before JavaScript's number rounding can reach a command or URL. */
export function decodeJson(text: string): unknown {
  return JSON.parse(text, (key: string, value: unknown, context?: { source: string }) => {
    if (typeof value !== 'number') return value;
    if (integers.has(key)) {
      if (!context?.source && !Number.isSafeInteger(value)) throw new Error('Браузер не поддерживает точные ID сервера.');
      const source = context?.source ?? String(value);
      if (!/^\d+$/.test(source)) throw new Error('Некорректный числовой ответ сервера.');
      const exact = BigInt(source);
      if (exact > maxLong) throw new Error('Число выходит за диапазон сервера.');
      return exact;
    }
    if (Number.isInteger(value) && !Number.isSafeInteger(value)) throw new Error('Неточное число в ответе сервера.');
    return value;
  });
}

export function encodeJson(value: unknown): string {
  return JSON.stringify(value, (_key, item: unknown) => {
    if (typeof item !== 'bigint') return item;
    if (item < 0n || item > maxLong) throw new Error('Число выходит за диапазон сервера.');
    const raw = (JSON as typeof JSON & { rawJSON?: (text: string) => unknown }).rawJSON;
    if (typeof raw === 'function') return raw(String(item));
    if (item > BigInt(Number.MAX_SAFE_INTEGER)) throw new Error('Браузер не поддерживает точные ID сервера.');
    return Number(item);
  });
}

export function userId(value: string): bigint {
  if (!/^[1-9]\d{0,18}$/.test(value.trim())) throw new Error('Введите положительный ID пользователя.');
  const id = BigInt(value.trim());
  if (id > maxLong) throw new Error('ID выходит за диапазон сервера.');
  return id;
}

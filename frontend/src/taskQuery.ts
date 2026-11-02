import type { TaskQuery } from './types.ts';

export const emptyTaskQuery = (): TaskQuery =>
  ({ status: '', assigneeId: '', dueFrom: '', dueTo: '', labelId: '', bucket: '' });

/** Builds query string for task list / mine endpoints; omits blank filters. */
export function taskQueryString(base: Record<string, string | number | bigint>, filters: TaskQuery): string {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(base)) params.set(key, String(value));
  if (filters.status) params.set('status', filters.status);
  if (filters.assigneeId) params.set('assigneeId', filters.assigneeId);
  if (filters.dueFrom) params.set('dueFrom', filters.dueFrom);
  if (filters.dueTo) params.set('dueTo', filters.dueTo);
  if (filters.labelId) params.set('labelId', filters.labelId);
  if (filters.bucket) params.set('bucket', filters.bucket);
  return params.toString();
}

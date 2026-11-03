/** Parse `#/path?query` from location.hash. */
export function hashRoute(raw = window.location.hash.slice(1) || '/projects'): {
  path: string;
  params: URLSearchParams;
} {
  const q = raw.indexOf('?');
  if (q < 0) return { path: raw || '/projects', params: new URLSearchParams() };
  return { path: raw.slice(0, q) || '/projects', params: new URLSearchParams(raw.slice(q + 1)) };
}

export function taskQueryFromParams(params: URLSearchParams) {
  return {
    status: params.get('status') ?? '',
    assigneeId: params.get('assigneeId') ?? '',
    dueFrom: params.get('dueFrom') ?? '',
    dueTo: params.get('dueTo') ?? '',
    labelId: params.get('labelId') ?? '',
    bucket: params.get('bucket') ?? ''
  };
}

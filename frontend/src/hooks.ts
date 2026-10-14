import { useEffect, useRef, useState } from 'react';
import { explain } from './client.ts';

export function useLoad<T>(key: string, load: (signal: AbortSignal) => Promise<T>) {
  const request = useRef(load); request.current = load;
  const [revision, setRevision] = useState(0);
  const [state, setState] = useState<{ value?: T; error: string; loading: boolean }>({ error: '', loading: true });
  useEffect(() => {
    const abort = new AbortController();
    setState(previous => ({ ...previous, error: '', loading: true }));
    void request.current(abort.signal).then(value => {
      if (!abort.signal.aborted) setState({ value, error: '', loading: false });
    }, error => {
      if (!abort.signal.aborted) setState(previous => ({ ...previous, error: explain(error), loading: false }));
    });
    return () => abort.abort();
  }, [key, revision]);
  return { ...state, reload: () => setRevision(value => value + 1) };
}

export function useCommand() {
  const guard = useRef(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>();
  const [notice, setNotice] = useState('');
  async function run(action: () => Promise<void>) {
    if (guard.current) return;
    guard.current = true; setBusy(true); setError(undefined); setNotice('');
    try { await action(); }
    catch (failure) { setError(failure); }
    finally { guard.current = false; setBusy(false); }
  }
  return { busy, error, notice, run, setNotice, clear: () => setError(undefined) };
}

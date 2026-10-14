import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { Client, explain } from './client.ts';
import type { SessionEvent } from './client.ts';
import type { User } from './types.ts';

interface State { user: User | null; lastUser: User | null; loading: boolean; error: string; generation: number }
interface SessionContext extends State { client: Client; refresh: () => Promise<void> }
const Context = createContext<SessionContext | null>(null);

export function SessionProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<State>({ user: null, lastUser: null, loading: true, error: '', generation: 0 });
  const channel = useRef<BroadcastChannel | null>(null);
  const accept = useCallback((user: User | null, clearDrafts = false) => {
    setState(previous => ({
      user, lastUser: user ?? (clearDrafts ? null : previous.lastUser), loading: false, error: '',
      generation: previous.generation + (user && previous.user?.id !== user.id ? 1 : 0)
    }));
  }, []);
  const client = useMemo(() => new Client({
    fetch: window.fetch.bind(window), session: accept,
    lock: action => {
      if (!navigator.locks) return Promise.reject(new Error('Этот браузер не поддерживает безопасный вход. Обновите браузер.'));
      return navigator.locks.request('projectflow-browser-session', action);
    },
    announce: (event: SessionEvent) => channel.current?.postMessage(event)
  }), [accept]);
  const refresh = useCallback(async () => {
    try { await client.restore(); }
    catch (error) { setState(previous => ({ ...previous, loading: false, error: explain(error) })); }
  }, [client]);

  useEffect(() => {
    const updates = typeof BroadcastChannel === 'function' ? new BroadcastChannel('projectflow-session') : null;
    channel.current = updates;
    if (updates) updates.onmessage = event => {
      if (event.data !== 'changed' && event.data !== 'signed-out') return;
      setState(previous => ({ ...previous, user: null, loading: true,
        lastUser: event.data === 'signed-out' ? null : previous.lastUser }));
      void refresh();
    };
    void refresh();
    const focus = () => { void refresh(); };
    window.addEventListener('focus', focus);
    return () => { channel.current = null; updates?.close(); window.removeEventListener('focus', focus); };
  }, [refresh]);

  return <Context.Provider value={{ ...state, client, refresh }}>{children}</Context.Provider>;
}

export function useSession(): SessionContext {
  const context = useContext(Context);
  if (!context) throw new Error('Session provider missing.');
  return context;
}

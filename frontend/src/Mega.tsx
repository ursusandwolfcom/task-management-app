import { useState } from 'react';
import type { FormEvent } from 'react';
import { useCommand, useLoad } from './hooks.ts';
import { useSession } from './session.tsx';
import { Alert, Field, Notice } from './ui.tsx';
import type { User } from './types.ts';

interface MegaStatus {
  configured: boolean;
  linked: boolean;
  accountId: string | null;
  linkedAt: string | null;
}

function useMega(user: User) {
  const { client } = useSession();
  const state = useLoad(`mega:${user.id}`, signal => client.request<MegaStatus>(user.id, '/api/mega', 'GET', undefined, signal));
  const command = useCommand();
  const link = async (email: string, password: string, mfaCode: string) => {
    await client.request<MegaStatus>(user.id, '/api/mega/link', 'POST', { email, password, mfaCode: mfaCode || null });
    state.reload();
    command.setNotice('MEGA подключена. Новые вложения будут сохраняться в вашем личном аккаунте.');
  };
  const unlink = async () => {
    await client.request<MegaStatus>(user.id, '/api/mega/link', 'DELETE');
    state.reload();
    command.setNotice('MEGA отключена. Сохранённые файлы остались в прежнем аккаунте.');
  };
  return { state, command, link, unlink };
}

export function MegaAccount({ user }: { user: User }) {
  const { state, command, link, unlink } = useMega(user);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [mfaCode, setMfaCode] = useState('');
  const [acknowledged, setAcknowledged] = useState(false);
  function submit(event: FormEvent) {
    event.preventDefault();
    void command.run(async () => {
      try { await link(email, password, mfaCode); }
      finally { setPassword(''); setMfaCode(''); }
    });
  }
  return <section className="card" aria-labelledby="mega-heading">
    <h2 id="mega-heading">Личный аккаунт MEGA</h2>
    <p>Подключите MEGA для новых вложений. Файлы проекта доступны участникам через ProjectFlow.</p>
    <Alert error={state.error} />
    {state.error && <button onClick={state.reload}>Повторить загрузку MEGA</button>}
    {state.loading && <p role="status">Проверяем подключение MEGA…</p>}
    {state.value && <>
      {state.value.linked && <p>Подключён аккаунт: <strong>{state.value.accountId}</strong></p>}
      {!state.value.configured && <Notice>MEGA пока не настроена администратором приложения.</Notice>}
      {state.value.linked && <>
        <p id="mega-warning">Отключение или смена аккаунта сохранит записи вложений, но файлы из прежнего
          аккаунта станут недоступны до его повторного подключения. Перенос файлов не выполняется.
          Для отзыва сессии MEGA используйте настройки безопасности MEGA.</p>
        <label className="mega-confirmation"><input type="checkbox" checked={acknowledged} disabled={command.busy}
          aria-describedby="mega-warning" onChange={event => setAcknowledged(event.target.checked)} />
          Я понимаю, что прежние файлы могут стать недоступны</label>
        <button className="mega-disconnect" disabled={command.busy || !acknowledged}
          onClick={() => void command.run(async () => {
          await unlink(); setAcknowledged(false);
        })}>Отключить MEGA</button>
      </>}
      {state.value.configured && <form onSubmit={submit}>
        <fieldset disabled={command.busy}>
          <legend>{state.value.linked ? 'Подключить другой аккаунт или обновить вход' : 'Подключить MEGA'}</legend>
          <Field label="Email MEGA" type="email" required maxLength={254} autoComplete="off" value={email}
            onChange={event => setEmail(event.target.value)} />
          <Field label="Пароль MEGA" type="password" required maxLength={128} autoComplete="off" value={password}
            onChange={event => setPassword(event.target.value)} />
          <Field label="Код двухфакторной аутентификации MEGA" inputMode="numeric" pattern="[0-9]{6}"
            maxLength={6} autoComplete="one-time-code" value={mfaCode}
            hint="Если в MEGA включена двухфакторная аутентификация" onChange={event => setMfaCode(event.target.value)} />
          <p className="muted">Сервер получит сессию доступа к вашему MEGA-аккаунту.
            Пароль передаётся для входа и не сохраняется в ProjectFlow.</p>
          <button type="submit" disabled={state.value.linked && !acknowledged}>
            {command.busy ? 'Подключение…' : 'Подключить личную MEGA'}</button>
        </fieldset>
      </form>}
    </>}
    <Alert error={command.error} /><Notice>{command.notice}</Notice>
  </section>;
}

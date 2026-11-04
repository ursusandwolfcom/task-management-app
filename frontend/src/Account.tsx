import { useState } from 'react';
import type { FormEvent } from 'react';
import { ApiError } from './client.ts';
import { useCommand } from './hooks.ts';
import { useSession } from './session.tsx';
import { Alert, Field, Notice, PageHeader } from './ui.tsx';
import type { ProfileFields, User } from './types.ts';
import { MegaAccount } from './Mega.tsx';

function fieldsOf(user: ProfileFields): ProfileFields {
  const { username, email, firstName, lastName } = user;
  return { username, email, firstName, lastName };
}

function IdentityFields({ fields, change }: { fields: ProfileFields; change: (fields: ProfileFields) => void }) {
  return <>
    <Field label="Логин" value={fields.username} required maxLength={50} pattern="[a-z0-9_.-]{3,50}"
      autoComplete="username" autoCapitalize="none" hint="3–50 символов: a–z, цифры, точка, дефис и подчёркивание"
      onChange={event => change({ ...fields, username: event.target.value })} />
    <Field label="Email" type="email" value={fields.email} required maxLength={254} autoComplete="email"
      onChange={event => change({ ...fields, email: event.target.value })} />
    <div className="form-grid"><Field label="Имя" value={fields.firstName} required maxLength={100} autoComplete="given-name"
      onChange={event => change({ ...fields, firstName: event.target.value })} />
    <Field label="Фамилия" value={fields.lastName} required maxLength={100} autoComplete="family-name"
      onChange={event => change({ ...fields, lastName: event.target.value })} /></div>
  </>;
}

export function Auth() {
  const { client, lastUser, error, refresh } = useSession();
  const [register, setRegister] = useState(false);
  const [fields, setFields] = useState<ProfileFields>({ username: '', email: '', firstName: '', lastName: '' });
  const [identifier, setIdentifier] = useState(lastUser?.username ?? '');
  const [password, setPassword] = useState('');
  const command = useCommand();
  function submit(event: FormEvent) {
    event.preventDefault();
    void command.run(async () => {
      if (register) {
        const user = await client.register({ ...fields, password });
        setIdentifier(user.username); setPassword(''); setRegister(false);
        command.setNotice('Аккаунт создан. Войдите с вашим паролем.');
      } else { await client.login({ identifier, password }); setPassword(''); }
    });
  }
  return <main id="auth-content" tabIndex={-1} className="auth-layout">
    <section className="auth-story"><a href="#/projects" className="brand"><span className="brand-mark">PF</span>ProjectFlow</a>
      <div><p className="eyebrow">РАБОЧЕЕ ПРОСТРАНСТВО</p><h1>Команда.<br />Проекты.<br /><span>Общий контекст.</span></h1>
      <p>Всё, что нужно для начала проекта: понятная цель, участники и актуальные данные.</p></div>
      <p className="muted">Один аккаунт — ваши проекты на любом устройстве.</p>
    </section>
    <section className="auth-card card"><p className="eyebrow">PROJECTFLOW</p><h2>{register ? 'Создать аккаунт' : 'С возвращением'}</h2>
      <p className="muted">{register ? 'Начните с вашего профиля.' : 'Войдите, чтобы продолжить работу.'}</p>
      <Alert error={error} />{error && <button onClick={() => void refresh()}>Проверить соединение</button>}
      {lastUser && <Notice>Черновик сохранён в этой вкладке. Войдите в тот же аккаунт, чтобы продолжить.</Notice>}
      <form onSubmit={submit}><fieldset disabled={command.busy}>
        {register ? <IdentityFields fields={fields} change={setFields} /> :
          <Field label="Email или логин" required value={identifier} maxLength={254} autoComplete="username"
            autoCapitalize="none" onChange={event => setIdentifier(event.target.value)} />}
        <Field label="Пароль" type="password" value={password} required minLength={register ? 12 : undefined}
          maxLength={128} autoComplete={register ? 'new-password' : 'current-password'}
          hint={register ? 'От 12 до 128 символов' : undefined} onChange={event => setPassword(event.target.value)} />
        <Alert error={command.error} /><Notice>{command.notice}</Notice>
        <button className="primary wide" type="submit">{command.busy ? 'Подождите…' : register ? 'Создать аккаунт' : 'Войти'}</button>
      </fieldset></form>
      <p className="auth-switch">{register ? 'Уже есть аккаунт?' : 'Впервые здесь?'} <button className="text-button" disabled={command.busy}
        onClick={() => { setRegister(!register); setPassword(''); command.clear(); command.setNotice(''); }}>
        {register ? 'Войти' : 'Зарегистрироваться'}</button></p>
    </section>
  </main>;
}

export function Profile({ user }: { user: User }) {
  const { client, refresh } = useSession();
  const [base, setBase] = useState(user);
  const [fields, setFields] = useState<ProfileFields>(fieldsOf(user));
  const [latest, setLatest] = useState<User>();
  const command = useCommand();
  function save(event: FormEvent) {
    event.preventDefault();
    void command.run(async () => {
      const saved = await client.request<User>(user.id, '/api/users/me', 'PUT', { ...fields, version: base.version });
      setBase(saved); setFields(fieldsOf(saved)); setLatest(undefined); command.setNotice('Профиль сохранён.'); await refresh();
    });
  }
  return <><PageHeader eyebrow="ВАШ АККАУНТ" title="Профиль" />
    <div className="details-grid"><section className="card"><h2>Личные данные</h2><form onSubmit={save}>
      <fieldset disabled={command.busy}><IdentityFields fields={fields} change={setFields} />
      <Alert error={command.error} /><Notice>{command.notice}</Notice>
      {command.error instanceof ApiError && command.error.code === 'VERSION_CONFLICT' &&
        <button type="button" onClick={() => void command.run(async () => {
          setLatest(await client.request<User>(user.id, '/api/users/me'));
          command.setNotice('Актуальная версия загружена для сравнения. Ваши поля не изменены.');
        })}>Показать актуальную версию</button>}
      <button className="primary" type="submit">{command.busy ? 'Сохранение…' : 'Сохранить профиль'}</button>
      </fieldset></form>
      {latest && <div className="comparison"><h3>Актуальный профиль</h3><p>{latest.firstName} {latest.lastName} · {latest.username} · {latest.email}</p>
        <button onClick={() => { setBase(latest); setFields(fieldsOf(latest)); setLatest(undefined); command.clear(); }}>
          Редактировать актуальную версию</button></div>}
    </section><aside className="card"><p className="eyebrow">ДОСТУП</p><h2>ID {String(user.id)}</h2>
      <p>Передайте этот ID владельцу проекта, чтобы он добавил вас в команду.</p>
      <dl><dt>Роль</dt><dd>{user.role === 'ADMIN' ? 'Администратор' : 'Пользователь'}</dd></dl>
      <Password user={user} /></aside></div>
    <MegaAccount key={String(user.id)} user={user} /></>;
}

function Password({ user }: { user: User }) {
  const { client } = useSession();
  const [currentPassword, setCurrent] = useState('');
  const [newPassword, setNew] = useState('');
  const command = useCommand();
  function submit(event: FormEvent) {
    event.preventDefault();
    void command.run(async () => { await client.request<void>(user.id, '/api/users/me/password', 'POST', { currentPassword, newPassword }); });
  }
  return <form className="password-section" onSubmit={submit}><h3>Сменить пароль</h3>
    <p className="muted">После смены нужно будет войти заново на всех устройствах.</p>
    <fieldset disabled={command.busy}>
      <Field label="Текущий пароль" type="password" required maxLength={128} autoComplete="current-password"
        value={currentPassword} onChange={event => setCurrent(event.target.value)} />
      <Field label="Новый пароль" type="password" required minLength={12} maxLength={128} autoComplete="new-password"
        value={newPassword} onChange={event => setNew(event.target.value)} />
      <Alert error={command.error} /><button type="submit">Сменить пароль</button>
    </fieldset></form>;
}

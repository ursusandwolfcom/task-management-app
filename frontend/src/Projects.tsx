import { useState } from 'react';
import type { FormEvent } from 'react';
import { ApiError } from './client.ts';
import { hashRoute, taskQueryFromParams } from './hashRoute.ts';
import { useCommand, useLoad } from './hooks.ts';
import { userId } from './json.ts';
import { useSession } from './session.tsx';
import { ProjectSchedule, ProjectTasks } from './Tasks.tsx';
import { Alert, Empty, Field, Loading, Notice, PageHeader, Pagination } from './ui.tsx';
import type { Member, Page, Project, ProjectFields, User } from './types.ts';

const statuses: Record<string, string> = { INITIATED: 'Новый', IN_PROGRESS: 'В работе', COMPLETED: 'Завершён',
  FAILED: 'Ошибка', CANCELLED: 'Отменён' };
function fieldsOf(project: ProjectFields): ProjectFields {
  const { name, description, startDate, endDate, timezone } = project;
  return { name, description, startDate, endDate, timezone };
}
function date(value: string) { return /^\d{4}-\d{2}-\d{2}$/.test(value) ? value.split('-').reverse().join('.') : value; }

export function ProjectFieldsForm({ fields, change }: { fields: ProjectFields; change: (value: ProjectFields) => void }) {
  return <>
    <Field label="Название проекта" required maxLength={200} value={fields.name}
      onChange={event => change({ ...fields, name: event.target.value })} />
    <label className="field"><span>Описание</span><textarea rows={4} maxLength={10000} value={fields.description}
      onChange={event => change({ ...fields, description: event.target.value })} /></label>
    <div className="form-grid"><Field label="Дата начала" required placeholder="2026-10-14"
      hint="Дата в формате ГГГГ-ММ-ДД" value={fields.startDate}
      onChange={event => change({ ...fields, startDate: event.target.value })} />
    <Field label="Срок проекта" required placeholder="2026-10-31" value={fields.endDate}
      hint="Не раньше даты начала" onChange={event => change({ ...fields, endDate: event.target.value })} /></div>
    <Field label="Часовой пояс" required maxLength={64} value={fields.timezone} placeholder="Europe/Kyiv"
      hint="Например, UTC или Europe/Kyiv" onChange={event => change({ ...fields, timezone: event.target.value })} />
  </>;
}

export function ProjectList({ user }: { user: User }) {
  const { client, generation } = useSession();
  const [page, setPage] = useState(0);
  const query = useLoad(`${user.id}:${generation}:${page}`, signal =>
    client.request<Page<Project>>(user.id, `/api/projects?page=${page}&size=12`, 'GET', undefined, signal));
  return <><PageHeader eyebrow="РАБОЧЕЕ ПРОСТРАНСТВО" title="Проекты">
    <a className="button primary" href="#/projects/new">＋ Новый проект</a></PageHeader>
    <div className="section-toolbar"><p className="muted">{user.role === 'ADMIN' ? 'Все проекты' : 'Ваши проекты и проекты команды'}</p>
      <button disabled={query.loading} onClick={query.reload}>Обновить список</button></div>
    <Alert error={query.error} />{query.loading && <Loading />}
    {query.value && <>
      <p className="count-label">ПРОЕКТОВ: {String(query.value.totalElements)}</p>
      {query.value.content.length === 0 ? <Empty title="Пока нет проектов">
        Создайте первый проект или передайте свой ID владельцу команды, чтобы получить доступ.
      </Empty> : <div className="project-grid">{query.value.content.map(project =>
        <a href={`#/projects/${project.id}`} className="project-card card" key={String(project.id)}>
          <div className="card-top"><span className="project-symbol" aria-hidden="true">◇</span>
            <span className="badge">{statuses[project.status] ?? project.status}</span></div>
          <h2>{project.name}</h2><p className="project-description">{project.description || 'Описание ещё не добавлено'}</p>
          <div className="project-footer"><span>{date(project.startDate)} — {date(project.endDate)}</span><span aria-hidden="true">↗</span></div>
          <small>{project.ownerId === user.id ? 'Вы — владелец' : `Владелец · ID ${project.ownerId}`}</small>
        </a>)}</div>}
      <Pagination page={page} total={query.value.totalPages} onChange={setPage} />
    </>}
  </>;
}

export function CreateProject({ user }: { user: User }) {
  const { client } = useSession();
  const [fields, setFields] = useState<ProjectFields>({ name: '', description: '',
    startDate: new Date().toISOString().slice(0, 10), endDate: new Date().toISOString().slice(0, 10), timezone: 'UTC' });
  const command = useCommand();
  function submit(event: FormEvent) {
    event.preventDefault();
    void command.run(async () => {
      const project = await client.request<Project>(user.id, '/api/projects', 'POST', fields);
      window.location.hash = `/projects/${project.id}`;
    });
  }
  return <><PageHeader eyebrow="НАЧАЛО РАБОТЫ" title="Новый проект"><a href="#/projects">К проектам</a></PageHeader>
    <div className="details-grid"><section className="card"><h2>О проекте</h2><form onSubmit={submit}>
      <fieldset disabled={command.busy}><ProjectFieldsForm fields={fields} change={setFields} />
        <Alert error={command.error} /><button className="primary" type="submit">{command.busy ? 'Создание…' : 'Создать проект'}</button>
      </fieldset></form></section><aside className="card guide"><span className="project-symbol" aria-hidden="true">◇</span>
      <h2>Начните с контекста</h2><p>Дайте проекту понятное название и опишите ожидаемый результат.</p>
      <p className="muted">Вы станете владельцем. После создания сможете добавить участников и передать владение.</p>
    </aside></div></>;
}

export function ProjectDetail({ user, id }: { user: User; id: bigint }) {
  const { client, generation } = useSession();
  const query = useLoad(`${user.id}:${generation}:${id}`, signal => client.request<Project>(user.id, `/api/projects/${id}`, 'GET', undefined, signal));
  const command = useCommand();
  const project = query.value;
  const manage = project && (user.role === 'ADMIN' || project.ownerId === user.id);
  return <><PageHeader eyebrow="ПРОЕКТ" title={project?.name ?? 'Проект'}><a href="#/projects">← К проектам</a></PageHeader>
    <Alert error={query.error} />{query.loading && <Loading />}
    {query.error && !query.loading && <button onClick={query.reload}>Повторить загрузку</button>}
    {project && <>
      <div className="project-overview"><span className="badge">{statuses[project.status] ?? project.status}</span>
        <span>{date(project.startDate)} — {date(project.endDate)}</span><span>{project.timezone}</span>
        <span>Владелец · ID {String(project.ownerId)}</span>
        <button disabled={query.loading} onClick={query.reload}>Обновить проект</button></div>
      <div className="details-grid"><section className="card">
        {manage ? <ProjectEditor key={String(id)} user={user} initial={project} onSaved={query.reload} /> :
          <><h2>О проекте</h2><p className="description-full">{project.description || 'Описание ещё не добавлено.'}</p>
            <p className="muted">Управлять данными и составом команды может владелец проекта.</p></>}
      </section><Members key={String(id)} user={user} project={project} manage={Boolean(manage)}
        updating={query.loading} refreshProject={query.reload} /></div>
      <ProjectTasks key={`tasks-${project.inputRevision}-${hashRoute().params.toString()}`}
        user={user} project={project} manage={Boolean(manage)}
        initialFilters={taskQueryFromParams(hashRoute().params)} />
      <ProjectHolidays user={user} project={project} manage={Boolean(manage)} onChanged={query.reload} />
      <ProjectSchedule key={`gantt-${project.inputRevision}-${project.calendarRevision}`} user={user} project={project} />
      {manage && <section className="danger-section"><div><h2>Удалить проект</h2><p className="muted">Удаление доступно только для пустого проекта.</p></div>
        <button className="danger" disabled={command.busy} onClick={() => {
          if (!window.confirm(`Удалить проект «${project.name}»? Это действие нельзя отменить.`)) return;
          void command.run(async () => { await client.request<void>(user.id, `/api/projects/${id}?version=${project.version}`, 'DELETE');
            window.location.hash = '/projects'; });
        }}>Удалить проект</button><Alert error={command.error} /></section>}
    </>}
  </>;
}

function ProjectEditor({ user, initial, onSaved }: { user: User; initial: Project; onSaved: () => void }) {
  const { client } = useSession();
  const [base, setBase] = useState(initial);
  const [fields, setFields] = useState(fieldsOf(initial));
  const [latest, setLatest] = useState<Project>();
  const command = useCommand();
  function submit(event: FormEvent) {
    event.preventDefault();
    void command.run(async () => {
      const saved = await client.request<Project>(user.id, `/api/projects/${initial.id}`, 'PUT', { ...fields, version: base.version });
      setBase(saved); setFields(fieldsOf(saved)); setLatest(undefined); command.setNotice('Проект сохранён.'); onSaved();
    });
  }
  return <><h2>Данные проекта</h2><form onSubmit={submit}><fieldset disabled={command.busy}>
    <ProjectFieldsForm fields={fields} change={setFields} /><Alert error={command.error} /><Notice>{command.notice}</Notice>
    {command.error instanceof ApiError && command.error.code === 'VERSION_CONFLICT' &&
      <button type="button" onClick={() => void command.run(async () => {
        setLatest(await client.request<Project>(user.id, `/api/projects/${initial.id}`));
        command.setNotice('Актуальная версия загружена для сравнения. Ваши поля не изменены.');
      })}>Показать актуальную версию</button>}
    <button className="primary" type="submit">{command.busy ? 'Сохранение…' : 'Сохранить проект'}</button>
  </fieldset></form>{latest && <div className="comparison"><h3>Актуальные данные</h3>
    <p>{latest.name}</p><p className="description-full">{latest.description}</p><p>{latest.startDate} — {latest.endDate} · {latest.timezone}</p>
    <button onClick={() => { setBase(latest); setFields(fieldsOf(latest)); setLatest(undefined); command.clear(); }}>
      Редактировать актуальную версию</button></div>}</>;
}

function ProjectHolidays({ user, project, manage, onChanged }: {
  user: User; project: Project; manage: boolean; onChanged: () => void
}) {
  const { client, generation } = useSession();
  const [draft, setDraft] = useState('');
  const command = useCommand();
  const query = useLoad(`${user.id}:${generation}:${project.id}:holidays:${project.calendarRevision}`, signal =>
    client.request<string[]>(user.id, `/api/projects/${project.id}/holidays`, 'GET', undefined, signal));
  const dates = query.value ?? [];
  return <section className="card"><h2>Праздничные дни</h2>
    <p className="muted">Нерабочие будни для CPM/Gantt (Пн–Пт 09–17, обед 13–14 фиксирован).</p>
    <Alert error={query.error || command.error} />{query.loading && <Loading />}
    <Notice>{command.notice}</Notice>
    <ul className="member-list">{dates.map(day => <li key={day}>
      <strong>{date(day)}</strong>
      {manage && <button className="text-button danger-text" disabled={command.busy} onClick={() =>
        void command.run(async () => {
          const next = dates.filter(item => item !== day);
          await client.request<Project>(user.id, `/api/projects/${project.id}/holidays`, 'PUT',
            { dates: next, version: project.version });
          command.setNotice('Праздник удалён.'); onChanged(); query.reload();
        })}>Удалить</button>}
    </li>)}</ul>
    {dates.length === 0 && !query.loading && <Empty title="Праздников нет">Календарь использует только выходные.</Empty>}
    {manage && <form className="team-form" onSubmit={event => { event.preventDefault();
      void command.run(async () => {
        if (!/^\d{4}-\d{2}-\d{2}$/.test(draft)) throw new ApiError(400, 'VALIDATION_ERROR');
        const next = Array.from(new Set([...dates, draft])).sort();
        await client.request<Project>(user.id, `/api/projects/${project.id}/holidays`, 'PUT',
          { dates: next, version: project.version });
        setDraft(''); command.setNotice('Праздник добавлен.'); onChanged(); query.reload();
      });
    }}><fieldset disabled={command.busy}>
      <Field label="Дата (ГГГГ-ММ-ДД)" required placeholder="2026-11-04" value={draft}
        onChange={event => setDraft(event.target.value)} />
      <button type="submit">Добавить праздник</button>
    </fieldset></form>}
  </section>;
}

function Members({ user, project, manage, updating, refreshProject }: {
  user: User; project: Project; manage: boolean; updating: boolean; refreshProject: () => void
}) {
  const { client, generation } = useSession();
  const [page, setPage] = useState(0);
  const [candidate, setCandidate] = useState('');
  const [owner, setOwner] = useState('');
  const query = useLoad(`${user.id}:${generation}:${project.version}:${page}`, signal =>
    client.request<Page<Member>>(user.id, `/api/projects/${project.id}/members?page=${page}&size=20`, 'GET', undefined, signal));
  const command = useCommand();
  function add(event: FormEvent) {
    event.preventDefault();
    void command.run(async () => { const id = userId(candidate);
      await client.request<Project>(user.id, `/api/projects/${project.id}/members/${id}`, 'PUT', { version: project.version });
      setCandidate(''); command.setNotice('Участник добавлен.'); refreshProject(); query.reload(); });
  }
  function transfer(event: FormEvent) {
    event.preventDefault();
    void command.run(async () => { const id = userId(owner);
      if (!window.confirm(`Передать владение участнику ID ${id}? Вы останетесь участником.`)) return;
      await client.request<Project>(user.id, `/api/projects/${project.id}/owner`, 'PUT', { userId: id, version: project.version });
      setOwner(''); command.setNotice('Владение передано.'); refreshProject(); query.reload(); });
  }
  return <aside className="card"><div className="section-toolbar"><h2>Команда</h2><span className="badge">{String(query.value?.totalElements ?? 0n)}</span></div>
    <Alert error={query.error} />{query.loading && <Loading />}
    <ul className="member-list">{query.value?.content.map(member => <li key={String(member.userId)}>
      <span className="avatar" aria-hidden="true">{String(member.userId).slice(-2)}</span>
      <div><strong>ID {String(member.userId)}</strong><small>{member.owner ? 'Владелец' : 'Участник'}{member.userId === user.id ? ' · Вы' : ''}</small></div>
      {manage && !member.owner && <button className="text-button danger-text" disabled={command.busy || updating}
        aria-label={`Удалить участника ${member.userId}`} onClick={() => {
          if (!window.confirm(`Удалить участника ID ${member.userId} из проекта?`)) return;
          void command.run(async () => { await client.request<Project>(user.id,
            `/api/projects/${project.id}/members/${member.userId}?version=${project.version}`, 'DELETE');
            refreshProject(); query.reload(); command.setNotice('Участник удалён.'); });
        }}>Удалить</button>}
    </li>)}</ul>
    <Pagination page={page} total={query.value?.totalPages ?? 0} onChange={setPage} />
    <Alert error={command.error} /><Notice>{command.notice}</Notice>
    {manage && <><form className="team-form" onSubmit={add}><h3>Добавить участника</h3><fieldset disabled={command.busy || updating}>
      <Field label="ID пользователя" inputMode="numeric" required value={candidate}
        hint="Пользователь найдёт свой ID в профиле." onChange={event => setCandidate(event.target.value)} />
      <button type="submit">Добавить участника</button></fieldset></form>
      <form className="team-form" onSubmit={transfer}><h3>Передать владение</h3><fieldset disabled={command.busy || updating}>
        <Field label="ID нового владельца" inputMode="numeric" required value={owner}
          hint="Выберите существующего участника проекта." onChange={event => setOwner(event.target.value)} />
        <button type="submit">Передать владение</button></fieldset></form></>}
  </aside>;
}

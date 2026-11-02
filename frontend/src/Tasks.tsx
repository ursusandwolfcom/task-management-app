import { useState } from 'react';
import type { FormEvent } from 'react';
import { ApiError } from './client.ts';
import { useCommand, useLoad } from './hooks.ts';
import { userId } from './json.ts';
import { useSession } from './session.tsx';
import { emptyTaskQuery, taskQueryString } from './taskQuery.ts';
import { Alert, Empty, Field, Loading, Notice, PageHeader, Pagination } from './ui.tsx';
import type {
  AvailableTasks, Comment, ExecutionAttempt, Label, Page, Predecessors, Project,
  Task, TaskBucket, TaskFields, TaskPriority, TaskQuery, User
} from './types.ts';

const statuses: Record<string, string> = {
  NOT_STARTED: 'Не начата', IN_PROGRESS: 'В работе', COMPLETED: 'Завершена',
  FAILED: 'Ошибка', CANCELLED: 'Отменена'
};
const buckets: Record<TaskBucket, string> = {
  INBOX: 'Inbox', INCUBATOR: 'Инкубатор', WORK: 'Work', ARCHIVE: 'Архив'
};
const priorities: TaskPriority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
const emptyFields = (): TaskFields =>
  ({ name: '', description: '', priority: 'MEDIUM', bucket: 'INBOX',
    estimatedMinutes: '', dueDate: '', assigneeId: '' });

function fieldsOf(task: Task): TaskFields {
  return {
    name: task.name, description: task.description, priority: task.priority, bucket: task.bucket,
    estimatedMinutes: task.estimatedMinutes == null ? '' : String(task.estimatedMinutes),
    dueDate: task.dueDate ?? '', assigneeId: task.assigneeId == null ? '' : String(task.assigneeId)
  };
}

function bodyOf(fields: TaskFields) {
  return {
    name: fields.name, description: fields.description, priority: fields.priority, bucket: fields.bucket,
    estimatedMinutes: fields.estimatedMinutes === '' ? null : Number(fields.estimatedMinutes),
    dueDate: fields.dueDate === '' ? null : fields.dueDate,
    assigneeId: fields.assigneeId === '' ? null : userId(fields.assigneeId)
  };
}

function BucketTabs({ value, change }: { value: string; change: (bucket: string) => void }) {
  return <div className="bucket-tabs" role="tablist" aria-label="Боксы задач">
    <button type="button" role="tab" aria-selected={value === ''} className={value === '' ? 'active' : undefined}
      onClick={() => change('')}>Все</button>
    {(Object.keys(buckets) as TaskBucket[]).map(id =>
      <button type="button" role="tab" key={id} aria-selected={value === id}
        className={value === id ? 'active' : undefined} onClick={() => change(id)}>{buckets[id]}</button>)}
  </div>;
}

function TaskFilters({ value, change, showAssignee }: {
  value: TaskQuery; change: (next: TaskQuery) => void; showAssignee?: boolean
}) {
  return <div className="filter-bar">
    <label className="field compact"><span>Статус</span>
      <select value={value.status} onChange={event => change({ ...value, status: event.target.value })}>
        <option value="">Все</option>
        {Object.entries(statuses).map(([id, name]) => <option key={id} value={id}>{name}</option>)}
      </select></label>
    {showAssignee !== false && <Field label="Исполнитель ID" inputMode="numeric" value={value.assigneeId}
      onChange={event => change({ ...value, assigneeId: event.target.value })} />}
    <Field label="Срок с" type="date" value={value.dueFrom}
      onChange={event => change({ ...value, dueFrom: event.target.value })} />
    <Field label="Срок по" type="date" value={value.dueTo}
      onChange={event => change({ ...value, dueTo: event.target.value })} />
    <Field label="Метка ID" inputMode="numeric" value={value.labelId}
      onChange={event => change({ ...value, labelId: event.target.value })} />
  </div>;
}

function TaskFieldsForm({ fields, change }: { fields: TaskFields; change: (value: TaskFields) => void }) {
  return <>
    <Field label="Название" required maxLength={200} value={fields.name}
      onChange={event => change({ ...fields, name: event.target.value })} />
    <label className="field"><span>Описание</span><textarea rows={3} maxLength={10000} value={fields.description}
      onChange={event => change({ ...fields, description: event.target.value })} /></label>
    <div className="form-grid">
      <label className="field"><span>Приоритет</span>
        <select value={fields.priority} onChange={event => change({ ...fields, priority: event.target.value as TaskPriority })}>
          {priorities.map(item => <option key={item} value={item}>{item}</option>)}
        </select></label>
      <label className="field"><span>Бокс</span>
        <select value={fields.bucket} onChange={event => change({ ...fields, bucket: event.target.value as TaskBucket })}>
          {(Object.keys(buckets) as TaskBucket[]).map(item =>
            <option key={item} value={item}>{buckets[item]}</option>)}
        </select></label>
      <Field label="Оценка (мин)" inputMode="numeric" placeholder="15–525600" value={fields.estimatedMinutes}
        onChange={event => change({ ...fields, estimatedMinutes: event.target.value })} />
      <Field label="Срок" type="date" value={fields.dueDate}
        onChange={event => change({ ...fields, dueDate: event.target.value })} />
      <Field label="Исполнитель ID" inputMode="numeric" value={fields.assigneeId}
        hint="Участник проекта" onChange={event => change({ ...fields, assigneeId: event.target.value })} />
    </div>
  </>;
}

export function MyTasks({ user }: { user: User }) {
  const { client, generation } = useSession();
  const [page, setPage] = useState(0);
  const [draft, setDraft] = useState(emptyTaskQuery);
  const [applied, setApplied] = useState(emptyTaskQuery);
  const query = useLoad(`${user.id}:${generation}:${page}:${JSON.stringify(applied)}`, signal =>
    client.request<Page<Task>>(user.id, `/api/tasks/mine?${taskQueryString({ page, size: 20 }, {
      ...applied, assigneeId: ''
    })}`, 'GET', undefined, signal));
  return <><PageHeader eyebrow="ОЧЕРЕДЬ" title="Мои задачи" />
    <BucketTabs value={draft.bucket} change={bucket => { setDraft({ ...draft, bucket }); setPage(0); setApplied({ ...draft, bucket }); }} />
    <form className="card filter-card" onSubmit={event => { event.preventDefault(); setPage(0); setApplied(draft); }}>
      <TaskFilters value={draft} change={setDraft} showAssignee={false} />
      <button type="submit">Применить фильтры</button>
    </form>
    <Alert error={query.error} />{query.loading && <Loading />}
    {query.value && <>
      <p className="count-label">НАЗНАЧЕНО ВАМ: {String(query.value.totalElements)}</p>
      {query.value.content.length === 0 ? <Empty title="Нет назначенных задач">
        Когда вас назначат исполнителем, задачи появятся здесь.
      </Empty> : <ul className="task-list">{query.value.content.map(task =>
        <li key={String(task.id)}><a href={`#/tasks/${task.id}`}>
          <strong>{task.name}</strong>
          <span className="badge">{buckets[task.bucket]}</span>
          <span className="badge">{statuses[task.status]}</span>
          <small>Проект {String(task.projectId)} · {task.priority}
            {task.dueDate ? ` · до ${task.dueDate}` : ''}</small>
        </a></li>)}</ul>}
      <Pagination page={page} total={query.value.totalPages} onChange={setPage} />
    </>}
  </>;
}

export function ProjectTasks({ user, project, manage }: { user: User; project: Project; manage: boolean }) {
  const { client, generation } = useSession();
  const [page, setPage] = useState(0);
  const [draft, setDraft] = useState(emptyTaskQuery);
  const [applied, setApplied] = useState(emptyTaskQuery);
  const [fields, setFields] = useState(emptyFields);
  const command = useCommand();
  const list = useLoad(`${user.id}:${generation}:${project.id}:${project.inputRevision}:${page}:${JSON.stringify(applied)}`,
    signal => client.request<Page<Task>>(user.id,
      `/api/projects/${project.id}/tasks?${taskQueryString({ page, size: 20 }, applied)}`,
      'GET', undefined, signal));
  const queue = useLoad(`${user.id}:${generation}:${project.id}:${project.inputRevision}:queue`, signal =>
    client.request<AvailableTasks>(user.id, `/api/projects/${project.id}/available-tasks`, 'GET', undefined, signal));
  function create(event: FormEvent) {
    event.preventDefault();
    void command.run(async () => {
      const created = await client.request<Task>(user.id, `/api/projects/${project.id}/tasks`, 'POST', bodyOf(fields));
      setFields(emptyFields()); command.setNotice('Задача создана.');
      window.location.hash = `/tasks/${created.id}`;
    });
  }
  return <section className="card tasks-panel">
    <div className="section-toolbar"><h2>Задачи</h2>
      <button disabled={list.loading} onClick={() => { list.reload(); queue.reload(); }}>Обновить</button></div>
    <BucketTabs value={draft.bucket} change={bucket => {
      const next = { ...draft, bucket }; setDraft(next); setPage(0); setApplied(next);
    }} />
    <p className="muted">Очередь и старт доступны только для бокса Work. Inbox / Инкубатор / Архив — свободные переходы.</p>
    {queue.value && <div className="queue-box">
      <p className="count-label">WORK · ДОСТУПНЫ К СТАРТУ: {String(queue.value.tasks.length)}</p>
      <ul className="task-list compact">{queue.value.tasks.map(task =>
        <li key={String(task.id)}><a href={`#/tasks/${task.id}`}>{task.name}
          <span className="badge">{task.priority}</span></a></li>)}
      </ul>
      {queue.value.blockingReasons.length > 0 && <>
        <p className="count-label">ЗАБЛОКИРОВАНЫ</p>
        <ul className="task-list compact">{queue.value.blockingReasons.map(item =>
          <li key={String(item.taskId)}><a href={`#/tasks/${item.taskId}`}>Задача {String(item.taskId)}</a>
            <small>Ждёт: {item.predecessorIds.map(String).join(', ') || '—'}</small></li>)}</ul>
      </>}
    </div>}
    <form className="filter-card nested" onSubmit={event => { event.preventDefault(); setPage(0); setApplied(draft); }}>
      <TaskFilters value={draft} change={setDraft} />
      <button type="submit">Фильтровать</button>
    </form>
    <Alert error={list.error} />{list.loading && <Loading />}
    {list.value && <>
      <p className="count-label">ВСЕГО: {String(list.value.totalElements)}</p>
      {list.value.content.length === 0 ? <Empty title="В проекте пока нет задач">
        Создайте первую задачу ниже.
      </Empty> : <ul className="task-list">{list.value.content.map(task =>
        <li key={String(task.id)}><a href={`#/tasks/${task.id}`}>
          <strong>{task.name}</strong>
          <span className="badge">{buckets[task.bucket]}</span>
          <span className="badge">{statuses[task.status]}</span>
          <small>{task.priority}{task.assigneeId ? ` · исп. ${task.assigneeId}` : ''}
            {task.dueDate ? ` · до ${task.dueDate}` : ''}</small>
        </a></li>)}</ul>}
      <Pagination page={page} total={list.value.totalPages} onChange={setPage} />
    </>}
    <form className="team-form" onSubmit={create}><h3>Новая задача</h3>
      <fieldset disabled={command.busy}><TaskFieldsForm fields={fields} change={setFields} />
        <Alert error={command.error} /><Notice>{command.notice}</Notice>
        <button className="primary" type="submit">{command.busy ? 'Создание…' : 'Создать задачу'}</button>
      </fieldset></form>
    {manage && <p className="muted">Метки проекта и комментарии доступны в карточке задачи.</p>}
  </section>;
}

export function TaskDetail({ user, id }: { user: User; id: bigint }) {
  const { client, generation } = useSession();
  const query = useLoad(`${user.id}:${generation}:${id}`, signal =>
    client.request<Task>(user.id, `/api/tasks/${id}`, 'GET', undefined, signal));
  return <><PageHeader eyebrow="ЗАДАЧА" title={query.value?.name ?? 'Задача'}>
    {query.value && <a href={`#/projects/${query.value.projectId}`}>← К проекту</a>}
  </PageHeader>
    <Alert error={query.error} />{query.loading && <Loading />}
    {query.error && !query.loading && <button onClick={query.reload}>Повторить</button>}
    {query.value && <TaskWorkspace key={String(query.value.version)} user={user} task={query.value}
      onReload={query.reload} />}
  </>;
}

function TaskWorkspace({ user, task, onReload }: { user: User; task: Task; onReload: () => void }) {
  const { client, generation } = useSession();
  const project = useLoad(`${user.id}:${generation}:p:${task.projectId}`, signal =>
    client.request<Project>(user.id, `/api/projects/${task.projectId}`, 'GET', undefined, signal));
  const manage = Boolean(project.value && (user.role === 'ADMIN' || project.value.ownerId === user.id));
  const canExecute = Boolean(project.value && (manage || task.assigneeId === user.id));
  return <>
    <Alert error={project.error} />{project.loading && <Loading />}
    {project.value && <>
      <div className="project-overview">
        <span className="badge">{buckets[task.bucket]}</span>
        <span className="badge">{statuses[task.status]}</span>
        <span>{task.priority}</span>
        <span>Версия {String(task.version)}</span>
        <span>Проект · ID {String(task.projectId)}</span>
        <button disabled={project.loading} onClick={() => { onReload(); project.reload(); }}>Обновить</button>
      </div>
      <div className="details-grid">
        <TaskEditor user={user} task={task} onSaved={onReload} />
        <aside className="card">
          <Lifecycle user={user} task={task} canExecute={canExecute} manage={manage} onDone={onReload} />
          <Dependencies user={user} task={task} project={project.value} onChanged={() => {
            onReload(); project.reload();
          }} />
        </aside>
      </div>
      <div className="details-grid">
        <Attempts user={user} taskId={task.id} />
        <Comments user={user} taskId={task.id} />
      </div>
      <LabelsPanel user={user} task={task} />
    </>}
  </>;
}

function TaskEditor({ user, task, onSaved }: { user: User; task: Task; onSaved: () => void }) {
  const { client } = useSession();
  const [base, setBase] = useState(task);
  const [fields, setFields] = useState(fieldsOf(task));
  const command = useCommand();
  function submit(event: FormEvent) {
    event.preventDefault();
    void command.run(async () => {
      const saved = await client.request<Task>(user.id, `/api/tasks/${task.id}`, 'PUT',
        { ...bodyOf(fields), version: base.version });
      setBase(saved); setFields(fieldsOf(saved)); command.setNotice('Задача сохранена.'); onSaved();
    });
  }
  return <section className="card"><h2>Данные задачи</h2>
    <form onSubmit={submit}><fieldset disabled={command.busy}>
      <TaskFieldsForm fields={fields} change={setFields} />
      <Alert error={command.error} /><Notice>{command.notice}</Notice>
      {command.error instanceof ApiError && command.error.code === 'VERSION_CONFLICT' &&
        <button type="button" onClick={() => void command.run(async () => {
          const latest = await client.request<Task>(user.id, `/api/tasks/${task.id}`);
          setBase(latest); setFields(fieldsOf(latest)); command.setNotice('Загружена актуальная версия.');
        })}>Загрузить актуальную версию</button>}
      <button className="primary" type="submit">{command.busy ? 'Сохранение…' : 'Сохранить'}</button>
    </fieldset></form></section>;
}

function Lifecycle({ user, task, canExecute, manage, onDone }: {
  user: User; task: Task; canExecute: boolean; manage: boolean; onDone: () => void
}) {
  const { client } = useSession();
  const command = useCommand();
  const actions: { path: string; label: string; need: boolean }[] = [
    { path: 'start', label: 'Старт', need: canExecute && task.status === 'NOT_STARTED' },
    { path: 'complete', label: 'Завершить', need: canExecute && task.status === 'IN_PROGRESS' },
    { path: 'fail', label: 'Ошибка', need: canExecute && task.status === 'IN_PROGRESS' },
    { path: 'retry', label: 'Повтор', need: canExecute && task.status === 'FAILED' },
    { path: 'cancel', label: 'Отменить', need: manage && (task.status === 'NOT_STARTED'
      || task.status === 'IN_PROGRESS' || task.status === 'FAILED') }
  ];
  return <div className="lifecycle"><h2>Действия</h2>
    <div className="action-row">{actions.filter(item => item.need).map(item =>
      <button key={item.path} disabled={command.busy} onClick={() => void command.run(async () => {
        await client.request<Task>(user.id, `/api/tasks/${task.id}/${item.path}`, 'POST', { version: task.version });
        command.setNotice(`Выполнено: ${item.label}`); onDone();
      })}>{item.label}</button>)}
    </div>
    {manage && (task.status === 'NOT_STARTED' || task.status === 'CANCELLED') && task.attemptCount === 0 &&
      <button className="danger-text text-button" disabled={command.busy} onClick={() => {
        if (!window.confirm('Удалить задачу без истории?')) return;
        void command.run(async () => {
          await client.request<void>(user.id, `/api/tasks/${task.id}?version=${task.version}`, 'DELETE');
          window.location.hash = `/projects/${task.projectId}`;
        });
      }}>Удалить задачу</button>}
    <Alert error={command.error} /><Notice>{command.notice}</Notice>
  </div>;
}

function Dependencies({ user, task, project, onChanged }: {
  user: User; task: Task; project: Project; onChanged: () => void
}) {
  const { client, generation } = useSession();
  const [predecessor, setPredecessor] = useState('');
  const command = useCommand();
  const deps = useLoad(`${user.id}:${generation}:${task.id}:${project.inputRevision}:deps`, signal =>
    client.request<Predecessors>(user.id, `/api/tasks/${task.id}/dependencies`, 'GET', undefined, signal));
  const editable = task.status === 'NOT_STARTED' && task.attemptCount === 0;
  return <div className="team-form"><h3>Зависимости</h3>
    <Alert error={deps.error} />{deps.loading && <Loading />}
    <ul className="member-list">{deps.value?.dependsOnTaskIds.map(item => <li key={String(item)}>
      <a href={`#/tasks/${item}`}>← {String(item)}</a>
      {editable && <button className="text-button danger-text" disabled={command.busy} onClick={() =>
        void command.run(async () => {
          await client.request<void>(user.id,
            `/api/tasks/${task.id}/dependencies/${item}?inputRevision=${deps.value?.inputRevision ?? project.inputRevision}`,
            'DELETE');
          command.setNotice('Зависимость удалена.'); deps.reload(); onChanged();
        })}>Удалить</button>}
    </li>)}</ul>
    {editable && <form onSubmit={event => { event.preventDefault();
      void command.run(async () => {
        await client.request(user.id, `/api/tasks/${task.id}/dependencies`, 'POST', {
          dependsOnTaskId: userId(predecessor),
          inputRevision: deps.value?.inputRevision ?? project.inputRevision
        });
        setPredecessor(''); command.setNotice('Зависимость добавлена.'); deps.reload(); onChanged();
      });
    }}><fieldset disabled={command.busy}>
      <Field label="ID предшественника" inputMode="numeric" required value={predecessor}
        onChange={event => setPredecessor(event.target.value)} />
      <button type="submit">Добавить зависимость</button>
    </fieldset></form>}
    <Alert error={command.error} /><Notice>{command.notice}</Notice>
    <small>Ревизия графа: {String(deps.value?.inputRevision ?? project.inputRevision)}</small>
  </div>;
}

function Attempts({ user, taskId }: { user: User; taskId: bigint }) {
  const { client, generation } = useSession();
  const [page, setPage] = useState(0);
  const query = useLoad(`${user.id}:${generation}:${taskId}:attempts:${page}`, signal =>
    client.request<Page<ExecutionAttempt>>(user.id,
      `/api/tasks/${taskId}/attempts?page=${page}&size=20`, 'GET', undefined, signal));
  return <section className="card"><h2>История выполнения</h2>
    <Alert error={query.error} />{query.loading && <Loading />}
    {query.value?.content.length === 0 && <Empty title="Попыток ещё не было">
      История появится после старта задачи.
    </Empty>}
    <ul className="attempt-list">{query.value?.content.map(item => <li key={item.number}>
      <strong>Попытка {item.number}</strong>
      <span>{item.outcome ? statuses[item.outcome] : 'В процессе'}</span>
      <small>{item.actualStart}{item.actualEnd ? ` → ${item.actualEnd}` : ''}</small>
    </li>)}</ul>
    <Pagination page={page} total={query.value?.totalPages ?? 0} onChange={setPage} />
  </section>;
}

function Comments({ user, taskId }: { user: User; taskId: bigint }) {
  const { client, generation } = useSession();
  const [page, setPage] = useState(0);
  const [text, setText] = useState('');
  const command = useCommand();
  const query = useLoad(`${user.id}:${generation}:${taskId}:comments:${page}`, signal =>
    client.request<Page<Comment>>(user.id, `/api/comments?taskId=${taskId}&page=${page}&size=20`,
      'GET', undefined, signal));
  return <section className="card"><h2>Комментарии</h2>
    <Alert error={query.error} />{query.loading && <Loading />}
    <ul className="attempt-list">{query.value?.content.map(item => <li key={String(item.id)}>
      <strong>Автор {String(item.authorId)}</strong><small>{item.createdAt}</small>
      <p className="description-full">{item.text}</p>
      {(item.authorId === user.id || user.role === 'ADMIN') &&
        <button className="text-button danger-text" disabled={command.busy} onClick={() =>
          void command.run(async () => {
            await client.request<void>(user.id, `/api/comments/${item.id}`, 'DELETE');
            query.reload();
          })}>Удалить</button>}
    </li>)}</ul>
    <Pagination page={page} total={query.value?.totalPages ?? 0} onChange={setPage} />
    <form className="team-form" onSubmit={event => { event.preventDefault();
      void command.run(async () => {
        await client.request(user.id, '/api/comments', 'POST', { taskId, text });
        setText(''); command.setNotice('Комментарий добавлен.'); query.reload();
      });
    }}><fieldset disabled={command.busy}>
      <label className="field"><span>Новый комментарий</span>
        <textarea required maxLength={4000} rows={3} value={text}
          onChange={event => setText(event.target.value)} /></label>
      <Alert error={command.error} /><Notice>{command.notice}</Notice>
      <button type="submit">Отправить</button>
    </fieldset></form>
  </section>;
}

function LabelsPanel({ user, task }: { user: User; task: Task }) {
  const { client, generation } = useSession();
  const [labelId, setLabelId] = useState('');
  const [name, setName] = useState('');
  const [color, setColor] = useState('#5B8DEF');
  const command = useCommand();
  const projectLabels = useLoad(`${user.id}:${generation}:labels:${task.projectId}`, signal =>
    client.request<Page<Label>>(user.id, `/api/labels?projectId=${task.projectId}&page=0&size=100`,
      'GET', undefined, signal));
  const linked = useLoad(`${user.id}:${generation}:task-labels:${task.id}`, signal =>
    client.request<Page<Label>>(user.id, `/api/tasks/${task.id}/labels?page=0&size=100`,
      'GET', undefined, signal));
  return <section className="card"><h2>Метки</h2>
    <Alert error={projectLabels.error || linked.error} />
    <div className="label-row">{linked.value?.content.map(item =>
      <button key={String(item.id)} className="badge label-chip" style={{ borderColor: item.color }}
        disabled={command.busy} onClick={() => void command.run(async () => {
          await client.request<void>(user.id, `/api/tasks/${task.id}/labels/${item.id}`, 'DELETE');
          linked.reload();
        })}>{item.name} ×</button>)}
    </div>
    <form className="team-form inline-form" onSubmit={event => { event.preventDefault();
      void command.run(async () => {
        await client.request<void>(user.id, `/api/tasks/${task.id}/labels/${userId(labelId)}`, 'PUT');
        setLabelId(''); linked.reload();
      });
    }}><fieldset disabled={command.busy}>
      <Field label="Привязать метку ID" inputMode="numeric" required value={labelId}
        onChange={event => setLabelId(event.target.value)} />
      <button type="submit">Привязать</button>
    </fieldset></form>
    <form className="team-form" onSubmit={event => { event.preventDefault();
      void command.run(async () => {
        const created = await client.request<Label>(user.id, '/api/labels', 'POST',
          { projectId: task.projectId, name, color });
        await client.request<void>(user.id, `/api/tasks/${task.id}/labels/${created.id}`, 'PUT');
        setName(''); projectLabels.reload(); linked.reload(); command.setNotice('Метка создана и привязана.');
      });
    }}><h3>Новая метка проекта</h3><fieldset disabled={command.busy}>
      <div className="form-grid">
        <Field label="Название" required maxLength={100} value={name}
          onChange={event => setName(event.target.value)} />
        <Field label="Цвет" required maxLength={32} value={color}
          onChange={event => setColor(event.target.value)} />
      </div>
      <button type="submit">Создать и привязать</button>
    </fieldset></form>
    <p className="muted">Метки проекта: {(projectLabels.value?.content ?? []).map(item =>
      `${item.name} (${item.id})`).join(', ') || 'пока нет'}</p>
    <Alert error={command.error} /><Notice>{command.notice}</Notice>
  </section>;
}

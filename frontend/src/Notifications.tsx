import { useCommand, useLoad } from './hooks.ts';
import { useSession } from './session.tsx';
import { Alert, Empty, Loading, Notice, PageHeader, Pagination } from './ui.tsx';
import type { NotificationPage, User } from './types.ts';
import { useState } from 'react';

export function Notifications({ user }: { user: User }) {
  const { client, generation } = useSession();
  const [page, setPage] = useState(0);
  const [unreadOnly, setUnreadOnly] = useState(false);
  const command = useCommand();
  const query = useLoad(`${user.id}:${generation}:notifications:${page}:${unreadOnly}`, signal =>
    client.request<NotificationPage>(user.id,
      `/api/notifications?page=${page}&size=20&unreadOnly=${unreadOnly}`, 'GET', undefined, signal));
  return <>
    <PageHeader eyebrow="УВЕДОМЛЕНИЯ" title="Входящие">
      <button disabled={query.loading} onClick={query.reload}>Обновить</button>
      <button disabled={command.busy || !query.value?.unreadCount} onClick={() => void command.run(async () => {
        await client.request<void>(user.id, '/api/notifications/read-all', 'POST');
        command.setNotice('Все прочитаны.'); query.reload();
      })}>Прочитать все</button>
    </PageHeader>
    <p className="muted">In-app через outbox: назначение, комментарии, lifecycle и файлы. Email/Telegram позже.</p>
    <label className="field compact"><span>Только непрочитанные</span>
      <input type="checkbox" checked={unreadOnly} onChange={event => { setUnreadOnly(event.target.checked); setPage(0); }} />
    </label>
    <Alert error={query.error || command.error} /><Notice>{command.notice}</Notice>
    {query.loading && <Loading />}
    {query.value && (query.value.content.length === 0 ? <Empty title="Нет уведомлений">
      События задач появятся здесь после назначения, комментариев, смены статуса или файла.
    </Empty> : <>
      <p className="muted">Непрочитанных: {String(query.value.unreadCount)}</p>
      <ul className="task-list">{query.value.content.map(item =>
        <li key={String(item.id)}>
          <strong>{item.title}</strong>
          <span className="badge">{item.readAt ? 'Прочитано' : 'Новое'}</span>
          <p className="description-full">{item.body}</p>
          <small>{item.createdAt} · {item.eventType}</small>
          <a href={`#/tasks/${item.taskId}`} onClick={() => {
            if (item.readAt || command.busy) return;
            void command.run(async () => {
              await client.request<void>(user.id, `/api/notifications/${item.id}/read`, 'POST');
              query.reload();
            });
          }}>К задаче</a>
          {!item.readAt && <button className="text-button" disabled={command.busy} onClick={() =>
            void command.run(async () => {
              await client.request<void>(user.id, `/api/notifications/${item.id}/read`, 'POST');
              query.reload();
            })}>Прочитано</button>}
        </li>)}
      </ul>
      <Pagination page={page} total={query.value.totalPages} onChange={setPage} />
    </>)}
  </>;
}

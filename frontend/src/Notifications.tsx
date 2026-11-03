import { useCommand, useLoad } from './hooks.ts';
import { useSession } from './session.tsx';
import { Alert, Empty, Loading, Notice, PageHeader, Pagination } from './ui.tsx';
import type { EmailStatus, NotificationPage, TelegramLink, TelegramStatus, User } from './types.ts';
import { useState } from 'react';

export function Notifications({ user }: { user: User }) {
  const { client, generation } = useSession();
  const [page, setPage] = useState(0);
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [link, setLink] = useState<TelegramLink | null>(null);
  const command = useCommand();
  const query = useLoad(`${user.id}:${generation}:notifications:${page}:${unreadOnly}`, signal =>
    client.request<NotificationPage>(user.id,
      `/api/notifications?page=${page}&size=20&unreadOnly=${unreadOnly}`, 'GET', undefined, signal));
  const telegram = useLoad(`${user.id}:${generation}:telegram`, signal =>
    client.request<TelegramStatus>(user.id, '/api/telegram', 'GET', undefined, signal));
  const email = useLoad(`${user.id}:${generation}:email`, signal =>
    client.request<EmailStatus>(user.id, '/api/email', 'GET', undefined, signal));
  return <>
    <PageHeader eyebrow="УВЕДОМЛЕНИЯ" title="Входящие">
      <button disabled={query.loading} onClick={() => { query.reload(); telegram.reload(); email.reload(); }}>Обновить</button>
      <button disabled={command.busy || !query.value?.unreadCount} onClick={() => void command.run(async () => {
        await client.request<void>(user.id, '/api/notifications/read-all', 'POST');
        command.setNotice('Все прочитаны.'); query.reload();
      })}>Прочитать все</button>
    </PageHeader>
    <p className="muted">In-app всегда. Telegram/Email — опционально (assigned / comment / complete / fail).</p>
    <section className="panel">
      <h2>Email</h2>
      {email.loading && <Loading />}
      <Alert error={email.error} />
      {email.value && <>
        {!email.value.configured && <p className="muted">SMTP не настроен (`MAIL_HOST`).</p>}
        {email.value.configured && <>
          <p>{email.value.address} · {email.value.enabled ? 'уведомления включены' : 'уведомления выключены'}</p>
          <button disabled={command.busy} onClick={() => void command.run(async () => {
            await client.request<EmailStatus>(user.id, '/api/email/enabled', 'PUT',
              { enabled: !email.value!.enabled });
            email.reload();
          })}>{email.value.enabled ? 'Выключить' : 'Включить'}</button>
        </>}
      </>}
    </section>
    <section className="panel">
      <h2>Telegram</h2>
      {telegram.loading && <Loading />}
      <Alert error={telegram.error} />
      {telegram.value && <>
        {!telegram.value.configured && <p className="muted">Бот не настроен на сервере (`TELEGRAM_BOT_TOKEN`).</p>}
        {telegram.value.configured && !telegram.value.linked && <>
          <p className="muted">Привяжите чат через deep-link `/start`.</p>
          <button disabled={command.busy} onClick={() => void command.run(async () => {
            const created = await client.request<TelegramLink>(user.id, '/api/telegram/link', 'POST');
            setLink(created);
            command.setNotice('Ссылка создана. Откройте её в Telegram.');
          })}>Получить ссылку</button>
          {link && <p><a href={link.deepLink} target="_blank" rel="noreferrer">{link.deepLink}</a></p>}
        </>}
        {telegram.value.configured && telegram.value.linked && <>
          <p>Чат привязан · {telegram.value.enabled ? 'уведомления включены' : 'уведомления выключены'}</p>
          <button disabled={command.busy} onClick={() => void command.run(async () => {
            await client.request<TelegramStatus>(user.id, '/api/telegram/enabled', 'PUT',
              { enabled: !telegram.value!.enabled });
            telegram.reload();
          })}>{telegram.value.enabled ? 'Выключить' : 'Включить'}</button>
          <button className="text-button" disabled={command.busy} onClick={() => void command.run(async () => {
            await client.request<TelegramStatus>(user.id, '/api/telegram/link', 'DELETE');
            setLink(null);
            telegram.reload();
          })}>Отвязать</button>
        </>}
      </>}
    </section>
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

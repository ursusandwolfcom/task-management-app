import { useLoad } from './hooks.ts';
import { useSession } from './session.tsx';
import { Alert, Empty, Loading, PageHeader, Pagination } from './ui.tsx';
import type { PortfolioPage, User } from './types.ts';
import { useState } from 'react';

const risks: Record<string, string> = {
  ON_TRACK: 'В плане', AT_RISK: 'Риск срока', OVERDUE: 'Просрочен'
};

export function Portfolio({ user }: { user: User }) {
  const { client, generation } = useSession();
  const [page, setPage] = useState(0);
  const query = useLoad(`${user.id}:${generation}:portfolio:${page}`, signal =>
    client.request<PortfolioPage>(user.id, `/api/portfolio?page=${page}&size=20`, 'GET', undefined, signal));
  return <>
    <PageHeader eyebrow="УПРАВЛЕНИЕ" title="Обзор портфеля">
      <button disabled={query.loading} onClick={query.reload}>Обновить</button>
    </PageHeader>
    <p className="muted">Риск считается по рабочему календарю CPM: просрочка проекта или задач со сроком.</p>
    <Alert error={query.error} />{query.loading && <Loading />}
    {query.value && (query.value.content.length === 0 ? <Empty title="Нет доступных проектов">
      Создайте проект, чтобы увидеть риски.
    </Empty> : <>
      <ul className="task-list">{query.value.content.map(item =>
        <li key={String(item.projectId)}><a href={`#/projects/${item.projectId}`}>
          <strong>{item.name}</strong>
          <span className="badge">{risks[item.risk] ?? item.risk}</span>
          <small>открытых {String(item.openTasks)} · критических {String(item.criticalTasks)}
            · план {String(item.durationMinutes)} мин · до {item.endDate}</small>
        </a></li>)}
      </ul>
      <Pagination page={page} total={query.value.totalPages} onChange={setPage} />
    </>)}
  </>;
}

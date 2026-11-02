import { useLoad } from './hooks.ts';
import { useSession } from './session.tsx';
import { Alert, Empty, Loading, PageHeader, Pagination } from './ui.tsx';
import type { PortfolioPage, User, WorkloadSnapshot } from './types.ts';
import { useState } from 'react';

const risks: Record<string, string> = {
  ON_TRACK: 'В плане', AT_RISK: 'Риск срока', OVERDUE: 'Просрочен'
};

export function Portfolio({ user }: { user: User }) {
  const { client, generation } = useSession();
  const [page, setPage] = useState(0);
  const query = useLoad(`${user.id}:${generation}:portfolio:${page}`, signal =>
    client.request<PortfolioPage>(user.id, `/api/portfolio?page=${page}&size=20`, 'GET', undefined, signal));
  const load = useLoad(`${user.id}:${generation}:workload`, signal =>
    client.request<WorkloadSnapshot>(user.id, '/api/portfolio/workload', 'GET', undefined, signal));
  return <>
    <PageHeader eyebrow="УПРАВЛЕНИЕ" title="Обзор портфеля">
      <button disabled={query.loading || load.loading} onClick={() => { query.reload(); load.reload(); }}>Обновить</button>
    </PageHeader>
    <p className="muted">Риск — CPM-сроки. Загрузка — открытые назначенные задачи без выравнивания ресурсов.</p>
    <Alert error={query.error || load.error} />
    {(query.loading || load.loading) && <Loading />}
    {load.value && <section className="card tasks-panel">
      <h2>Загрузка исполнителей</h2>
      {load.value.assignees.length === 0 ? <Empty title="Нет назначенной работы">
        Назначьте исполнителей на открытые задачи.
      </Empty> : <ul className="task-list">{load.value.assignees.map(item =>
        <li key={String(item.userId)}>
          <strong>ID {String(item.userId)}</strong>
          <small>открытых {String(item.openTasks)} · оценка {String(item.estimatedMinutes)} мин
            · просрочено {String(item.overdueTasks)}</small>
        </li>)}
      </ul>}
    </section>}
    {query.value && (query.value.content.length === 0 ? <Empty title="Нет доступных проектов">
      Создайте проект, чтобы увидеть риски.
    </Empty> : <>
      <h2>Риски проектов</h2>
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

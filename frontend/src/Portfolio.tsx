import { useLoad } from './hooks.ts';
import { useSession } from './session.tsx';
import { Alert, Empty, Loading, PageHeader, Pagination } from './ui.tsx';
import type { PortfolioPage, ProjectRisk, User, WorkloadSnapshot } from './types.ts';
import { useState } from 'react';

const risks: Record<string, string> = {
  ON_TRACK: 'В плане', AT_RISK: 'Риск срока', OVERDUE: 'Просрочен'
};

function csvEscape(value: string) {
  if (/[",\n\r]/.test(value)) return `"${value.replace(/"/g, '""')}"`;
  return value;
}

function downloadPortfolioCsv(page: PortfolioPage, load: WorkloadSnapshot) {
  const riskRows = [
    ['section', 'projectId', 'name', 'risk', 'openTasks', 'criticalTasks', 'durationMinutes', 'endDate'],
    ...page.content.map(item => [
      'project', String(item.projectId), item.name, item.risk, String(item.openTasks),
      String(item.criticalTasks), String(item.durationMinutes), item.endDate
    ])
  ];
  const loadRows = [
    ['section', 'userId', 'openTasks', 'estimatedMinutes', 'overdueTasks'],
    ...load.assignees.map(item => [
      'assignee', String(item.userId), String(item.openTasks), String(item.estimatedMinutes),
      String(item.overdueTasks)
    ])
  ];
  const body = [...riskRows, ...loadRows].map(row => row.map(csvEscape).join(',')).join('\n');
  const blob = new Blob([body], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `portfolio-${page.calculatedAt.slice(0, 10)}.csv`;
  link.click();
  URL.revokeObjectURL(url);
}

function projectTaskHref(item: ProjectRisk) {
  const params = new URLSearchParams();
  if (item.risk !== 'ON_TRACK') params.set('bucket', 'WORK');
  const q = params.toString();
  return `#/projects/${item.projectId}${q ? `?${q}` : ''}`;
}

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
      <button disabled={!query.value || !load.value} onClick={() => {
        if (query.value && load.value) downloadPortfolioCsv(query.value, load.value);
      }}>Экспорт CSV</button>
    </PageHeader>
    <p className="muted">Риск и загрузка ведут к спискам задач. CSV — только доступные вам проекты и исполнители.</p>
    <Alert error={query.error || load.error} />
    {(query.loading || load.loading) && <Loading />}
    {load.value && <section className="card tasks-panel">
      <h2>Загрузка исполнителей</h2>
      {load.value.assignees.length === 0 ? <Empty title="Нет назначенной работы">
        Назначьте исполнителей на открытые задачи.
      </Empty> : <ul className="task-list">{load.value.assignees.map(item =>
        <li key={String(item.userId)}>
          <a href={`#/tasks?assigneeId=${item.userId}`}>
            <strong>ID {String(item.userId)}</strong>
            <span className="badge">Открыть задачи</span>
            <small>открытых {String(item.openTasks)} · оценка {String(item.estimatedMinutes)} мин
              · просрочено {String(item.overdueTasks)}</small>
          </a>
        </li>)}
      </ul>}
    </section>}
    {query.value && (query.value.content.length === 0 ? <Empty title="Нет доступных проектов">
      Создайте проект, чтобы увидеть риски.
    </Empty> : <>
      <h2>Риски проектов</h2>
      <ul className="task-list">{query.value.content.map(item =>
        <li key={String(item.projectId)}><a href={projectTaskHref(item)}>
          <strong>{item.name}</strong>
          <span className="badge">{risks[item.risk] ?? item.risk}</span>
          <span className="badge">К задачам</span>
          <small>открытых {String(item.openTasks)} · критических {String(item.criticalTasks)}
            · план {String(item.durationMinutes)} мин · до {item.endDate}</small>
        </a></li>)}
      </ul>
      <Pagination page={page} total={query.value.totalPages} onChange={setPage} />
    </>)}
  </>;
}

import { useEffect, useState } from 'react';
import { Auth, Profile } from './Account.tsx';
import { Notifications } from './Notifications.tsx';
import { Portfolio } from './Portfolio.tsx';
import { CreateProject, ProjectDetail, ProjectList } from './Projects.tsx';
import { MyTasks, TaskDetail } from './Tasks.tsx';
import { useCommand, useLoad } from './hooks.ts';
import { userId } from './json.ts';
import { useSession } from './session.tsx';
import { Alert, Loading } from './ui.tsx';
import type { NotificationPage, User } from './types.ts';

const themes = [ ['gray', 'Тёмная'], ['light', 'Светлая'], ['dark', 'Сине-серая'], ['graphite', 'Графит'],
  ['taupe', 'Тауп'], ['forest', 'Лесная'], ['ivory', 'Слоновая кость'], ['pearl', 'Жемчужная'] ];

function Theme() {
  const [theme, setTheme] = useState(() => {
    try { const stored = localStorage.getItem('projectflow.workspace.theme');
      return themes.some(([id]) => id === stored) ? stored ?? 'gray' : 'gray';
    } catch { return 'gray'; }
  });
  const [notice, setNotice] = useState('');
  useEffect(() => { document.documentElement.dataset.theme = theme; }, [theme]);
  return <label className="theme-picker"><span className="sr-only">Тема интерфейса</span>
    <select value={theme} onChange={event => {
      const selected = event.target.value; setTheme(selected);
      try { localStorage.setItem('projectflow.workspace.theme', selected); setNotice(''); }
      catch { setNotice('Тема применена для этой вкладки.'); }
    }}>{themes.map(([id, name]) => <option key={id} value={id}>{name}</option>)}</select>
    {notice && <small role="status">{notice}</small>}
  </label>;
}

function Content({ user, route }: { user: User; route: string }) {
  if (route === '/overview') return <Portfolio user={user} />;
  if (route === '/notifications') return <Notifications user={user} />;
  if (route === '/projects') return <ProjectList user={user} />;
  if (route === '/projects/new') return <CreateProject user={user} />;
  if (route === '/tasks') return <MyTasks user={user} />;
  if (route === '/profile') return <Profile user={user} />;
  const project = /^\/projects\/([1-9]\d{0,18})$/.exec(route);
  if (project) {
    try { return <ProjectDetail key={project[1]} user={user} id={userId(project[1])} />; }
    catch { /* Invalid route must not become a rounded server ID. */ }
  }
  const task = /^\/tasks\/([1-9]\d{0,18})$/.exec(route);
  if (task) {
    try { return <TaskDetail key={task[1]} user={user} id={userId(task[1])} />; }
    catch { /* Invalid route must not become a rounded server ID. */ }
  }
  return <section className="card"><h1>Страница не найдена</h1><a href="#/projects">Вернуться к проектам</a></section>;
}

function NavUnread({ user }: { user: User }) {
  const { client, generation } = useSession();
  const unread = useLoad(`${user.id}:${generation}:nav-unread`, signal =>
    client.request<NotificationPage>(user.id, '/api/notifications?page=0&size=1&unreadOnly=true',
      'GET', undefined, signal));
  const count = unread.value?.unreadCount;
  if (count == null || Number(count) === 0) return null;
  return <span className="nav-unread" aria-label={`Непрочитанных: ${String(count)}`}>{String(count)}</span>;
}

export function App() {
  const { user, lastUser, loading, error, client } = useSession();
  const [route, setRoute] = useState(window.location.hash.slice(1) || '/projects');
  const command = useCommand();
  useEffect(() => {
    const change = () => setRoute(window.location.hash.slice(1) || '/projects');
    window.addEventListener('hashchange', change); return () => window.removeEventListener('hashchange', change);
  }, []);
  return <><a className="skip-link" href={user ? '#main-content' : '#auth-content'} onClick={event => {
    event.preventDefault(); document.getElementById(user ? 'main-content' : 'auth-content')?.focus();
  }}>Перейти к содержимому</a>
    <div className="theme-corner"><Theme /></div>
    {!user && (loading ? <div className="initial-loading"><Loading /></div> : <Auth />)}
    {lastUser && <div className="workspace-layout" hidden={!user} key={String(lastUser.id)}>
      <aside className="sidebar"><a className="brand" href="#/projects"><span className="brand-mark">PF</span>ProjectFlow</a>
        <p className="sidebar-label">РАБОЧЕЕ ПРОСТРАНСТВО</p><nav aria-label="Основная навигация">
          <a href="#/overview" aria-current={route === '/overview' ? 'page' : undefined}><span aria-hidden="true">◈</span>Обзор</a>
          <a href="#/projects" aria-current={route.startsWith('/projects') ? 'page' : undefined}><span aria-hidden="true">◇</span>Проекты</a>
          <a href="#/tasks" aria-current={route === '/tasks' || route.startsWith('/tasks/') ? 'page' : undefined}><span aria-hidden="true">▣</span>Мои задачи</a>
          <a href="#/notifications" aria-current={route === '/notifications' ? 'page' : undefined}>
            <span aria-hidden="true">◉</span>Уведомления{user ? <NavUnread user={lastUser} /> : null}</a>
          <a href="#/profile" aria-current={route === '/profile' ? 'page' : undefined}><span aria-hidden="true">○</span>Профиль</a>
        </nav><div className="sidebar-bottom"><span className="small-dot" /> Ваша команда. Ваши проекты.</div>
      </aside>
      <div className="workspace-body"><header className="topbar"><p>Рабочее пространство</p>
        <div className="account-summary"><a href="#/profile" className="account-link"><span className="avatar" aria-hidden="true">
          {lastUser.firstName.slice(0, 1)}{lastUser.lastName.slice(0, 1)}</span><span><strong>{lastUser.firstName} {lastUser.lastName}</strong>
          <small>@{lastUser.username}</small></span></a>
          <button className="text-button" disabled={command.busy} onClick={() => void command.run(() => client.logout())}>Выйти</button>
        </div></header>
        <main id="main-content" className="main-content" tabIndex={-1}><Alert error={command.error || error} />
          <Content key={route.split('/').slice(0, 2).join('/')} user={lastUser} route={route} />
        </main><footer className="workspace-footer">ProjectFlow <span>Общий контекст для вашей команды</span></footer>
      </div>
    </div>}
  </>;
}

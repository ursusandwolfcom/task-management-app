import { decodeJson, encodeJson } from './json.ts';
import type { Credentials, Csrf, Registration, Session, User } from './types.ts';

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  constructor(status: number, code: string) {
    super(code); this.status = status; this.code = code;
  }
}
export type SessionEvent = 'changed' | 'signed-out';
export interface ClientOptions {
  fetch: typeof fetch;
  lock: <T>(action: () => Promise<T>) => Promise<T>;
  session: (user: User | null, clearDrafts?: boolean) => void;
  announce: (event: SessionEvent) => void;
}

/** All cookie-changing commands and authenticated operations share an origin-wide Web Lock. */
export class Client {
  readonly options: ClientOptions;
  constructor(options: ClientOptions) { this.options = options; }

  async raw<T>(path: string, method = 'GET', body?: unknown, csrf?: Csrf, signal?: AbortSignal): Promise<T> {
    const response = await this.options.fetch(path, {
      method, credentials: 'same-origin', cache: 'no-store', signal,
      headers: { ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
        ...(csrf ? { [csrf.headerName]: csrf.token } : {}) },
      ...(body === undefined ? {} : { body: encodeJson(body) })
    });
    if (response.status === 204) return undefined as T;
    const text = await response.text();
    const data: unknown = text ? decodeJson(text) : null;
    if (!response.ok) {
      const code = data && typeof data === 'object' && 'code' in data && typeof data.code === 'string'
        ? data.code : 'HTTP_ERROR';
      throw new ApiError(response.status, code);
    }
    return data as T;
  }

  private async restoreLocked(): Promise<User | null> {
    try { return await this.raw<User>('/api/users/me'); }
    catch (error) { if (!(error instanceof ApiError) || error.status !== 401) throw error; }
    const csrf = await this.raw<Csrf>('/api/session/csrf');
    try {
      const session = await this.raw<Session>('/api/session/refresh', 'POST', undefined, csrf);
      return session.user;
    } catch (error) {
      if (!(error instanceof ApiError) || error.status !== 401) throw error;
      return null;
    }
  }

  async restore(): Promise<User | null> {
    return this.options.lock(async () => {
      const user = await this.restoreLocked(); this.options.session(user); return user;
    });
  }

  async login(credentials: Credentials): Promise<User> {
    return this.options.lock(async () => {
      const csrf = await this.raw<Csrf>('/api/session/csrf');
      const session = await this.raw<Session>('/api/session/login', 'POST', credentials, csrf);
      this.options.session(session.user); this.options.announce('changed'); return session.user;
    });
  }

  async register(fields: Registration): Promise<User> {
    return this.options.lock(async () => {
      const csrf = await this.raw<Csrf>('/api/session/csrf');
      return this.raw<User>('/api/auth/register', 'POST', fields, csrf);
    });
  }

  async logout(): Promise<void> {
    return this.options.lock(async () => {
      const csrf = await this.raw<Csrf>('/api/session/csrf');
      await this.raw<void>('/api/session/logout', 'POST', undefined, csrf);
      this.options.session(null, true); this.options.announce('signed-out');
    });
  }

  private async requireActor(actor: bigint, signal?: AbortSignal): Promise<User> {
    signal?.throwIfAborted();
    // Recheck under the lock: another tab may have refreshed or switched accounts.
    const user = await this.restoreLocked();
    this.options.session(user);
    if (!user) throw new ApiError(401, 'INVALID_SESSION');
    if (user.id !== actor) throw new ApiError(409, 'ACCOUNT_CHANGED');
    return user;
  }

  async request<T>(actor: bigint, path: string, method = 'GET', body?: unknown, signal?: AbortSignal): Promise<T> {
    // Reads only need the lock for session restore; holding it for the whole GET
    // serializes every tab's project/task fetches and stalls dual-tab restore.
    if (method === 'GET') {
      await this.options.lock(async () => { await this.requireActor(actor, signal); });
      try {
        return await this.raw<T>(path, method, body, undefined, signal);
      } catch (error) {
        if (error instanceof ApiError && error.status === 401) this.options.session(null);
        throw error;
      }
    }
    return this.options.lock(async () => {
      await this.requireActor(actor, signal);
      const csrf = await this.raw<Csrf>('/api/session/csrf');
      try {
        const result = await this.raw<T>(path, method, body, csrf, signal);
        if (path === '/api/users/me/password' && method === 'POST') {
          this.options.session(null, true); this.options.announce('signed-out');
        }
        return result;
      } catch (error) {
        // Never replay a command. The user can restore the session and explicitly resubmit the preserved form.
        if (error instanceof ApiError && error.status === 401) this.options.session(null);
        throw error;
      }
    });
  }
}

export function explain(error: unknown): string {
  if (!(error instanceof ApiError)) {
    if (error instanceof Error && !(error instanceof TypeError)) return error.message;
    return 'Нет ответа сервера. Результат записи неизвестен: проверьте данные перед повтором. Форма сохранена.';
  }
  const messages: Record<string, string> = {
    INVALID_CREDENTIALS: 'Неверный логин или пароль.', DUPLICATE_IDENTITY: 'Логин или email уже занят.',
    VERSION_CONFLICT: 'Данные уже изменены. Ваши правки сохранены; загрузите актуальную версию и сверьте их.',
    INVALID_SESSION: 'Сессия завершилась. Войдите снова; ваша форма сохранена до входа в тот же аккаунт.',
    UNAUTHORIZED: 'Войдите снова, чтобы продолжить.', CSRF_INVALID: 'Защита сессии обновилась. Проверьте вход и повторите действие.',
    FORBIDDEN: 'У вас нет прав для этого действия.', PROJECT_NOT_FOUND: 'Проект не найден или недоступен.',
    MEMBER_NOT_FOUND: 'Участник не найден.', USER_NOT_FOUND: 'Пользователь с таким ID не найден.',
    MEMBER_ALREADY_EXISTS: 'Пользователь уже участвует в проекте.',
    OWNER_REMOVAL_FORBIDDEN: 'Сначала передайте владение другому участнику.',
    VALIDATION_ERROR: 'Проверьте поля формы, даты и часовой пояс.', MALFORMED_REQUEST: 'Проверьте формат полей.',
    ACCOUNT_CHANGED: 'Аккаунт изменился в другой вкладке. Действие не отправлено.',
    TASK_NOT_FOUND: 'Задача не найдена или недоступна.', ASSIGNEE_NOT_MEMBER: 'Исполнитель должен быть участником проекта.',
    ASSIGNEE_REQUIRED: 'Назначьте исполнителя перед стартом.', INVALID_TASK_TRANSITION: 'Это действие недоступно в текущем статусе.',
    DEPENDENCIES_NOT_COMPLETED: 'Сначала завершите предшествующие задачи.',
    TASK_NOT_IN_WORK: 'Старт доступен только из бокса Work. Переместите задачу и повторите.',
    ASSIGNMENT_CHANGE_FORBIDDEN: 'Исполнителя нельзя менять в этом статусе.',
    TASK_DELETE_FORBIDDEN: 'Удалить можно только задачу без истории выполнения.', TASK_HAS_DEPENDENTS: 'Сначала удалите зависимости от этой задачи.',
    SELF_DEPENDENCY: 'Задача не может зависеть от себя.', CYCLIC_DEPENDENCY: 'Зависимость создаёт цикл.',
    DEPENDENCY_ALREADY_EXISTS: 'Такая зависимость уже есть.', DEPENDENCY_CHANGE_FORBIDDEN: 'Зависимости меняются только до первого старта.',
    DEPENDENCY_NOT_FOUND: 'Зависимость не найдена.', CROSS_PROJECT_DEPENDENCY: 'Зависимость только внутри одного проекта.',
    GRAPH_TOO_LARGE: 'Граф слишком большой для этой операции.', COMMENT_NOT_FOUND: 'Комментарий не найден.',
    LABEL_NOT_FOUND: 'Метка не найдена.', LABEL_ALREADY_EXISTS: 'Метка с таким именем уже есть.',
    CROSS_PROJECT_LABEL: 'Метка принадлежит другому проекту.'
  };
  return messages[error.code] ?? 'Сервер отклонил действие. Форма сохранена; попробуйте после проверки данных.';
}

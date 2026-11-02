export interface User {
  id: bigint; username: string; email: string; firstName: string; lastName: string;
  role: 'ADMIN' | 'USER'; version: bigint;
}
export interface ProfileFields { username: string; email: string; firstName: string; lastName: string }
export interface Registration extends ProfileFields { password: string }
export interface Credentials { identifier: string; password: string }
export interface ProjectFields { name: string; description: string; startDate: string; endDate: string; timezone: string }
export interface Project extends ProjectFields {
  id: bigint; ownerId: bigint; version: bigint; inputRevision: bigint; calendarRevision: bigint; status: string;
}
export interface Member { userId: bigint; owner: boolean }
export interface Page<T> { content: T[]; page: number; size: number; totalElements: bigint; totalPages: number }
export interface Session { user: User; expiresAt: string }
export interface Csrf { headerName: string; token: string }

export type TaskStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED' | 'CANCELLED';
export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type TaskBucket = 'INBOX' | 'INCUBATOR' | 'WORK' | 'ARCHIVE';
export interface Task {
  id: bigint; projectId: bigint; name: string; description: string; priority: TaskPriority;
  estimatedMinutes: number | null; dueDate: string | null; assigneeId: bigint | null; bucket: TaskBucket;
  status: TaskStatus; actualStart: string | null; actualEnd: string | null; attemptCount: number; version: bigint;
}
export interface TaskFields {
  name: string; description: string; priority: TaskPriority; bucket: TaskBucket;
  estimatedMinutes: string; dueDate: string; assigneeId: string;
}
export interface TaskQuery {
  status: string; assigneeId: string; dueFrom: string; dueTo: string; labelId: string; bucket: string;
}
export interface ExecutionAttempt {
  number: number; actualStart: string; actualEnd: string | null; outcome: TaskStatus | null;
}
export interface BlockedTask { taskId: bigint; code: string; predecessorIds: bigint[] }
export interface AvailableTasks {
  projectId: bigint; inputRevision: bigint; tasks: Task[]; blockingReasons: BlockedTask[];
}
export interface Predecessors {
  taskId: bigint; projectId: bigint; inputRevision: bigint; dependsOnTaskIds: bigint[];
}
export interface Comment { id: bigint; projectId: bigint; taskId: bigint; authorId: bigint; text: string; createdAt: string }
export interface Label { id: bigint; projectId: bigint; name: string; color: string; version: bigint }

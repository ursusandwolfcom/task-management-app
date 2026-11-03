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
export interface Attachment {
  id: bigint; projectId: bigint; taskId: bigint; uploaderId: bigint; fileName: string;
  contentType: string; sizeBytes: bigint; createdAt: string;
}
export interface Label { id: bigint; projectId: bigint; name: string; color: string; version: bigint }
export interface GanttTask {
  taskId: bigint; durationMinutes: bigint; earlyStart: string; earlyFinish: string;
  lateStart: string; lateFinish: string; floatMinutes: bigint; critical: boolean; deadlineOverrun: boolean;
}
export interface CriticalDependency { predecessorId: bigint; successorId: bigint }
export interface ProjectGantt {
  projectId: bigint; inputRevision: bigint; calendarRevision: bigint; timezone: string;
  calculatedAt: string; projectStart: string; projectFinish: string; durationMinutes: bigint;
  projectDeadlineOverrun: boolean;
  tasks: GanttTask[]; criticalTaskIds: bigint[]; criticalEdges: CriticalDependency[];
}
export type PortfolioRisk = 'ON_TRACK' | 'AT_RISK' | 'OVERDUE';
export interface ProjectRisk {
  projectId: bigint; name: string; status: string; timezone: string; endDate: string;
  openTasks: bigint; criticalTasks: bigint; durationMinutes: bigint;
  projectDeadlineOverrun: boolean; hasTaskDeadlineOverrun: boolean; risk: PortfolioRisk;
}
export interface PortfolioPage {
  calculatedAt: string; content: ProjectRisk[]; page: number; size: number;
  totalElements: bigint; totalPages: number;
}
export interface AssigneeLoad {
  userId: bigint; openTasks: bigint; estimatedMinutes: bigint; overdueTasks: bigint;
}
export interface WorkloadSnapshot { calculatedAt: string; assignees: AssigneeLoad[] }
export interface NotificationItem {
  id: bigint; eventType: string; projectId: bigint; taskId: bigint; title: string; body: string;
  createdAt: string; readAt: string | null;
}
export interface NotificationPage {
  content: NotificationItem[]; page: number; size: number; totalElements: bigint; totalPages: number;
  unreadCount: bigint;
}
export interface TaskActivity {
  id: bigint; projectId: bigint; taskId: bigint; actorId: bigint; actionType: string;
  summary: string; createdAt: string;
}

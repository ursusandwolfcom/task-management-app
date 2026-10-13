export type Priority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type TaskStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED';
export interface Task {
  id: number;
  name: string;
  priority: Priority;
  estimatedMinutes: number | null;
  dueDate: string | null;
  status: TaskStatus;
  dependencies: number[];
}
export interface Project { id: number; name: string; description: string; tasks: Task[]; }
export interface Workspace { version: 1; revision: number; selectedProjectId: number; projects: Project[]; }
export interface Plan { executionOrder: number[]; availableIds: number[]; nextId: number | null; }
export type TaskSort = 'newest' | 'oldest' | 'execution';
export type Theme = 'dark' | 'light';
export interface Preferences { taskSort: TaskSort; theme: Theme; }

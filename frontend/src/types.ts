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

import type { InputHTMLAttributes, ReactNode } from 'react';
import { useId } from 'react';
import { explain } from './client.ts';

export function Field({ label, hint, ...input }: InputHTMLAttributes<HTMLInputElement> & { label: string; hint?: string }) {
  const generated = useId();
  const id = input.id ?? generated;
  return <div className="field"><label htmlFor={id}>{label}</label><input {...input} id={id}
    aria-describedby={hint ? `${id}-help` : input['aria-describedby']} />{hint && <small id={`${id}-help`}>{hint}</small>}</div>;
}
export function Alert({ error }: { error: unknown }) {
  return error ? <div role="alert" className="alert">{typeof error === 'string' ? error : explain(error)}</div> : null;
}
export function Notice({ children }: { children: ReactNode }) {
  return children ? <p role="status" className="notice">{children}</p> : null;
}
export function Loading() { return <p role="status" className="muted">Загрузка…</p>; }
export function Empty({ title, children }: { title: string; children: ReactNode }) {
  return <div className="empty"><span className="empty-symbol" aria-hidden="true">◇</span><h2>{title}</h2><p>{children}</p></div>;
}
export function PageHeader({ eyebrow, title, children }: { eyebrow: string; title: string; children?: ReactNode }) {
  return <header className="page-heading"><div><p className="eyebrow">{eyebrow}</p><h1>{title}</h1></div>{children}</header>;
}
export function Pagination({ page, total, onChange }: { page: number; total: number; onChange: (page: number) => void }) {
  return total > 1 ? <nav aria-label="Страницы" className="pagination">
    <button disabled={page === 0} onClick={() => onChange(page - 1)}>Назад</button>
    <span>Страница {page + 1} из {total}</span>
    <button disabled={page + 1 >= total} onClick={() => onChange(page + 1)}>Далее</button>
  </nav> : null;
}

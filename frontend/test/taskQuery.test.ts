import { test } from 'node:test';
import assert from 'node:assert/strict';
import { emptyTaskQuery, taskQueryString } from '../src/taskQuery.ts';

test('omits blank filters and keeps base paging params', () => {
  const query = taskQueryString({ page: 0, size: 20, projectId: 9n }, emptyTaskQuery());
  assert.equal(query, 'page=0&size=20&projectId=9');
});

test('encodes status assignee due and label filters', () => {
  const query = taskQueryString({ page: 1, size: 10 }, {
    status: 'NOT_STARTED', assigneeId: '42', dueFrom: '2026-01-01', dueTo: '2026-12-31', labelId: '7',
    bucket: 'WORK'
  });
  assert.equal(query,
    'page=1&size=10&status=NOT_STARTED&assigneeId=42&dueFrom=2026-01-01&dueTo=2026-12-31&labelId=7&bucket=WORK');
});


// @ts-check
/** @typedef {import('./types').Project} Project */
/** @typedef {import('./types').Task} Task */

export const demoGuides = {
  portal: 'Начните «Реализовать личный кабинет». Завершение откроет проверку безопасности. Запуск портала требует завершения обеих веток: безопасности и оплаты. Переключитесь на Канбан, чтобы показать все четыре статуса.',
  recovery: '«Восстановить платёжный шлюз» завершилась ошибкой и блокирует проверку заказов. Повторите её, запустите и завершите: проверка станет доступна. Высокий приоритет зависимой задачи не позволяет обойти незавершённого предшественника.',
  priorities: 'Выберите «Порядок выполнения»: критическая задача идёт первой, затем высокий приоритет; среди равных приоритетов решает срок, затем ID. Завершение любой доступной задачи меняет следующий шаг. Задачи без срока тоже участвуют в расчёте.'
};

/** @param {number} firstId @returns {Project[]} */
export function demoProjects(firstId) {
  /** @param {number} id @param {string} name @param {Task['priority']} priority
   * @param {Task['status']} status @param {number[]} dependencies @param {number | null} minutes
   * @param {string | null} [dueDate] @returns {Task} */
  const task = (id, name, priority, status, dependencies, minutes, dueDate = null) =>
    ({ id, name, priority, status, dependencies, estimatedMinutes: minutes, dueDate });
  return [
    { id: firstId, demoKey: 'portal', name: 'Демо · Клиентский портал',
      description: 'Запуск B2B-портала: параллельная разработка, контроль качества и единая точка релиза. Демонстрационные данные, сроки условные.',
      tasks: [
        task(1, 'Согласовать требования', 'HIGH', 'COMPLETED', [], 120),
        task(2, 'Утвердить дизайн', 'HIGH', 'COMPLETED', [1], 180),
        task(3, 'Реализовать личный кабинет', 'CRITICAL', 'NOT_STARTED', [2], 480, '2026-11-10'),
        task(4, 'Подключить оплату', 'HIGH', 'IN_PROGRESS', [1], 240, '2026-11-12'),
        task(5, 'Проверить безопасность', 'CRITICAL', 'NOT_STARTED', [3], 120, '2026-11-13'),
        task(6, 'Подготовить инструкции', 'MEDIUM', 'NOT_STARTED', [2], 90),
        task(7, 'Исправить импорт клиентов', 'HIGH', 'FAILED', [1], 150),
        task(8, 'Запустить портал', 'CRITICAL', 'NOT_STARTED', [4, 5, 6, 7], 60, '2026-11-16')
      ] },
    { id: firstId + 1, demoKey: 'recovery', name: 'Демо · Восстановление сервиса',
      description: 'Инцидент в обработке заказов: повторная попытка, блокировка зависимых задач и безопасное возобновление работы.',
      tasks: [
        task(1, 'Локализовать причину сбоя', 'CRITICAL', 'COMPLETED', [], 30),
        task(2, 'Восстановить платёжный шлюз', 'HIGH', 'FAILED', [1], 60),
        task(3, 'Проверить обработку заказов', 'CRITICAL', 'NOT_STARTED', [2], 30),
        task(4, 'Подготовить уведомление клиентам', 'MEDIUM', 'IN_PROGRESS', [1], 20),
        task(5, 'Закрыть инцидент', 'HIGH', 'NOT_STARTED', [3, 4], 15),
        task(6, 'Обновить регламент поддержки', 'LOW', 'NOT_STARTED', [], null)
      ] },
    { id: firstId + 2, demoKey: 'priorities', name: 'Демо · Очередь приоритетов',
      description: 'Независимые задачи и одна зависимость: приоритет → срок → идентификатор. Даты условные; длительность пока не используется для расписания.',
      tasks: [
        task(1, 'Проверить договор · срок 18 ноября', 'HIGH', 'NOT_STARTED', [], 60, '2026-11-18'),
        task(2, 'Согласовать смету · срок 12 ноября', 'HIGH', 'NOT_STARTED', [], 45, '2026-11-12'),
        task(3, 'Подтвердить бюджет · срок 12 ноября', 'HIGH', 'NOT_STARTED', [], 30, '2026-11-12'),
        task(4, 'Устранить риск релиза', 'CRITICAL', 'NOT_STARTED', [], 90),
        task(5, 'Обновить презентацию', 'MEDIUM', 'NOT_STARTED', [], null),
        task(6, 'Архивировать материалы', 'LOW', 'NOT_STARTED', [], 20),
        task(7, 'Выпустить подтверждение бюджета', 'CRITICAL', 'NOT_STARTED', [3], 15)
      ] }
  ];
}

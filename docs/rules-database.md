# Правила работы с базой данных — EarnIt Kids

> [!NOTE]
> AI agents: core rules live in root `AGENTS.md`. This file provides extended detail for humans.

## СУБД

- **PostgreSQL** — единственная БД проекта
- **Драйвер** — `pg` (node-postgres), connection pool
- **ORM** — не используется, только raw SQL с параметризацией

---

## Схема базы данных

### Таблицы

| Таблица | Назначение | Ключевые поля |
|---|---|---|
| `migrations` | Трекинг выполненных миграций | `name`, `executed_at` |
| `families` | Семьи (аккаунты родителей) | `family_id`, `email`, `admin_password`, `is_blocked` |
| `children` | Дети (привязаны к семье) | `family_id` (FK), `name`, `token`, `balance`, `monthly_limit` |
| `tasks` | Задачи для детей | `child_id` (FK), `task_id`, `name`, `coins`, `group_name`, `frequency`, `is_deleted` |
| `shop_items` | Товары магазина | `child_id` (FK), `item_id`, `name`, `price`, `group_name`, `frequency`, `is_deleted` |
| `history` | История операций | `child_id` (FK), `type`, `amount`, `description`, `task_id`, `comment`, `money_amount` |
| `requests` | Запросы на начисление/покупку | `child_id` (FK), `task_id`, `coins`, `status`, `request_type`, `item_id` |
| `friends` | Друзья (ребёнок↔ребёнок) | `child_id` (FK), `friend_child_id` (FK) |
| `super_admin` | Суперадминистраторы | `email`, `password` |
| `push_tokens` | Push-токены устройств | `child_id`, `token`, `platform` |

### Связи (ER)

```
families ──1:N──> children ──1:N──> tasks
                           ──1:N──> shop_items
                           ──1:N──> history
                           ──1:N──> requests
                           ──N:M──> friends (self-join через child_id / friend_child_id)
                           ──1:N──> push_tokens
```

---

## Миграции

### Правила

1. **Папка** — `migrations/` в корне проекта
2. **Именование** — `NNN_description.sql` (001, 003, 004, ..., 010)
3. **Иммутабельность** — после мержа миграция НЕ редактируется
4. **Новая миграция** — всегда следующий номер
5. **Идемпотентность** — используй `IF NOT EXISTS`, `IF EXISTS` где возможно
6. **Скрипт** — `npm run migrate` (`scripts/migrate.js`)
7. **Трекинг** — таблица `migrations`, миграция выполняется только один раз

### Текущие миграции

| №   | Описание |
|-----|----------|
| 001 | Начальная схема: families, family_data, tasks, shop_items, history, requests, friends, super_admin |
| 003 | Верификация email |
| 004 | Multi-child — таблица `children`, миграция данных с family_data |
| 005 | Лимиты для детей |
| 006 | Purchase flow — `request_type`, `item_id`, `money_amount` в requests |
| 007 | Push-токены |
| 008 | Отдельные поля `task_id` и `comment` в history |
| 009 | Soft delete для tasks и shop_items (`is_deleted`) |
| 010 | Очистка устаревших колонок history |

---

## Правила написания SQL

### Запросы

- **Параметризация** — всегда `$1, $2, ...`, НИКОГДА строковая интерполяция
- **Именование таблиц** — `snake_case`, множественное число (`tasks`, `shop_items`)
- **Именование колонок** — `snake_case` (`family_id`, `created_at`, `is_deleted`)
- **Индексы** — `idx_tablename_column` (например `idx_tasks_family_id`)
- **Constraints** — `tablename_col1_col2_key` для UNIQUE

### Пример правильного запроса

```javascript
const { rows } = await pool.query(
    'SELECT * FROM tasks WHERE child_id = $1 AND is_deleted = false ORDER BY created_at DESC',
    [childId]
);
```

### Транзакции

```javascript
const client = await pool.connect();
try {
    await client.query('BEGIN');
    await client.query('UPDATE children SET balance = balance - $1 WHERE id = $2', [amount, childId]);
    await client.query('INSERT INTO history (child_id, type, amount) VALUES ($1, $2, $3)', [childId, 'spend', amount]);
    await client.query('COMMIT');
} catch (e) {
    await client.query('ROLLBACK');
    throw e;
} finally {
    client.release();
}
```

---

## Soft Delete

- Таблицы `tasks` и `shop_items` поддерживают soft delete: поле `is_deleted BOOLEAN DEFAULT false`
- При удалении: `UPDATE tasks SET is_deleted = true WHERE id = $1`
- При выборке: всегда фильтровать `WHERE is_deleted = false`
- История (history) хранит ссылку `task_id` на задачу, поэтому задача не удаляется физически

---

## Принципы

1. **Скоупинг по child_id** — все данные (tasks, shop_items, history, requests) привязаны к конкретному ребёнку через `child_id`
2. **CASCADE DELETE** — при удалении семьи каскадно удаляются дети, при удалении ребёнка — его данные
3. **Timestamps** — `created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()` на всех таблицах
4. **Balance** — хранится в таблице `children`, обновляется при earn/spend операциях
5. **Frequency** — JSONB поле в tasks/shop_items для настройки повторяемости (daily, weekly и т.д.)
6. **External ID** — `external_id BIGINT` в history и requests для ID из клиентского приложения

---

## Подключение

- **Pool** — `pg.Pool` в `src/db/connection.js`
- **Env-переменные** — `DATABASE_URL` или `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD`
- **Тестирование подключения** — `testConnection()` при старте сервера

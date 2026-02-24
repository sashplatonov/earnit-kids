# Проверка и исправление изоляции данных

## Описание проблемы

Ребёнок одной семьи может видеть задания другого ребёнка по magic-ссылке. Необходимо гарантировать:
- **Все** API вызовы проверяют JWT-токен
- Данные выбираются/обновляются **только** в пределах `familyId` + `childId` из токена
- Роль (`admin`/`child`) определяет, к каким данным есть доступ

---

## Полная матрица API endpoints

### Легенда
- ✅ = Корректно защищено
- ⚠️ = Частично защищено (требует исправления)
- 🔴 = Уязвимо (отсутствует проверка)

### Auth endpoints (без токена — OK)

| Endpoint | Метод | Токен | Статус | Комментарий |
|----------|-------|-------|--------|-------------|
| `/api/login` | POST | Нет | ✅ | Public — это login |
| `/api/register` | POST | Нет | ✅ | Public — регистрация |
| `/api/logout` | POST | Нет | ✅ | Очищает cookies |
| `/api/forgot-password` | POST | Нет | ✅ | Public — восстановление |
| `/api/reset-password` | POST | Нет | ✅ | Public — сброс пароля |
| `/api/verify` | POST | Нет | ✅ | Public — верификация email |
| `/api/auth-config` | GET | Нет | ✅ | Public — конфиг |
| `/login-child/:token` | GET | Нет | ✅ | Magic link → JWT |

### Infrastructure (без токена)

| Endpoint | Метод | Токен | Статус | Комментарий |
|----------|-------|-------|--------|-------------|
| `/api/health` | GET | Нет | ✅ | Health check — OK без авторизации |
| `/api/metrics` | GET | Нет | ⚠️ | **Должен быть защищён** — метрики приложения |
| `/api/docs` | GET | Нет | ✅ | Документация — допустимо |
| `/api/openapi.yaml` | GET | Нет | ✅ | OpenAPI spec — допустимо |

### Main API (требуют [apiAuthMiddleware](file:///Users/sash/Dev/Projects/coins-kids-shop-web/src/routes/api.js#26-40) — проверка `ctx.familyId`)

| Endpoint | Метод | Auth | Role check | Data scoping | Статус |
|----------|-------|------|------------|--------------|--------|
| `/api/data` | GET | ✅ familyId | ⚠️ | 🔴 tasks/shop по `family_id` без `childId` | **Дети видят данные друг друга!** |
| `/api/data` | POST | ✅ familyId | ✅ role check | ⚠️ [syncBalances](file:///Users/sash/Dev/Projects/coins-kids-shop-web/src/db/syncRepository.js#5-20) без ownership | Частично |
| `/api/children` | POST | ✅ familyId | ✅ admin only | ✅ | OK |
| `/api/base-data` | GET | ✅ familyId | — | ✅ | Общие данные |
| `/api/update-nickname` | POST | ✅ familyId | ✅ child only | ✅ `ctx.childId` | OK |
| `/api/search-user` | GET | ✅ familyId | ✅ child only | 🔴 Возвращает `family_id` | Утечка |
| `/api/add-friend` | POST | ✅ familyId | ✅ child only | ⚠️ `friendId` не валидируется | — |
| `/api/friends-list` | GET | ✅ familyId | ✅ child only | ✅ `ctx.childId` | OK |
| `/api/analytics` | GET | ✅ familyId | ✅ admin/child | ✅ child→`ctx.childId` | OK |
| `/api/history` | GET | ✅ familyId | — | ✅ child→`ctx.childId` | OK |
| `/api/requests` | GET | ✅ familyId | — | ✅ child→`ctx.childId` | OK |

### Children dynamic routes (admin only)

| Endpoint | Метод | Auth | Role | Ownership check | Статус |
|----------|-------|------|------|-----------------|--------|
| `/api/children/:id/link` | GET | ✅ | ✅ admin | 🔴 `targetChildId` из URL, не проверяется что ребёнок принадлежит семье | **Опасно** |
| `/api/children/:id/regenerate-token` | POST | ✅ | ✅ admin | ⚠️ Проверка в service, но не в controller | Частично |
| `/api/children/:id` | DELETE | ✅ | ✅ admin | ⚠️ Проверка в service, но не в controller | Частично |
| `/api/children/:id/settings` | POST | ✅ | ✅ admin | ⚠️ Проверка в service, но не в controller | Частично |

### WebSocket

| Endpoint | Auth | Статус |
|----------|------|--------|
| `/ws` | ✅ JWT из cookie | ✅ — `familyId` scoped |

### Super Admin (все требуют `role === 'super_admin'`)

| Endpoint | Статус |
|----------|--------|
| `/api/super/*` | ✅ Двойная проверка: в middleware + [handleSuperAdminAPI](file:///Users/sash/Dev/Projects/coins-kids-shop-web/src/controllers/superAdminController.js#97-125) |

---

## Выявленные уязвимости (11 штук)

### 🔴 CRITICAL

| # | Уязвимость | Файл | Строка |
|---|-----------|------|--------|
| 1 | **Tasks загружаются по `family_id` без фильтра `childId`** — все дети видят задания друг друга | [familyDataRepository.js](file:///Users/sash/Dev/Projects/coins-kids-shop-web/src/db/familyDataRepository.js#L31) | 31 |
| 2 | **Shop items загружаются по `family_id` без фильтра `childId`** | [familyDataRepository.js](file:///Users/sash/Dev/Projects/coins-kids-shop-web/src/db/familyDataRepository.js#L32) | 32 |
| 3 | **Admin может запросить link/settings/delete ребёнка чужой семьи** — `targetChildId` берётся из URL без ownership-проверки в роутере | [api.js](file:///Users/sash/Dev/Projects/coins-kids-shop-web/src/routes/api.js#L142-L171) | 142-171 |

### 🟠 HIGH

| # | Уязвимость | Файл |
|---|-----------|------|
| 4 | [updateBalance](file:///Users/sash/Dev/Projects/coins-kids-shop-web/src/db/familyDataRepository.js#176-180) — `WHERE id=$2` без `family_id`, можно изменить баланс ребёнка чужой семьи | [familyDataRepository.js](file:///Users/sash/Dev/Projects/coins-kids-shop-web/src/db/familyDataRepository.js#L178) |
| 5 | [updateRequestStatus](file:///Users/sash/Dev/Projects/coins-kids-shop-web/src/db/familyDataRepository.js#180-184) — `WHERE id=$2` без `family_id`, запрос чужой семьи | [familyDataRepository.js](file:///Users/sash/Dev/Projects/coins-kids-shop-web/src/db/familyDataRepository.js#L182) |
| 6 | [syncBalances](file:///Users/sash/Dev/Projects/coins-kids-shop-web/src/db/syncRepository.js#5-20) — обновляет `children.balance` по [id](file:///Users/sash/Dev/Projects/coins-kids-shop-web/src/utils/authUtils.js#67-76) без проверки `family_id` | [syncRepository.js](file:///Users/sash/Dev/Projects/coins-kids-shop-web/src/db/syncRepository.js#L8-L18) |
| 7 | Admin `GET /api/data?childId=X` — нет проверки что `X` принадлежит `ctx.familyId` | [familyController.js](file:///Users/sash/Dev/Projects/coins-kids-shop-web/src/controllers/familyController.js#L27-L28) |

### 🟡 MEDIUM

| # | Уязвимость | Файл |
|---|-----------|------|
| 8 | [searchByNickname](file:///Users/sash/Dev/Projects/coins-kids-shop-web/src/db/childRepository.js#112-126) возвращает `family_id` вместо `child_id` | [childRepository.js](file:///Users/sash/Dev/Projects/coins-kids-shop-web/src/db/childRepository.js#L112-L124) |
| 9 | `/api/metrics` доступен без авторизации — утечка внутренних метрик | [api.js](file:///Users/sash/Dev/Projects/coins-kids-shop-web/src/routes/api.js#L64-L68) |
| 10 | [updateChild](file:///Users/sash/Dev/Projects/coins-kids-shop-web/tests/unit/familyService.test.js#23-24) не проверяет `family_id` — можно обновить ребёнка другой семьи | [childRepository.js](file:///Users/sash/Dev/Projects/coins-kids-shop-web/src/db/childRepository.js#L66-L91) |
| 11 | [addFriend](file:///Users/sash/Dev/Projects/coins-kids-shop-web/src/services/familyService.js#194-215) не проверяет что `friendId` — реальный ребёнок другой семьи (не свой) | [familyDataRepository.js](file:///Users/sash/Dev/Projects/coins-kids-shop-web/src/db/familyDataRepository.js#L184-L186) |

---

## Предлагаемые изменения

### Компонент 1: Фильтрация GET /api/data по childId

#### [MODIFY] [familyDataRepository.js](file:///Users/sash/Dev/Projects/coins-kids-shop-web/src/db/familyDataRepository.js)

**Изменение 1:** Фильтрация tasks и shop по `childId` (фикс #1, #2):

```diff
-query(`SELECT t.*, t.group_name FROM tasks t WHERE t.family_id = $1 AND t.is_deleted = false`, [dbId])
+query(`SELECT t.*, t.group_name FROM tasks t WHERE t.family_id = $1 AND t.is_deleted = false${childId ? ' AND t.child_id = $2' : ''}`, childId ? [dbId, childId] : [dbId])

-query(`SELECT s.*, s.group_name FROM shop_items s WHERE s.family_id = $1 AND s.is_deleted = false`, [dbId])
+query(`SELECT s.*, s.group_name FROM shop_items s WHERE s.family_id = $1 AND s.is_deleted = false${childId ? ' AND s.child_id = $2' : ''}`, childId ? [dbId, childId] : [dbId])
```

**Изменение 2:** Добавить `family_id` в [updateBalance](file:///Users/sash/Dev/Projects/coins-kids-shop-web/src/db/familyDataRepository.js#176-180) (фикс #4):

```diff
-query('UPDATE children SET balance=$1 WHERE id=$2', [b, cid])
+query('UPDATE children SET balance=$1 WHERE id=$2 AND family_id=(SELECT id FROM families WHERE family_id=$3)', [b, cid, fid])
```

**Изменение 3:** Добавить `family_id` в [updateRequestStatus](file:///Users/sash/Dev/Projects/coins-kids-shop-web/src/db/familyDataRepository.js#180-184) (фикс #5):

```diff
-query('UPDATE requests SET status=$1, updated_at=NOW() WHERE id=$2', [s, id])
+query('UPDATE requests SET status=$1, updated_at=NOW() WHERE id=$2 AND family_id=(SELECT id FROM families WHERE family_id=$3)', [s, id, fid])
```

---

### Компонент 2: Ownership middleware для children routes

#### [MODIFY] [api.js](file:///Users/sash/Dev/Projects/coins-kids-shop-web/src/routes/api.js)

Добавить helper-функцию `validateChildOwnership` и применить ко всем `/api/children/:id/*` (фикс #3):

```javascript
// Новый helper — проверяет, что ребёнок принадлежит семье
async function validateChildOwnership(ctx, targetChildId, res) {
    const families = await loadFamilies();
    const familyInfo = families.families[ctx.familyId];
    if (!familyInfo || !familyInfo.children.some(c => c.id === targetChildId)) {
        sendJSON(res, { error: 'Child not found in family' }, 404);
        return false;
    }
    return true;
}
```

Применить в каждом обработчике children routes:
```javascript
fn: async (c, rq, rs) => {
    c.targetChildId = parseInt(c.params.id);
    if (!await validateChildOwnership(c, c.targetChildId, rs)) return;
    await childController.handleLinkGet({ ctx: c, req: rq, res: rs, targetChildId: c.targetChildId });
}
```

---

### Компонент 3: Валидация childId в контроллере

#### [MODIFY] [familyController.js](file:///Users/sash/Dev/Projects/coins-kids-shop-web/src/controllers/familyController.js)

Добавить проверку ownership для admin `GET /api/data?childId=X` (фикс #7):

```diff
 async function handleDataGet(ctx, req, res) {
     const queryChildId = ctx.urlObj.searchParams.get('childId');
-    const targetChildId = ctx.role === 'child' ? ctx.childId : (queryChildId ? parseInt(queryChildId) : null);
+    let targetChildId;
+    if (ctx.role === 'child') {
+        targetChildId = ctx.childId;
+    } else if (queryChildId) {
+        targetChildId = parseInt(queryChildId);
+        // Проверить, что ребёнок принадлежит этой семье
+        const families = await loadFamilies();
+        const familyInfo = families.families[ctx.familyId];
+        if (!familyInfo?.children?.some(c => c.id === targetChildId)) {
+            return sendJSON(res, { error: 'Child not found' }, 404);
+        }
+    } else {
+        targetChildId = null;
+    }
```

Аналогично для [handleHistoryGet](file:///Users/sash/Dev/Projects/coins-kids-shop-web/src/controllers/familyController.js#75-84) и [handleRequestsGet](file:///Users/sash/Dev/Projects/coins-kids-shop-web/src/controllers/familyController.js#85-94).

---

### Компонент 4: Защита syncBalances

#### [MODIFY] [syncRepository.js](file:///Users/sash/Dev/Projects/coins-kids-shop-web/src/db/syncRepository.js)

Добавить `AND family_id = $3` (фикс #6):

```diff
-await client.query('UPDATE children SET balance = $1 WHERE id = $2', [data.balance, actingChildId]);
+await client.query('UPDATE children SET balance = $1 WHERE id = $2 AND family_id = $3', [data.balance, actingChildId, dbId]);

-await client.query('UPDATE children SET balance = $1 WHERE id = $2', [child.balance, child.id]);
+await client.query('UPDATE children SET balance = $1 WHERE id = $2 AND family_id = $3', [child.balance, child.id, dbId]);
```

---

### Компонент 5: Скрытие family_id и защита updateChild

#### [MODIFY] [childRepository.js](file:///Users/sash/Dev/Projects/coins-kids-shop-web/src/db/childRepository.js)

**Изменение 1:** Не возвращать `family_id` в searchByNickname (фикс #8):

```diff
-return result.rows.map(row => ({
-    id: row.family_id,
-    nickname: row.name
-}));
+return result.rows.map(row => ({
+    id: row.id,
+    nickname: row.name
+}));
```

**Изменение 2:** Добавить `familyDbId` в updateChild (фикс #10):

```diff
-async function updateChild(childId, data) {
+async function updateChild(childId, data, familyDbId = null) {
     // ...
-    const result = await query(`UPDATE children SET ${clauses.join(', ')} WHERE id = $${vals.length}`, vals);
+    let whereClause = `WHERE id = $${vals.length}`;
+    if (familyDbId) {
+        vals.push(familyDbId);
+        whereClause += ` AND family_id = $${vals.length}`;
+    }
+    const result = await query(`UPDATE children SET ${clauses.join(', ')} ${whereClause}`, vals);
```

---

### Компонент 6: Защита /api/metrics

#### [MODIFY] [api.js](file:///Users/sash/Dev/Projects/coins-kids-shop-web/src/routes/api.js)

Добавить проверку роли super_admin (фикс #9):

```diff
 apiRouter.get('/api/metrics', async (ctx, req, res) => {
+    if (ctx.role !== 'super_admin') {
+        return sendJSON(res, { error: 'Forbidden' }, 403);
+    }
     const { generateMetrics } = require('../utils/metrics');
```

---

## Файлы для изменения — сводка

| Файл | Изменения |
|------|-----------|
| [familyDataRepository.js](file:///Users/sash/Dev/Projects/coins-kids-shop-web/src/db/familyDataRepository.js) | Фильтр tasks/shop по `childId`, ownership в [updateBalance](file:///Users/sash/Dev/Projects/coins-kids-shop-web/src/db/familyDataRepository.js#176-180)/[updateRequestStatus](file:///Users/sash/Dev/Projects/coins-kids-shop-web/src/db/familyDataRepository.js#180-184) |
| [api.js](file:///Users/sash/Dev/Projects/coins-kids-shop-web/src/routes/api.js) | `validateChildOwnership` middleware, защита `/api/metrics` |
| [familyController.js](file:///Users/sash/Dev/Projects/coins-kids-shop-web/src/controllers/familyController.js) | Ownership check для `queryChildId` в handleDataGet/History/Requests |
| [syncRepository.js](file:///Users/sash/Dev/Projects/coins-kids-shop-web/src/db/syncRepository.js) | `AND family_id` в syncBalances |
| [childRepository.js](file:///Users/sash/Dev/Projects/coins-kids-shop-web/src/db/childRepository.js) | Скрыть `family_id`, ownership в [updateChild](file:///Users/sash/Dev/Projects/coins-kids-shop-web/tests/unit/familyService.test.js#23-24) |

---

## План верификации

### Unit-тесты

#### [NEW] [dataIsolation.test.js](file:///Users/sash/Dev/Projects/coins-kids-shop-web/tests/unit/dataIsolation.test.js)

| Тест | Что проверяет |
|------|--------------|
| `child sees only own tasks` | tasks фильтруются по `childId` |
| `child sees only own shop` | shop items фильтруются по `childId` |
| `admin cannot access child of another family` | ownership validation |
| `updateBalance rejects foreign child` | `family_id` в WHERE |
| `updateRequestStatus rejects foreign request` | `family_id` в WHERE |
| `searchByNickname does not expose family_id` | формат ответа |
| `syncBalances scoped by family` | `family_id` в UPDATE |

### Автоматические тесты

```bash
cd /Users/sash/Dev/Projects/coins-kids-shop-web
npm test          # Все unit-тесты
npm run lint      # Линтер
```

### Ручная верификация

1. **Тест между детьми одной семьи:** Magic link ребёнка A1 → видны только задания A1, не A2
2. **Тест между семьями:** Magic link ребёнка B1 → не видны данные семьи A
3. **API injection:** `GET /api/data?childId=<чужой id>` → 404
4. **Children API:** `GET /api/children/<чужой id>/link` → 404

---
name: java-service-standards
description: "Стандарты разработки Java-сервисов для EarnIt Kids — конвенции сборки, стиля, архитектуры, тестирования и качества. Используется агентами для проверки PR, генерации шаблонов и выдачи рекомендаций."
---

## Краткое описание
- Цель: зафиксировать практики и правила для разработки backend-сервисов (Quarkus) в проекте EarnIt Kids.
- Область применения: любые изменения в `backend/` — ресурсы (REST), сервисы, репозитории, DTO, миграции и тесты.

## Когда использовать
- При создании/изменении REST-эндпоинтов, сервисного слоя, репозиториев или миграций.
- Для проверки соответствия PR код-стайлу, сборки и тестового покрытия перед мержем.

## Решение (Decision Flow)
1. Boundary: изменения касаются `backend/`? Да → применить эти правила.
2. DTOs: входные/выходные payloads → предпочтительно `record` (immutable request/response).
3. Injection: используем конструкторную инъекцию для бинов (Quarkus/Arc).
4. Ошибки API: использовать RFC-7807-подобную структуру `ErrorResponse`.
5. Миграции: новые SQL-файлы `NNN_description.sql`, не редактировать уже применённые.
6. Контроль качества: `mvnw validate` должен пройти (Checkstyle), `mvnw test` — тесты.

## Контрольный список (pre-merge)
- Код проходит `./mvnw validate` (Checkstyle конфиг `config/checkstyle.xml`, сборка падает при ошибках).
- Юнит/ресурсные тесты проходят: `./mvnw test`.
- JaCoCo: глобальное покрытие >= 80% (см. `pom.xml` исключения).
- OpenAPI аннотации обновлены для изменённых эндпоинтов.
- Миграции добавлены и имеют имена `NNN_description.sql`.
- SQL — параметризованные запросы (ни в коем случае строковая интерполяция).
- Запросы/репозитории фильтруют по `family_id` на основе JWT (изоляция данных).
- Не допускаются вложенные типы: каждый класс/интерфейс — отдельный top-level файл.

## Стиль и правила (выдержки из `config/checkstyle.xml` и конвенций)
- Javadoc и блок-комментарии запрещены; предпочитаются однострочные комментарии `//`.
- Комментарии разрешены ТОЛЬКО с префиксами `EXPLAIN:` или `FIXME:` для сложной логики/фиксов.
- Блоковые `/* ... */` запрещены.
- Максимальная длина строки: 120 символов.
- Максимальная длина метода: 60 строк.
- Максимальное число параметров в методе: 10.
- Запрещены `import *` и неиспользуемые импорты.
- Именование типов/методов/переменных — стандартные Checkstyle правила (TypeName, MethodName и пр.).

## Архитектурные паттерны и соглашения
- Структура слоёв: `resource/` → `service/` → `repository/` → `domain/model/`.
- `resource/` — JAX-RS endpoints, бины должны быть минимальны: делегировать в сервис.
- `service/` — бизнес-правила, возвращают `OperationResult` при необходимости.
- `repository/` — Panache/JPA репозитории + кастомные SQL; все SQL‑запросы параметризованы.
- DTO: `dto/request/` и `dto/response/` как immutable `record`-ы.
- Конфигурация: `@ConfigMapping` для групповых настроек.
- Аутентификация: `AuthFilter` ставит `AuthContext` для downstream чтения; используйте его вместо парсинга cookies снова.

## Сборка и тестирование (точные команды)
```bash
# локально (пример среды):
export JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn"
export PATH="$JAVA_HOME/bin:$PATH"
cd backend
./mvnw validate   # Checkstyle
./mvnw test       # unit/resource tests
./mvnw verify     # полная проверка (SpotBugs, JaCoCo report)
```

## Качество и CI
- Checkstyle настроен с `failsOnError=true` — стиль ломает билд.
- JaCoCo проверяет покрытие (`minimum 0.80`), исключая репозитории Panache по причине bytecode-augmentation.
- SpotBugs запускается в `verify` (не всегда фейлит билд), но замечания стоит исправлять.

## Миграции и БД
- Миграции: `src/main/resources/db/migration/VNNN__desc.sql` или аналог в проекте; последовательность важна.
- Тестовая база: H2-миграции для тестов (см. `src/test/resources/db/migration`).

## Примеры подсказок (для агентов)
- "Use when: ресурсы, сервисы или репозитории в backend/ меняются. Проверки: checkstyle, тесты, миграции, OpenAPI."
- Пример запроса к агенту: "Проверь PR `feature/xyz` на соответствие java-service-standards: запусти Checkstyle, Unit tests, проверь отсутствие Javadoc, проверь EXPLAIN: комментарии, обновлён ли OpenAPI." 
- Быстрая команда для агента: "Generate request/response records and mapstruct mappers for new endpoint `FamilyResource#updateSettings` following project conventions."

## Неопределённые и вопросы
- `habbit runner` проект упомянут пользователем — нужно указать путь или репозиторий, чтобы извлечь из него дополнительные правила/шаблоны.

## Быстрая сводка (минимальная проверка)
- `./mvnw validate` — OK
- `./mvnw test` — OK
- Checkstyle: нет Javadoc/блок-комментариев; комментарии только `EXPLAIN:/FIXME:` — OK

---
Generated from: ARCHITECTURE.md, config/checkstyle.xml, pom.xml, /memories/repo/earnit-kids-backend.md, .agents/skills/project-patterns/SKILL.md

## Habbit Runner — Scan Summary
- Path scanned: `apps/backend` in `/Users/sash/Dev/Projects/habbit-runner`.
- Lombok declared version: `1.18.38` in `apps/backend/pom.xml` (dependency scope `provided`).
- `annotationProcessorPaths` include `lombok` and `lombok-mapstruct-binding`.
- Scan results:
	- No Lombok annotations found in `apps/backend/src/main/java`.
	- No usage of Java `record` types found.
- Recommendations:
	- If Lombok is intended: keep dependency & `annotationProcessorPaths`; add `lombok.config` with:
		```
		lombok.addLombokGeneratedAnnotation = true
		lombok.anyConstructor.addConstructorProperties = false
		```
	- If Lombok is unused: remove Lombok entries from `pom.xml` and `annotationProcessorPaths` to avoid unused tooling.
	- Avoid `@Data` on JPA entities — prefer explicit `@Getter`/`@Setter` or hand-written accessors; control equals/hashCode inclusion.
	- Add PR checklist items: run `./mvnw validate`, `./mvnw test`, include `lombok.config` when adding Lombok, document rationale in PR.
- Example commands:
```bash
cd /Users/sash/Dev/Projects/habbit-runner/apps/backend
./mvnw validate
./mvnw test
./mvnw verify
```

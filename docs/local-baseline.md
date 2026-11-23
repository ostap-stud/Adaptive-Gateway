# Схема PostgreSQL і збереження ручної конфігурації

Основна інструкція — [запуск через IDEA](operations.uk.md). Цей документ описує схему й перехід від БКР; історичний дамп **не є кроком нового встановлення**.

## Чиста інсталяція

Створіть порожню БД у pgAdmin або Database console IDEA, задайте її підключення в `config/local.properties`, запустіть Auth із local-профілем. `SchemaInitialization` перевіряє наявність `users.service`, створює DDL зі `ddl/000_empty_schema.sql` за потреби та виконує міграції за версіями `users.schema_migration`.

`000_empty_schema.sql` містить лише структуру базової моделі: таблиці, sequences, constraints та defaults. Він не імпортує історичних користувачів, паролів, business services або route даних. `LocalBootstrap` створює заданого адміністратора, `ROLE_INFRA_ADMIN`, service Auth та правила `/auth-service/**` і `/actuator/**`. Повторний запуск не скидає пароль або наявні service modes.

| Міграція | Призначення |
|---|---|
| 001_baseline_compatibility | Сумісність SQL-схеми з початковим кодом і журнал schema_migration |
| 002_adaptive_ownership | Management mode, ownership, active/override, версія та metadata service |
| 003_operation_policy | api_operation, access_policy, access_policy_role |
| 004_adaptation_event | Durable adaptation_event та source операції |
| 005_permissions_and_policy_audit | operation_permission, permission_role, policy_audit |

Скрипти транзакційні та повторно виконувані за номером міграції. Startup-схема серіалізована advisory lock. Необхідні права БД на створення схеми/таблиць. Auth слід запустити до Gateway, який читає нові columns.

## Таблиці та призначення

| Група | Таблиці | Використання |
|---|---|---|
| Сервіси й маршрути | service, route, route_role | Імена, mode/version, адреса документації, active/source/override, ручні маршрутні ролі |
| Параметри Gateway | filter, predicate, route_filter, route_predicate | Аргументи зберігаються в JSONB `args`; GENERATED ingress завжди використовує Authentication |
| Облікові записи | user_login, role, user_role | BCrypt/JWT login, центральні актуальні ролі |
| Операції | api_operation | Gateway/backend path, HTTP method, source, active |
| Політики | access_policy, access_policy_role | OPENAPI/DEFAULT/CENTRAL/ADMIN layers та role groups |
| Permissions | operation_permission, permission_role | Декларації сервісу та незалежне центральне зіставлення з ролями |
| Журнали | adaptation_event, policy_audit, schema_migration | Публікація desired state, аудит policies/mappings, версії схеми |

Усі таблиці розташовані в схемі `users`. Історичні таблиці груп збережено, але новий демонстраційний цикл їх не потребує. Effective policy обчислюється в Auth під час перевірки; окремої матеріалізованої таблиці кінцевих рішень немає.

## Наявна БД та ручні дані

Для чинної БД спочатку зробіть резервну копію та звірте її структуру: міграції розраховані на базову схему цього репозиторію. Auth не відновлює поверх неї дамп і не очищає дані. Конфлікти, наприклад дублікати service_name, зупиняють міграцію замість автоматичного видалення записів. `ddl/users.sql` і dev SQL залишені як історичні матеріали/засоби окремого тестового стенду.

Наявні сервіси та routes залишаються ручними. Це захищає адміністративні конфігурації від привласнення автоматикою. На чистому встановленні ця сумісність не вимагає створювати старі business routes: новий opt-in service створюється AUTOMATIC першим sync.

MANUAL/AUTOMATIC/DISABLED задає адміністратор. MANUAL зупиняє sync для сервісу; DISABLED виключає його з адаптації, але не знімає вже чинний route. Для припинення доступу потрібні active=false та refresh.

Ручна зміна GENERATED route виставляє `is_overridden`; автоматична синхронізація зберігає цей стан. ADMIN/CENTRAL policies і permission mapping мають окремий admin API, збереження під час sync та журнал причини змін. Для звичайної експлуатації редагувати SQL напряму не потрібно.

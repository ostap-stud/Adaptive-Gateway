# Backend API для адмін-панелі

Оновлення: додано `GET /admin/route-components` і `PUT /admin/adaptive/mode`; [опис ручного CRUD, Swagger UI та режимів](admin-panel-extension.uk.md).

Доповнення від **19.11.2026**. UI, нові технології та міграції БД не додаються. Збережені API зміни даних залишаються чинними.

## Доступ і підключення

Через локальний Gateway базова адреса — `http://localhost:8088/auth-service`; напряму до Auth — `http://localhost:8334/auth-service`. Отримайте JWT через наявний `POST /login`. Нові `/admin/**` вимагають чинний Bearer JWT користувача з поточною роллю **ROLE_INFRA_ADMIN** у БД. Відсутній JWT → 401, звичайний користувач → 403. Технічний ключ Manager не дає доступу до цих endpoints. Паролі та їхні хеші не повертаються.

Auth звертається до Manager серверним запитом. Local-профіль уже задає `adaptive.manager.base-url=http://localhost:8335`. Для іншого запуску задайте `ADAPTIVE_MANAGER_BASE_URL` і однаковий технічний ключ у `adaptive.sync-key` Auth та `manager.auth-key` Manager. Frontend користується лише своїм JWT. `adaptive.manager.timeout-ms` за замовчуванням 60000; великий цикл може тривати довше, ніж очікує клієнт. Після timeout перевірте останній результат перед повторним запуском.

## Нові endpoints читання

Шляхи в таблиці відносні до `/auth-service`.

| Метод і шлях | Відповідь |
|---|---|
| `GET /admin/services` | Усі зареєстровані сервіси, включно з MANUAL/DISABLED: `id`, `serviceName`, `managementMode`, `configurationVersion`, `openapiPath`, `openapiHash`, `lastSyncedAt`, наявні routes і збережені metadata. |
| `GET /admin/routes` | Усі routes, зокрема неактивні та internal: `id`, `serviceId`, `route`, `order`, `source`, `managedKey`, `active`, `isOverridden`, посилання на roles/filters/predicates. |
| `GET /admin/services/{name}/operations` | Активні й деактивовані operations, `permissions`, усі policy layers і `effectivePolicy`. Невідомий service → 404, відомий без operations → `[]`. |
| `GET /admin/users` | `id`, `login`, `login_desc`, `contact_id`, `roles` — масив повних назв ролей. |
| `GET /admin/roles` | Наявні ролі: `id`, `role`, `roleName`, `application`, `roleDesc`. |
| `GET /admin/permissions` | Декларації permissions та mappings, включно з невідомими/неналаштованими permissions. Кожен елемент: `permission`, `roles: []`, `operations: []` зі станом `active`. |
| `GET /admin/adaptive` | Останній snapshot Manager: `mode`, `registryStatus`, `observedAt`, `lastSuccessfulPollAt`, `observations`, `message`. Читання не запускає цикл. |

У `observations[]`: `name`, `status`, `observedAt`, `instanceId`, `candidate`, `warnings`, `auth`, `proposedRequest`, `message`, `sync`. `sync` містить `action`, `status`, `requestId`, `configurationVersion`, `message`; `auth.latestEvent` і `auth.pendingRefresh` — результат preflight перед синхронізацією, тому результат поточного циклу беріть із `sync`.

Стан доступності сервісу беріть із `/admin/adaptive`, зіставляючи `observations[].name` із `serviceName`. Поле БД `discoveryStatus`/`lastSeenAt` ще не оновлюється observation-циклом і не є live health check. Для сервісу, якого Manager ще не спостерігав, показуйте «ще немає спостереження». `ANALYZED` означає успішний аналіз специфікації, а не перевірку всіх бізнес-операцій. MISSING/NO_UP_INSTANCE/OBSERVATION_FAILED/STALE позначають недоступність або застаріле спостереження. Якщо Manager недоступний, Auth повертає 503; його HTTP-помилка → 502. Списки з БД продовжують працювати.

Масиви не мають пагінації — це API наявного невеликого стенду. Збережено стиль полів існуючих моделей: service/route — camelCase, operation та JDBC-проєкції — snake_case. `effectivePolicy` відповідає policy layers; `active=false` завжди окремо означає відсутність операції для авторизації, навіть якщо її історична policy була PUBLIC.

Приклад нового permission, для якого ще не створили mapping:

```json
[
  {
    "permission": "catalog.read",
    "roles": [],
    "operations": [
      {"id": 42, "service_name": "catalog", "gateway_path": "/catalog/items", "http_method": "GET", "active": true}
    ]
  }
]
```

Порожні `roles` означають відсутність mapping. Історичні permissions видалених operations залишаються видимими з `active=false`; mapping без поточного використання теж повертається. Старий `GET /policy/permissions` зберігає сумісність, але показує лише mappings і повертає `roles` як JSON-рядок. Для нового UI використовуйте `/admin/permissions`, де це масив.

## Ручний цикл

`POST /admin/adaptive/poll`, без body. Повертає snapshot після завершення циклу.

- **OBSERVE:** лише читання й побудова proposal; жодної синхронізації.
- **APPLY:** звичайний observation/synchronization із тими самими admission, management mode, override, optimistic version та refresh-retry правилами, що й плановий цикл.
- Поточний цикл уже працює → **409**, новий не запускається. Плановий запуск під час ручного пропускається.
- Запуск охоплює всі виявлені сервіси. Початковий `mode` береться з конфігурації Manager. Адміністратор може змінити його через `PUT /admin/adaptive/mode` до перезапуску процесу; перемикання не запускає цикл і повертає 409, якщо цикл зайнятий.

Прямі `GET /internal/admin/observations` та `POST /internal/admin/poll` Manager захищені `X-Adaptive-Key`; вони призначені для серверного виклику Auth. Старий `GET /observations` залишено сумісним. Останній observation зберігається в пам'яті: після перезапуску спочатку `NOT_POLLED`; журнал застосування конфігурацій у БД Auth зберігається.

## Наявні endpoints зміни даних

| Призначення | Метод і шлях |
|---|---|
| Створення користувача | `POST /signup` |
| Додавання/зняття ролей користувача | `POST /add-roles/{username}`, `DELETE /remove-roles/{username}`; body — масив повних назв ролей |
| Створення ролей | `POST /role/add`; body — масив RoleDTO |
| Створення/редагування/видалення service | `POST /service/add`, `PUT /service/{id}`, `DELETE /service/{id}` |
| Management mode сервісу | `PUT /service/{id}/management-mode`; `{"managementMode":"MANUAL"}` / `AUTOMATIC` / `DISABLED` |
| Route та його активність | `POST /service/route/add`, `PUT /service/route/{id}`, `DELETE /service/route/{id}`, `PUT /service/route/{id}/state` з `{"active":false}` |
| Permission → roles | `PUT /policy/permissions/{permission}` з `{"roles":["ROLE_CATALOG_READER"],"reason":"Причина"}`; порожні roles відкликають mapping |
| CENTRAL/ADMIN policy операції | `PUT /policy/operations/{id}/{source}` з `accessType`, `roles`, `forceOverride`, `reason`; `DELETE` за тим самим шляхом із `{"reason":"Причина"}` |
| Аудит policy/mapping | `GET /policy/audit` |

Сервіс не задає своїм permission центральну роль. Налаштування mapping і зміна ролей користувача вже впливають на перевірку без перевидання JWT. GENERATED routes захищені ручним override; не скидайте цей прапорець у UI автоматично. Наявні mutation endpoints використовують попередню route-based авторизацію Auth; новий `/admin/**` незалежно перевіряє ROLE_INFRA_ADMIN. Зміна route виконується за наявною процедурою публікації/refresh, цей API її не замінює.

## Перевірені сценарії зміни підключеного сервісу

| Сценарій | Результат і зміна |
|---|---|
| Додали operation | Уже було: новий semantic hash, новий GENERATED запис, існуючі ID збережені. |
| Змінили access/permission | Уже було: змінюється лише OPENAPI layer/operation permissions; ADMIN/CENTRAL і центральні mappings зберігаються. |
| Видалили частину operations | Уже було: відсутні GENERATED operations стають `active=false`, запит до них → 404 без legacy fallback; повернення відновлює ID. |
| Видалили останню operation | Виправлено: структурно валідний OpenAPI з явним порожнім `paths` тепер доходить до sync і деактивує останню operation. Ingress може залишитися активним, але без активних operations доступ через нього закритий. |
| HTTP-помилка, timeout, відсутній/невалідний OpenAPI | Уже було: не створюється порожній desired state, попередня конфігурація залишається. Відсутній `paths` — помилка; явний `paths: {}` у повному валідному документі — достовірний порожній стан. |
| Сервіс тимчасово зник із Eureka або DOWN | Уже було: MISSING/NO_UP_INSTANCE; жодних автоматичних видалень. Після повернення UP знову аналізується. |
| Eureka недоступна | Уже було: STALE, історичні candidates залишаються для перегляду, але proposal та sync прибираються. |

Grace period, автоматична деактивація зниклого сервісу та кілька UP-реплік не додані. Це збережені межі прототипу, не частина цього доопрацювання.

## Перевірка

**61 тест пройшов, failures/errors/skipped — 0:** Manager — 35; Auth — 26 (AdminApiTests, AdaptiveSyncTests, AdmissionPolicyTests, AuthSecurityBaselineTests, ManagerAdminClientTests). Інтеграційні тести Auth виконані на окремій тимчасовій PostgreSQL 18 зі стандартним bootstrap адміністративного route; робоча БД не використовувалась. Тимчасовий кластер зупинено й прибрано. ManagerAdminClientTests використовує справжній локальний HTTP-сервер.

Перевірено додавання/зміну/деактивацію/повернення operations зі стабільними ID; заміну permission; відмову для видаленої operation; валідний порожній OpenAPI в APPLY; збої й повернення підключеного сервісу без зайвих записів; адміністративні 401/403, відсутність паролів у відповіді, unresolved permissions, scoped technical key, 409 для паралельного ручного запуску та незмінність OBSERVE. [Портативний звіт](verification/predefense-2026-11-19.json).

Повторний запуск усіх модулів та повного acceptance-стенду 30.09.2026 не виконувався: Gateway/Eureka/demo не змінювалися в цьому доопрацюванні. Готові [запити IDEA HTTP Client](../requests/admin.http).

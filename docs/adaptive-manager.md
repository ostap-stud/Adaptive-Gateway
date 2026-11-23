# Adaptive Manager: Eureka, OpenAPI та автоматичне підключення

Поточне доповнення: `PUT /auth-service/admin/adaptive/mode` перемикає OBSERVE/APPLY для наступних циклів без restart. Admin JWT; активний poll → 409, неналаштований APPLY → 422. Зміна діє до перезапуску. [Докладніше](admin-panel-extension.uk.md).

Окремий модуль `adaptive-manager` читає Eureka, отримує OpenAPI безпосередньо з backend і формує desired configuration. Він не має JDBC або доступу до PostgreSQL. Запуск через IDEA описаний у [посібнику](operations.uk.md); стан останнього циклу доступний за **GET `http://localhost:8335/observations`**.

## Режими та налаштування

Основний профіль працює в OBSERVE. Збережена IDEA-конфігурація вмикає **local → APPLY/DISCOVERY**: сервіс із коректними metadata підключається без ручного створення запису в Auth.

| Властивість Spring | Основний профіль | Локальний профіль / призначення |
|---|---|---|
| manager.mode | OBSERVE | APPLY — аналіз і запис конфігурації |
| manager.admission-mode | ALLOWLIST | DISCOVERY — допуск усіх придатних opt-in сервісів |
| manager.allowed-services | Порожній набір | Для ALLOWLIST у APPLY потрібен явний перелік імен |
| manager.auth-base-url | Порожньо | `http://localhost:8334` |
| manager.auth-key | Порожньо | Береться з `ADAPTIVE_SYNC_KEY` у local.properties |
| manager.eureka-url | `http://localhost:8761/eureka/` | Довірений реєстр |
| manager.poll-delay-ms | 30000 | 5000; після завершення попереднього циклу |
| manager.initial-delay-ms | 1000 | Затримка першого циклу |
| manager.timeout-ms | 3000 | Таймаут HTTP-запиту разом із тілом |
| manager.max-response-bytes | 2097152 | Межа JSON-відповіді |
| manager.polling-enabled | true | false вимикає scheduler |
| server.address / server.port | 127.0.0.1 / 8335 | Адреса діагностичного API |

Властивості можна задавати в Environment variables Run configuration за правилами Spring, наприклад `MANAGER_MODE`, `MANAGER_ADMISSION_MODE`, `MANAGER_ALLOWED_SERVICES`, `MANAGER_AUTH_BASE_URL`, `MANAGER_AUTH_KEY`. У local використовується спільний приватний файл налаштувань. Auth key не передається до Eureka або backend і не відкриває admin API.

OBSERVE ніколи не виконує PUT/POST sync. Без Auth можна отримати candidate, але proposedRequest відсутній. Якщо Auth відповів NOT_REGISTERED, Manager може сформувати початковий запит з expectedVersion=0. У APPLY дозволений запит створить service в Auth. Для наявних MANUAL/DISABLED записів не буде.

## Контракт сервісу

Наприклад, для `catalog-service` потрібні metadata Eureka:

```yaml
eureka:
  instance:
    metadata-map:
      "[gateway.enabled]": "true"
      "[gateway.base-path]": "/catalog-service"
      "[gateway.openapi-path]": "/catalog-service/v3/api-docs"
```

Дужки у YAML зберігають крапки в ключах Spring map. На стороні Eureka ключі — `gateway.enabled`, `gateway.base-path`, `gateway.openapi-path`.

Менеджер нормалізує ім'я до нижнього регістру, відкидає інфраструктурні імена та потребує **одного UP-екземпляра**. `base-path` має бути `/<service-name>`, docs path — локальним шляхом у цьому namespace. Business paths у backend та OpenAPI вже містять цей префікс. Автоматичного переписування `/orders` у `/catalog-service/orders` немає.

Документ завантажується з hostName і ввімкненого порту UP-екземпляра. Увімкнений securePort використовує HTTPS зі стандартною перевіркою сертифіката. Redirects не виконуються; OpenAPI `servers`, homePageUrl та зовнішні `$ref` не використовуються як адреси завантаження. Eureka має бути довіреним реєстром.

## Аналіз доступу

Підтримано JSON OpenAPI 3.0.x/3.1.x, буквальні шляхи та `{parameter}`. Wildcard, regex, еквівалентні неоднозначні шаблони, path-level `$ref`, business paths поза namespace або непорожній `servers` base path відхиляють документ. Response/schema references не розгортаються.

Стандартний `security` операції заміщує глобальний. Відсутність обох дає AUTHENTICATED за консервативним правилом проєкту. `security: []` або допустима порожня OR-альтернатива дає PUBLIC. Підтримується HTTP bearer зі схемою JWT та порожніми scopes. Якщо серед OR-альтернатив є підтримувана JWT, приймається лише вона. OAuth, API keys, scopes і непідтримувані одночасні AND-вимоги блокуються, якщо немає підтримуваної альтернативи.

Найближче `x-access` шукається в operation → path → root:

| Декларація | Результат |
|---|---|
| `{"type":"PUBLIC"}` | PUBLIC, якщо немає явної стандартної JWT-вимоги |
| `{"type":"AUTHENTICATED"}` | Потрібний JWT |
| `{"type":"PERMISSION","permissions":["catalog.read"]}` | Потрібні JWT і всі заявлені permissions; mapping визначає Auth |
| `{"type":"INTERNAL"}` | BLOCKED на зовнішньому вході |
| Невідомий type, malformed permissions, зайві поля, `x-permissions` або `x-roles` | BLOCKED |

Непідтримуваний стандартний security не можна обійти через `x-access: PUBLIC`. Назви permissions відповідають `[a-zA-Z][a-zA-Z0-9_.:-]{0,127}`, максимум 100 на операцію. Сервіс не призначає центральні role names: невідомий mapping зберігається як декларація та блокує доступ до рішення адміністратора.

## Snapshot, hash та застосування

Candidate містить один ingress route і operations. Semantic hash враховує нормалізовані method/path/operationId/access/permissions, не залежить від форматування, опису, порядку paths, `info.version` або адреси екземпляра. requestId стабільний для сервісу, hash і expectedVersion.

Кожний цикл перечитує стан Auth. Новий hash або неопублікована версія веде до PUT snapshot. Актуальна pending/failed подія веде до retry тієї самої події. Уже підтверджений незмінний стан дає UNCHANGED без зайвих записів. 409 веде до CONFLICT і нового preflight у наступному циклі. Максимум один записувальний запит на сервіс за цикл.

Некоректний/відсутній документ, HTML, timeout, дублікати JSON-ключів або помилка HTTP не означають «видалити все»: попередня конфігурація зберігається. Пропущені в новому валідному snapshot GENERATED operations деактивуються; ручні дані не привласнюються. З 19.11.2026 валідний OpenAPI з явним `paths: {}` також є snapshot: він деактивує останню операцію. Відсутній або не об'єктний `paths` залишається помилкою. Manager має отримувати повну специфікацію сервісу.

## Спостереження та межі

`GET /observations` не запускає цикл, а повертає останній знімок із mode, часом та результатами:

Для адмін-панелі доступні захищені `GET /auth-service/admin/adaptive` і `POST /auth-service/admin/adaptive/poll` через Auth. Прямі технічні endpoints Manager `/internal/admin/observations` та `/internal/admin/poll` вимагають `X-Adaptive-Key`. Ручний цикл не змінює OBSERVE/APPLY; зайнятий Manager повертає 409. [Контракт API](admin-api.uk.md).

| Статус | Значення |
|---|---|
| ANALYZED / ANALYZED_WITH_BLOCKED_OPERATIONS | Побудовано candidate; у другому випадку є заблоковані операції |
| IGNORED | Інфраструктурний сервіс або немає opt-in |
| NO_UP_INSTANCE / MULTIPLE_INSTANCES | Немає UP або їх кілька; sync не виконується |
| INVALID_METADATA / OBSERVATION_FAILED | Непридатні metadata або OpenAPI |
| MISSING | Сервіс зник із реєстру; route автоматично не вимикається |
| STALE | Eureka недоступна; історичний candidate не застосовується |

Спостереження зберігаються в пам'яті; стан застосування відновлюється з durable-журналу Auth. Grace period, узгодження rolling update та вибір лідера Manager не реалізовані. За потреби адміністратор вимикає route або переводить service в MANUAL/DISABLED. Докази перевірки — [adaptive-cycle.md](adaptive-cycle.md).

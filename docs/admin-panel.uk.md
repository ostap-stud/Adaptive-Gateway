# Перевірка web admin panel

Це звіт початкової версії панелі. Актуальне доповнення — [ручний CRUD, Swagger UI та перемикання OBSERVE/APPLY](admin-panel-extension.uk.md).

Дата: **19.11.2026**. Frontend — окремий `admin-panel` на React + TypeScript + Vite. Зміни обмежені frontend, документацією й окремим інструментом тестового запуску. Backend-код та міграції для панелі не змінювалися.

## Запуск користувачем

Запустіть п'ять збережених local-конфігурацій IDEA у звичайному порядку. У `admin-panel` виконайте `npm ci`, потім `npm run dev`; адреса — `http://127.0.0.1:5173`. Логін і пароль адміністратора — з вашого приватного `config/local.properties`. Докладніше: [README frontend](../admin-panel/README.md).

## Межі перевірки

Production-збірка (`tsc -b && vite build`) успішна. 10 тестів Vitest перевіряють method/path comparison, additions/policy/permission changes/removals, останню операцію, manual ownership, неповні/stale дані, INTERNAL/BLOCKED, permissions та force override, а також розбір JWT session metadata.

Реальний стенд складався з PostgreSQL 18, Eureka, Auth, Gateway, demo service, Manager і Vite. Для перевірки створено окремий локальний кластер на `127.0.0.1:55489` і БД `adaptive_ui_qa`; приватні робочі credentials не використовувалися. `tools/start-ui-qa.mjs` — допоміжний інструмент цього ізольованого запуску, не заміна звичайних конфігурацій IDEA. Його демонстраційні test-only credentials не призначені для робочої БД.

## Перевірені сценарії

| Сценарій | Перевірка |
|---|---|
| Невірний пароль | Форма показує помилку, повторний вхід доступний |
| Вхід адміністратора | JWT отримано через frontend → Gateway → Auth; Dashboard має реальні дані |
| Services та details | Видимі AUTO/MANUAL, paths, OpenAPI, operations і останні стани |
| API Operations | Видимі PUBLIC/AUTHENTICATED/PERMISSION/INTERNAL-BLOCKED, окремий effective state; фільтр PUBLIC залишає одну demo operation |
| Routes | Видимі bootstrap MANUAL та GENERATED ingress, internal і active state, ролі |
| Створення ролі | Через форму створено `ROLE_DEMO_READER` |
| Створення користувача | Через форму створено окремого користувача з contact ID |
| Призначення ролі | Через UI призначено DEMO_READER; користувач має роль у відповіді Auth |
| Permission mapping | Через UI з причиною створено demo.read → ROLE_DEMO_READER; таблиця відображає новий mapping |
| Реальний доступ | Створений користувач отримує 200 від `/adaptive-demo/demo/restricted` через Gateway |
| Відкликання ролі | UI вимагає підтвердження; після зняття такий самий JWT отримує 403 без повторного login |
| Захист панелі | Звичайний JWT отримує 403, анонімний запит — 401 для admin API |
| Ручний APPLY | Підтвердження показує наслідки, backend виконує цикл, UI оновлює observation/sync і показує повідомлення |
| Недоступний Manager | Видимі error/stale state, ручна кнопка заблокована, GET inventory продовжують працювати |
| Ручний OBSERVE | UI показує відсутність запису; configuration versions усіх сервісів до/після циклу однакові |

Подальші результати адаптивного layout, production preview та завершення стенду зафіксовано у [машиночитаному звіті](verification/admin-panel-2026-11-19.json).

На desktop 1440 px і mobile 390 px перевірено layout, sidebar-навігацію та відсутність горизонтального виходу сторінки за екран. Широкі таблиці мають власне прокручування. Production deep-link reload зберігає сесію; logout і повторний reload повертають Login. JavaScript-помилок у production console не виявлено.

Після перевірки обидва frontend-сервери, п’ять тестових JVM і PostgreSQL зупинено; тимчасовий кластер прибрано. Робочу БД не використовували.

[Desktop-скріншот](verification/admin-panel-desktop-2026-11-19.jpg) · [Mobile-скріншот](verification/admin-panel-mobile-2026-11-19.jpg).

Постійна історія спостережень та зміна OBSERVE/APPLY через UI не додаються: цих контрактів немає в наявному backend. Панель показує останній snapshot і попередню подію Auth із preflight; поточний результат sync відображається окремо.

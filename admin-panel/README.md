# Adaptive Gateway Admin

Окремий frontend-модуль МКР: React, TypeScript, Vite, React Router, Lucide та власні CSS-компоненти. Ручні CRUD-дії використовують наявні API. Backend мінімально доповнено читанням параметрів маршрутів і захищеним перемиканням режиму Manager; схема БД не змінювалася.

## Запуск

Потрібні Node.js 22.12+ (перевірено на 24.15), npm та запущений локальний backend. Як і раніше, запустіть у IDEA Eureka → Auth → Gateway → demo service → Adaptive Manager з [local-профілями](../docs/operations.uk.md).

У терміналі каталогу `admin-panel`:

```sh
npm ci
npm run dev
```

Відкрийте [локальну панель](http://127.0.0.1:5173). Увійдіть адміністратором із `config/local.properties`: `BOOTSTRAP_ADMIN_LOGIN` / `BOOTSTRAP_ADMIN_PASSWORD`. Користувач повинен мати актуальну роль `ROLE_INFRA_ADMIN` у БД. Панель не містить готового пароля чи технічних ключів.

За замовчуванням `/auth-service/**` і `/swagger/**` проксіюються Vite до `http://127.0.0.1:8088`. За іншого порту скопіюйте `.env.example` в `.env.local` і змініть `GATEWAY_URL`, а також `VITE_GATEWAY_PUBLIC_URL` для адреси Swagger UI, потім перезапустіть Vite. Це серверна змінна, що не потрапляє до браузера. CORS-зміни backend не потрібні. `VITE_API_BASE` за замовчуванням `/auth-service`.

```sh
npm test
npm run build
npm run preview
```

`build` перевіряє TypeScript і створює `dist`; `preview` показує цю збірку на [порту 4173](http://127.0.0.1:4173) з тим самим локальним proxy. Для розгортання `dist` потрібен SPA fallback на `index.html` і reverse proxy для `/auth-service` та `/swagger` до Gateway. Vite dev/preview призначені для локальної роботи.

## Сторінки й дії

| Сторінка | Можливості |
|---|---|
| `/login` | JWT login, перевірка адміністративної ролі на сервері, пояснення помилок |
| `/` | Dashboard: сервіси, operations, routes, permissions, Manager, нерозв'язані permissions |
| `/services` | CRUD сервісів, AUTO/MANUAL/DISABLED, стан, base path, Swagger UI / OpenAPI, operations count, last observed/sync |
| `/services/:name` | Конфігурація й operations; CRUD маршрутів, їхніх ролей, filters/predicates з JSON-параметрами; management mode |
| `/operations` | Фільтри service/access, пошук path/permission, неактивні operations, declaration та effective policy, override/source; PUT/DELETE CENTRAL/ADMIN із reason/force підтвердженням |
| `/routes` | CRUD routes, source/override, active/internal, order, маршрутні ролі, filters/predicates; публікація Gateway |
| `/permissions` | Невідомі permissions і mappings; редагування ролей із причиною для аудиту; підтвердження відкликання; аудит policy/mapping |
| `/users` | Користувачі й ролі; створення ролі, призначення/відкликання ролей, підтвердження адмін-доступу |
| `/users/new`, `/register` | Створення користувача адміністратором через наявний `/signup`; login/password/contact ID/roles |
| `/adaptive` | Режим, registry, observation, candidate/current differences, результати sync, warnings/errors, ручний цикл і перемикання OBSERVE/APPLY з підтвердженням |

Публічної self-registration немає, оскільки чинний backend дозволяє `/signup` адміністратору. Призначення кількох ролей використовує існуючі окремі add/remove API; якщо друга дія не вдалася, панель повідомляє про можливі часткові зміни й перечитує дані.

## Важливі правила відображення

- JWT зберігається в `sessionStorage` лише цієї вкладки. Вихід, закінчення строку або серверний 401 завершують сесію. Claims використовуються для відображення login/expiry; право адміністрування перевіряє backend.
- Дані оновлюються кожні 15 секунд у видимій вкладці та кнопкою refresh. Це GET-запити, вони не запускають observation. Помилка одного ресурсу не приховує інші; попередні дані позначаються як потенційно застарілі.
- `ANALYZED` — стан аналізу, а не business health check. `discoveryStatus` з БД не використовується як поточний стан. Відсутність спостереження показується як `NOT_OBSERVED`.
- Backend нормалізує INTERNAL до BLOCKED, а PERMISSION до AUTHENTICATED + permissions. Панель не вигадує втрачений тип: показує `INTERNAL / BLOCKED` лише за відповідним warning і тим самим spec hash; інші блокування — `BLOCKED`. Effective policy показується окремо й враховує overrides/mappings.
- Відмінності candidate ↔ Auth обчислюються з двох наявних REST snapshots. Це поточне порівняння, а не постійна історія змін. Після APPLY різниця може бути вже нульовою. Неповні дані не трактуються як видалення всіх operations.
- Результат поточного sync і попередня подія Auth відображаються окремо. OBSERVE не представляє історичний APPLIED як результат нового запису. Зміна OBSERVE/APPLY використовує новий захищений API. Під час активного циклу повертається 409; APPLY потребує налаштованих Auth credentials та admission. Режим змінюється для поточного процесу до перезапуску.
- Ручний цикл охоплює всі допущені сервіси. APPLY вимагає підтвердження; 409 пояснюється як зайнятий цикл/конфлікт. При недоступному Manager кнопка недоступна. Після невизначеного результату слід оновити snapshot перед повтором.

Перелік контрактів: [admin-api.uk.md](../docs/admin-api.uk.md). Перевірки frontend та реального стенду: [admin-panel.uk.md](../docs/admin-panel.uk.md).

## Ручне керування та Swagger UI

Після CRUD-дій натисніть **Опублікувати маршрути**: це наявний `POST /actuator/gateway/refresh` із вашим admin JWT. Прийняття refresh не називається APPLIED без окремого підтвердження Gateway. Для GENERATED редагування/деактивація створює override, а видалення залишає inactive tombstone; MANUAL видаляється фізично.

Кнопка **Swagger UI** відкриває нативний Gateway WebFlux UI `/webjars/swagger-ui/index.html?urls.primaryName=<service>`. Сервіс уже вибраний; Try it out звертається до Gateway. Для захищених operations введіть свій JWT у Swagger Authorize; панель не передає JWT у URL. `VITE_GATEWAY_PUBLIC_URL` за замовчуванням `http://127.0.0.1:8088`; `/` означає спільний public origin.

Після цього оновлення **перезапустіть Auth і Adaptive Manager** для нових endpoints. Якщо Vite вже працює, перезапустіть його, щоб перечитати proxy configuration. [Перевірка розширення](../docs/admin-panel-extension.uk.md).


## Ручні маршрути, Base path і документація

- Base path / ingress показує активний зовнішній GENERATED маршрут з managed key ingress. Якщо такого маршруту немає, показано «Не визначено». Ручні маршрути перелічено окремо; перший створений маршрут не визначає namespace сервісу.
- Swagger/OpenAPI доступні для зареєстрованого Eureka service ID без observation, APPLY або активного бізнес-маршруту. Gateway використовує збережений openapi_path або /service-name/v3/api-docs і завантажує документ через Eureka. Сам сервіс та його OpenAPI мають бути доступними. Для ручних зовнішніх http(s) URL цей proxy не підтримується. Try it out використовує наявні маршрути та їхні політики доступу.
- /service-name/new-route входить до /service-name/**. Активний MANUAL маршрут у цьому просторі блокує автоматичну синхронізацію; вимкнений не заважає, повторне ввімкнення відновлює CONFLICT. Зміна management mode не перетворює ручні маршрути на GENERATED.
- CONFLICT не скасовує вже виконаних адміністративних змін і не видаляє раніше застосовану конфігурацію. Сервіс у MANUAL/DISABLED виключений із автоматичної синхронізації.
- Після оновлення Swagger-каталогу перезапустіть Gateway. Нових залежностей чи міграцій БД немає.

Перевірено 22.11.2026: 17 frontend-тестів, 20 інтеграційних Auth-тестів, 10 Gateway-тестів; frontend і backend збираються. На ізольованому стенді перевірено ручне створення сервісу та Swagger до першого observation, CONFLICT → вимкнення → APPLIED → повторне ввімкнення → CONFLICT зі збереженням IDs, версії та operations; PUBLIC повертає 200, AUTHENTICATED без JWT — 401. У браузері перевірено запуск циклу, Base path, повернення на початок сторінки й розміщення кнопок на вузькому екрані.

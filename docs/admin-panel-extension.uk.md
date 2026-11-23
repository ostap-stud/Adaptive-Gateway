# Ручне адміністрування, Swagger UI та режими Manager

Оновлення від **19.11.2026**. `/admin` — API читання і керування Manager, а не обмеження решти панелі. Основні ручні мутації виконуються через попередні контролери Auth.

## Що додано до панелі

- Створення, редагування і видалення сервісів; редагування опису AUTO та перейменування MANUAL. Перед перейменуванням AUTO потрібно явно змінити management mode.
- Створення/повне редагування/видалення routes, зміна active, service/internal, order, route roles.
- Додавання, редагування параметрів і зняття filters/predicates **конкретного маршруту**. Використовується повний DTO маршруту через наявний `PUT /service/route/{id}`; спільні компоненти інших routes не переписуються. Каталог містить варіанти параметрів; невідомі ID блокують збереження, щоб не стерти їх мовчки.
- Публікація ручної конфігурації через наявний `POST /actuator/gateway/refresh` з admin JWT. Ключ Gateway у frontend не передається.
- CENTRAL/ADMIN policy editor: access type, roles, force override, reason, видалення шару; перегляд наявного policy/mapping audit.
- Посилання на нативний Swagger UI Gateway з уже вибраним сервісом. JSON залишається додатковим посиланням. Для WebFlux фактичний UI доступний за `/webjars/swagger-ui/index.html`; перехід через `/swagger-ui.html` губить service query при redirect.

Видалення сервісу/MANUAL route потребує введення його назви/path. GENERATED route backend деактивує зі збереженням override. Збереження route і зміна доступу вимагають підтвердження. Список Swagger показує лише опубліковані активні routes підтримуваних service names; фактична наявність OpenAPI залежить від самого сервісу.

## Для чого два режими

| Режим | Поведінка |
|---|---|
| OBSERVE | Discovery + OpenAPI analysis + порівняння з Auth. Формує пропозицію, не записує автоматичну конфігурацію й не викликає refresh. Дає переглянути наслідки зміни сервісу перед застосуванням. |
| APPLY | Той самий аналіз, потім ідемпотентна синхронізація допущених AUTOMATIC сервісів і публікація в Gateway. Зберігає MANUAL/DISABLED та ручні override. |

Режим стосується **автоматичного циклу Manager**. Явні ручні адміністративні дії доступні в обох режимах.

На сторінці Adaptive Manager кнопка **Змінити режим** відкриває пояснення, вибір і підтвердження. Перемикання не виконує цикл саме по собі: новий режим використовують наступні scheduled/manual цикли. Поточні результати позначаються історичними, proposals і sync прибираються до нового спостереження.

Зміна режиму бере той самий lock, що й poll. Активний цикл → 409; він не переривається, а режим не змінюється посеред запису. APPLY без налаштованих Auth credentials або admission → 422. Зміна в пам'яті діє до перезапуску; стартовий `manager.mode` лишається джерелом режиму після restart. MANUAL/AUTOMATIC конкретного сервісу — окреме налаштування.

## Мінімальні доповнення backend

| API | Призначення / захист |
|---|---|
| `GET /auth-service/admin/route-components` | Каталог filters/predicates з `id`, `name`, `args`; ROLE_INFRA_ADMIN. Потрібний для безпечного редагування існуючих посилань routes. |
| `PUT /auth-service/admin/adaptive/mode` | Body `{"mode":"OBSERVE"}` або `APPLY`; ROLE_INFRA_ADMIN; серверний proxy до Manager. |
| `PUT /internal/admin/mode` Manager | Той самий body; технічний X-Adaptive-Key, блокування poll і перевірка APPLY prerequisites. |

Нових таблиць, бібліотек або технологій backend немає. Існуючі service/route/role/filter/predicate/policy mutations повторно не реалізовувалися.

## Як застосувати оновлення

1. Перезапустіть **Auth** та **Adaptive Manager** з тими самими local-профілями, щоб зареєструвати нові API. Інші backend-компоненти не змінювалися.
2. Якщо Vite уже працює, перезапустіть його після зміни proxy configuration. Звичайний запуск — `npm run dev` у `admin-panel`.
3. У локальній конфігурації `GATEWAY_URL` і `VITE_GATEWAY_PUBLIC_URL` за замовчуванням указують на Gateway `http://127.0.0.1:8088`. Для іншого публічного порту змініть обидві адреси у `.env.local`.

## Перевірка

Пройшли **37 тестів Manager**, **8 цільових тестів Auth**, **14 тестів frontend**; production build TypeScript/Vite успішний. Перевіряються lock, заборона небезпечного APPLY, зняття історичних proposals, авторизація mode API, relay HTTP 409/422, відновлення filters/predicates із ID, збереження параметрів, відмова при неповному каталозі та Swagger URL.

Реальний стенд запущено на окремих портах: frontend 5175; Gateway 18088; Eureka 18761; Auth 18334; Manager 18335; demo 18996. PostgreSQL 18 — окремий тимчасовий кластер 55489. Наявні користувацькі процеси на стандартних портах не зупинялися, робоча БД не використовувалася. `tools/start-ui-qa.mjs` тепер за замовчуванням додає port offset 10000, щоб не заважати звичайному запуску.

У браузері перевірено створення й редагування сервісу, route з Authentication/роллю/негативним order, створення/редагування/зняття AddResponseHeader та Method, валідацію JSON, active=false, підтвердження видалення. Реальний Gateway містив опублікований route з оновленими аргументами. Перемикання APPLY → OBSERVE → APPLY виконано через UI без restart; ручний OBSERVE не змінив configuration versions. CENTRAL BLOCKED через UI дав 403 на реальному public endpoint; зняття шару відновило 200 та відобразило аудит. Swagger UI автоматично вибрав adaptive-demo, а Try it out повернув 200 від Gateway.

Поточний машинний звіт та знімки збережено в `docs/verification/admin-extension-2026-11-19.json` і сусідніх JPG-файлах.

Після перевірки зупинено лише власний тестовий стенд і прибрано тимчасову БД. Видалення route/service перевірено через чинні REST DELETE на disposable fixtures; у UI окремо перевірено діалог підтвердження. [Звіт](verification/admin-extension-2026-11-19.json) · [Редактор маршруту](verification/admin-extension-route-editor.jpg) · [Режими](verification/admin-extension-mode.jpg) · [Swagger UI](verification/admin-extension-swagger.jpg).

# Відповідність реалізації концепту МКР

Дата звірки: **30.09.2026**. Локальні зміни поверх commit `8ceaca00d0a1632bb541b8c86b0d7eb8a2810f4b`; публікації на GitHub ще не було. Ця редакція замінює попередній аудит: описані в ньому відсутність автоматичної реєстрації, permission mapping, admin policy API та проксі документації тепер усунені.

## Висновок і межі твердження

**Три головні виправлення реалізовано й перевірено:** новий сервіс автоматично реєструється через Eureka/OpenAPI; доступ перевіряється за операційними політиками й централізованим зіставленням permissions → roles; документація сервісів доступна в Swagger UI шлюзу. Адміністратор може самостійно керувати інфраструктурою, а sync не затирає його override.

Новий сценарій починається з порожньої БД. Він не потребує історичних бізнес-даних, ручного створення demo service або SQL-редагування policies. Збережений ручний API БКР залишається інструментом адміністратора.

Це завершення цієї серії виправлень і працездатний мінімальний стенд. **Усі можливості ширшого концепту МКР ще не реалізовані**: окремо нижче перелічено lifecycle, багаторепліковість, ширші OpenAPI-контракти та експерименти. Проходження тестів не є доказом наявності цих можливостей.

## Джерела вимог

- «Технічна концепція МКР»: desired/effective state, відповідальність компонентів, `x-access`, permissions → roles, normal/force override, lifecycle та дослідження.
- «Проєктування реалізації МКР»: один routing route на сервіс, `ApiOperation`/`AccessPolicy`, `/validate`, журнал.
- Організаційний чат МКР: узгоджена специфікація та відокремлення реалізації від експериментів.
- БКР «Основа_Гриб_Остап_КН-413_БКР.pdf», базовий код і SQL-дамп: попередні модулі, таблиці, admin API, Eureka та Swagger.
- Уточнення користувача в поточній розмові: автоматична реєстрація, завершений permission-контроль, централізована документація, запуск через IDEA та збереження ручного адміністрування.

У питаннях route/operation застосовано пізнішу модель проєктування. DISABLED означає виключення з адаптації. Звичайні policy layers поєднуються обмежувально; явне послаблення можливе через ADMIN force override.

## Матриця відповідності

| Вимога | Стан | Реальна поведінка |
|---|---|---|
| Зберегти архітектуру БКР | Реалізовано | Залишено Gateway/Auth/Eureka/PostgreSQL, JDBC, JWT, ручний admin API. Manager — окремий модуль без доступу до БД. |
| Автоматичне підключення нового сервісу | Реалізовано в підтримуваному контракті | У APPLY/DISCOVERY Manager формує перший snapshot для NOT_REGISTERED; Auth транзакційно створює AUTOMATIC service, route та operations. Ручного POST service немає. Потрібні metadata opt-in і один UP instance. |
| Контроль допуску адміністратором | Реалізовано | DISCOVERY або ALLOWLIST; Auth auto-register можна вимкнути. Наявні MANUAL/DISABLED не переводяться в AUTOMATIC автоматично. |
| Один route на сервіс, окремі operations | Реалізовано | `/<service>/**`, `lb://<service>`, окремі method/path записи зі стабільними ID. |
| Desired state та ідемпотентність | Реалізовано | Повний snapshot, expectedVersion, requestId, semantic hash, транзакція, збереження ADMIN/CENTRAL і ручних route override. |
| `x-access` PUBLIC/AUTHENTICATED/PERMISSION/INTERNAL | Реалізовано | Найближче розширення operation/path/root; INTERNAL → BLOCKED; PERMISSION зберігає декларацію permissions. Непідтримувані вимоги не відкривають доступ. |
| Permissions → централізовані ролі | Реалізовано | `operation_permission` і `permission_role`; admin API mapping; актуальні ролі перевіряються в БД. Невідомий/порожній mapping блокує. Кілька permissions — AND, ролі одного mapping — OR. |
| Default AUTHENTICATED і fail closed | Реалізовано | Без декларації потрібен JWT; невідома вимога BLOCKED; невідома/неактивна GENERATED operation — 404. Збій отримання всього OpenAPI зберігає попередній стан. |
| ADMIN/CENTRAL/OPENAPI/DEFAULT | Реалізовано для окремої операції | DEFAULT лише за відсутності явних шарів. Звичайні шари посилюють вимоги. ADMIN force заміщує їх; його застосування/видалення аудитується. Загальні правила за шаблонами окремо не реалізовані. |
| Адміністрування policies | Реалізовано | GET operations з effectivePolicy; PUT/DELETE ADMIN/CENTRAL; PUT permissions; журнал actor/time/reason/before/after. Звичайний користувач не має доступу. |
| Ручна конфігурація інфраструктури | Збережено | CRUD service/route/filter/predicate/role; active state; MANUAL/AUTOMATIC/DISABLED. Ручна зміна GENERATED route захищена від sync. API скидання `is_overridden` поки немає. |
| Додавання/зміна/вилучення endpoint | Реалізовано в межах snapshot | Відсутні GENERATED operations деактивуються; повторна поява зберігає ID. Доповнення 19.11.2026: валідний OpenAPI з явним `paths: {}` деактивує і останню операцію; недоступний/невалідний документ зберігає конфігурацію. |
| Надійний refresh після commit | Реалізовано | Durable event, refresh реального Gateway, звірка IDs/версії; retry тієї самої події після перезапуску. APPLIED стосується одного Gateway. |
| Технічна автентифікація | Реалізовано | Окремі ключі Manager → Auth і Auth → Gateway. Refresh key обмежений POST refresh та GET routes; користувацький JWT для нього не потрібний. |
| Централізована документація | Реалізовано | Swagger UI має каталог опублікованих сервісів; `/swagger/services/{name}` проксіює docs; Try it out звертається до Gateway й проходить ті самі business policies. Це список специфікацій, не merged JSON. |
| Легкий запуск на чистій БД | Реалізовано | Кореневий Maven, п'ять IDEA Run configurations, спільний приватний файл налаштувань, автоматичні schema/migrations/bootstrap. Зовнішня PostgreSQL потрібна окремо. |
| Довільні backend paths і prefix rewrite | Частково | Backend paths уже мають містити `/<service>/`; автоматичні StripPrefix/RewritePath не генеруються. |
| Стандартний OpenAPI security | Частково | JSON OAS 3.0/3.1, JWT bearer, PUBLIC, conservative default. OAuth/OIDC/API keys/scopes, складні вимоги та повний resolver не підтримані. |
| Зникнення/повернення, grace period | Ще немає повного lifecycle | MISSING/NO_UP_INSTANCE/STALE — спостереження; автоматична деактивація після таймера відсутня. Повернений UP-сервіс аналізується знову. |
| Кілька UP instances / rolling update | Частково | Gateway має load balancing; Manager відмовляється узгоджувати кілька UP instances. Мінімальний стенд — одна репліка кожного сервісу. |
| Повна атомарність між вузлами | Не реалізовано | Auth читає policies з БД; Gateway має кеш routes. APPLIED не означає розподілену транзакцію чи працездатність backend. |
| Повний журнал усіх рішень | Частково | Є adaptation events і audit policy/mapping mutations. Не всі спостереження, відмови та старі CRUD-дії мають постійний аудит. |
| Метрики та порівняння БКР/МКР | Ще немає завершеного дослідження | Є тести та реальний acceptance-сценарій; немає серії порівняльних наукових вимірювань. |

## Як перевіряється безпека

Автоматичний маршрут завжди використовує `Authentication` filter. Auth спочатку визначає активну операцію за HTTP method і gateway path, потім обчислює effective policy. PUBLIC пропускає без JWT; AUTHENTICATED вимагає чинного токена наявного користувача; PERMISSION додатково перевіряє поточні ролі за mapping; INTERNAL блокується на зовнішньому вході.

Звичайний ADMIN PUBLIC не прибирає обмеження OPENAPI/PERMISSION. Для свідомого послаблення адміністратор передає `forceOverride:true` і причину. Видалення ADMIN layer повертає дію декларації. Новий sync не затирає ADMIN/CENTRAL. Permission mapping змінює рішення без перевидання JWT або OpenAPI.

MANUAL-маршрути мають налаштовуваний адміністратором `Authentication` filter та `route_role`. Це збережений спосіб ручного керування. Для GENERATED operation маршрутні ролі не підміняють policy. На чистій БД лише початкові адміністративні маршрути створюються вручну визначеним bootstrap; бізнес-сервіс підключається автоматично.

Прямий доступ до backend оминає Gateway: у мережевому розгортанні backends повинні бути доступні через внутрішню мережу. Локальні профілі прив'язані до loopback. Документація публічна, як у попередньому сценарії; вона не є дозволом виконувати захищені операції.

## Докази поточної перевірки

Коренева Maven-збірка: **87 тестів, 0 failures, 0 errors, 0 skipped**: Auth 44, Manager 30, Gateway 10, test-service 2, Eureka 1.

Реальний стенд із порожньою тимчасовою БД та HTTPS Gateway пройшов 28 записаних контрольних точок. У ньому перевірено автоматичне створення service, 6 operations і одного GENERATED route; 200/401/403/404; mapping і ролі; normal/force override та аудит; Swagger; стабільні ID; ручний active override; відмову Gateway та retry з відновленням тієї самої події. Bootstrap створив лише адміністративні MANUAL routes; історичний дамп не імпортувався.

Окремо запущено всі п'ять застосунків із параметрами local-профілів, спільним `config/local.properties` і документованими портами. Перевірені bootstrap, автоматичний APPLIED, public API, Swagger UI, docs proxy та захищений policy API через Gateway. Це перевірка конфігурацій JVM, а не автоматизація натискання кнопок у самій IDEA. Локальний профіль використовував HTTP; HTTPS перевірявся в окремому acceptance-стенді.

Портативні звіти: [HTTPS acceptance](verification/acceptance-2026-09-30.json), [локальні профілі IDEA](verification/idea-profiles-2026-09-30.json). Тестові процеси й створені ними БД прибрані. Докладніше — [adaptive-cycle.md](adaptive-cycle.md).

## Що не слід стверджувати у МКР

Не називайте повністю реалізованими grace period, узгодження різних OpenAPI реплік, універсальну підтримку OAuth або атомарне оновлення всіх вузлів. Не використовуйте гіпотетичні числа з обговорень як виміряні результати. Реалізовані й перевірені можливості вище можна описувати як поточний результат; решту слід відокремити як межі та майбутнє розширення.

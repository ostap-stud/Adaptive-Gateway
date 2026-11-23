# Перевірка робочого циклу та локального запуску

Актуально на **30.09.2026**. Інструкція користувача — [operations.uk.md](operations.uk.md); готові запити — [requests/demo.http](../requests/demo.http). Для запуску системи не потрібні допоміжні засоби, якими виконувалась автоматизована перевірка.

## Автоматизовані тести

Коренева Maven-збірка п'яти модулів завершилася успішно:

| Модуль | Тестів | Failures / errors / skipped |
|---|---:|---|
| Auth-Service | 44 | 0 / 0 / 0 |
| Adaptive Manager | 30 | 0 / 0 / 0 |
| Gateway | 10 | 0 / 0 / 0 |
| test-service | 2 | 0 / 0 / 0 |
| Eureka | 1 | 0 / 0 / 0 |
| **Разом** | **87** | **0 / 0 / 0** |

Серія охоплює зіставлення method/path, рішення доступу, ownership, транзакційний snapshot, версії/повтори, нове створення service, permission mapping і відкликання доступу, AND/OR role groups, normal/force override та аудит, збереження policies після sync, аналіз `x-access`, admission і відновлення refresh. Контекстні/інтеграційні тести Auth потребують окремої тестової PostgreSQL БД; її адресу й credentials можна задати у відповідній IDEA Run configuration. Не спрямовуйте ці тести на БД зі своїми даними.

## Реальний HTTPS acceptance-стенд

[Збережений звіт](verification/acceptance-2026-09-30.json) має статус PASSED і 28 контрольних точок. Це запуск справжніх Eureka, Auth, Gateway, demo backend і Manager, а не лише mocks. Використано порожню тимчасову PostgreSQL 18, JDK 21 та тимчасовий довірений localhost-сертифікат; перевірка TLS залишалася ввімкненою.

| Сценарій | Результат |
|---|---|
| Нова БД | Auth сам створив schema/migrations та початкового адміністратора |
| Новий opt-in service | Сам зареєстрований у APPLY/DISCOVERY; admin POST service не виконувався |
| GENERATED configuration | Один route, 6 operations, APPLIED і фактичний доступ до backend |
| PUBLIC | 200 без JWT |
| AUTHENTICATED | 401 без JWT, 200 із JWT |
| PERMISSION без mapping | 403 із JWT |
| Mapping відомий, але немає JWT/ролі | 401 / 403 |
| Mapping та роль призначені | 200 із тим самим JWT |
| INTERNAL / невідома operation | 403 / 404 |
| Normal ADMIN PUBLIC | Не послабив permission policy |
| Force ADMIN PUBLIC | Дозволив явний публічний виняток, записав аудит, пережив sync |
| Видалення ADMIN override | Повернуло declarative policy |
| Admin API | Звичайний користувач не може змінювати policies |
| Розділення технічних ключів | Manager key не дає права refresh Gateway |
| Swagger | UI та docs proxy працюють через Gateway; servers спрямовані на нього |
| Повторні цикли/перезапуск Manager | Немає зайвих подій і route/operation дублікатів |
| Ручний active=false/true | Override збережено, правильна версія опублікована, відповідно 404/200 |
| Зупинка Gateway та зміна OpenAPI | Новий стан у БД, FAILED_REFRESH |
| Відновлення Manager/Gateway | Retry тієї самої події → APPLIED; PUBLIC → AUTHENTICATED реально змінює доступ |

Початкові ручні маршрути в цій перевірці — bootstrap Auth/Actuator, без історичного дампа БКР. Остаточна версія сервісу — 8, записаних подій — 8; ці числа описують конкретний запуск, не є універсальною вимогою системи. БД і власні процеси тесту прибрані.

## Перевірка конфігурацій IDEA

[Окремий звіт local-профілів](verification/idea-profiles-2026-09-30.json) має статус PASSED. Застосунки запускалися з тими самими profiles, working directory і портами, що задані у `.run/`. DB/JWT/keys/bootstrap налаштування читалися лише зі спільного приватного файла, без дублювання їх у середовищі окремих процесів.

Перевірено автоматичну схему й bootstrap login на іншій порожній БД, появу public endpoint без ручної реєстрації, APPLIED, Swagger UI, docs proxy та admin operations API через Gateway. Цей профіль використовує **HTTP на loopback**. Перевірка не керувала графічним інтерфейсом IDEA; імпорт Maven та натискання Run залишаються описаними діями користувача.

## Як повторити демонстрацію без термінала

1. Виконайте [перший запуск у IDEA](operations.uk.md).
2. Відкрийте `requests/demo.http`, виберіть local і виконуйте запити окремо.
3. Переконайтеся, що автоматично створений service видно до будь-яких адміністративних змін. Перевірте PUBLIC, JWT та невідомий permission.
4. Створіть роль, mapping і призначте роль користувачу. Повторіть permission-запит із тим самим токеном.
5. За бажанням виконайте приклади normal/force, перегляньте audit, потім видаліть override.
6. Перегляньте Swagger UI і виберіть adaptive-demo. Try it out має звертатися до адреси Gateway.
7. За бажанням вимкніть route, виконайте refresh, перевірте 404; увімкніть його знову й повторіть refresh.

Допоміжний `tools/verify-adaptive-cycle.py` залишено для автоматизації розробником. Він створює власну випадково названу БД, запускає підготовлені jar, перевіряє HTTPS/failure recovery і прибирає свої ресурси у finally. Він не є складовою запуску через IDEA та не потрібний користувачу системи.

Наявність перевіреного циклу не означає підтримки grace period, узгодження кількох UP-реплік або завершених порівняльних експериментів. Повна [матриця відповідності](concept-compliance.uk.md) відокремлює ці межі.

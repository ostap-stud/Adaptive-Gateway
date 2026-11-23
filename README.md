# Adaptive Gateway — прототип МКР

Збережена архітектура БКР: Gateway, Auth-Service, Eureka та PostgreSQL. Adaptive Manager автоматично підключає сервіси з Eureka/OpenAPI, а Auth централізовано керує доступом на рівні операцій. Адміністратор зберігає ручне керування маршрутами та політиками.

## Запуск в IntelliJ IDEA

1. Відкрийте кореневий `pom.xml`, імпортуйте Maven-проєкт і виберіть **JDK 21**.
2. Створіть порожню PostgreSQL БД `adaptive_gateway` через pgAdmin або Database console IDEA.
3. Скопіюйте `config/local.properties.example` у `config/local.properties`. Укажіть підключення до БД та власні логін/пароль адміністратора. Для трьох ключів згенеруйте окремі значення за інструкцією в docs/operations.uk.md.
4. Послідовно запустіть збережені Run configurations: **1 - Eureka → 2 - Auth → 3 - Gateway → 4 - Demo service → 5 - Adaptive Manager**. Дочекайтеся запуску Auth перед Gateway.
5. Відкрийте [Swagger UI](http://localhost:8088/swagger-ui.html) або [публічний demo API](http://localhost:8088/adaptive-demo/demo/public) після першої синхронізації.

Auth сам створить таблиці та першого адміністратора. Manager сам зареєструє `adaptive-demo`, його маршрут і операції. Імпорт історичного дампа, ручна реєстрація demo та команди термінала для цього не потрібні. Локальні профілі використовують HTTP лише на loopback; PostgreSQL запускається окремо.

- **[Повна інструкція запуску, роботи й адміністрування](docs/operations.uk.md)**.
- **[Готові запити IDEA HTTP Client](requests/demo.http)** — адреси, методи й JSON; виконуйте окремими запитами. Секрети задаються у копії `http-client.private.env.json.example` без `.example`.
- **[API для майбутньої адмін-панелі та перевірка змін сервісу](docs/admin-api.uk.md)** — доповнення від 19.11.2026; готові запити в `requests/admin.http`.
- **[Web admin panel](admin-panel/README.md)** — React + TypeScript + Vite, запуск на `http://127.0.0.1:5173`; [перевірка з реальним backend](docs/admin-panel.uk.md).
- [Що реалізовано та які межі залишилися](docs/concept-compliance.uk.md).
- [Manager та OpenAPI](docs/adaptive-manager.md), [протокол синхронізації](docs/adaptive-sync.md), [результати перевірок](docs/adaptive-cycle.md), [схема та міграції](docs/local-baseline.md).

Стан на **30.09.2026**: реалізовані автоматична реєстрація, `x-access`, permissions → roles, адміністративні normal/force policies з аудитом, централізований Swagger і локальні конфігурації IDEA. Пройшли 87 автоматизованих тестів та два реальні сценарії запуску. Це мінімальний працездатний стенд; повний lifecycle з grace period, узгодження кількох UP-екземплярів і дослідницькі вимірювання ще не реалізовані.

Опис стосується локальних змін поверх `8ceaca00d0a1632bb541b8c86b0d7eb8a2810f4b`. На момент перевірки ці зміни ще не опубліковані на GitHub.

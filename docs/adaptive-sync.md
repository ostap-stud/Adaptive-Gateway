# Контракт синхронізації Manager → Auth → Gateway

Протокол передає повний бажаний стан одного сервісу. Для звичайного користування його викликає Adaptive Manager; ручні приклади адміністративних дій наведені в [operations.uk.md](operations.uk.md) і [requests/demo.http](../requests/demo.http).

## Автентифікація та адреси

Технічний base path: `/auth-service/internal/adaptive/services/{name}`. Заголовок **`X-Adaptive-Key`** має відповідати `adaptive.sync-key` Auth (не менш як 32 байти). Звичайний admin JWT не замінює цей ключ; ключ не надає доступу до admin API.

| Метод і відносний шлях | Призначення |
|---|---|
| GET `/configuration` | Поточні service, routes, operations, layers, latestEvent і pendingRefresh |
| PUT `/configuration` | Повний desired snapshot; може створити ще не зареєстрований service |
| POST `/events/{requestId}/refresh` | Повторити публікацію вже збереженої події |

GET повертає DB-поля у snake_case: версія — `service.configuration_version`. Для відсутнього сервісу повертається 404; Manager трактує це як NOT_REGISTERED і пропонує expectedVersion=0. Для наявного сервісу береться прочитана версія. PUT використовує camelCase.

## Приклад початкового snapshot

**PUT** `http://localhost:8334/auth-service/internal/adaptive/services/catalog-service/configuration`, заголовки `X-Adaptive-Key: <технічний ключ>` і `Content-Type: application/json`:

```json
{
  "requestId": "df1a1dd1-34e5-4b80-9c57-1f55f38ebaa1",
  "expectedVersion": 0,
  "specHash": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
  "openapiPath": "/catalog-service/v3/api-docs",
  "route": {"path": "/catalog-service/**", "order": 0},
  "operations": [
    {
      "gatewayPath": "/catalog-service/public",
      "backendPath": "/catalog-service/public",
      "httpMethod": "GET",
      "access": "PUBLIC"
    },
    {
      "gatewayPath": "/catalog-service/orders",
      "backendPath": "/catalog-service/orders",
      "httpMethod": "GET",
      "access": "AUTHENTICATED",
      "permissions": ["catalog.read"]
    }
  ]
}
```

Hash тут демонстраційний: у реальному циклі його обчислює Manager. Permissions передаються з AUTHENTICATED та без roles; їх перетворює на поточні role groups Auth. Технічний DTO також підтримує ROLES з непорожнім списком наявних ролей, але Manager використовує декларативні permissions, а не призначає ролі сам.

`adaptive.auto-register=true` дозволяє створення відсутнього service. Валідація snapshot виконується перед створенням, service і його конфігурація комітяться разом. Наявний MANUAL/DISABLED ніколи не перетворюється в AUTOMATIC через цей endpoint. За `auto-register=false` адміністратор може створювати та вмикати сервіси сам.

## Власність, конкурентність та повтори

- Один GENERATED ingress route на service: `managed_key=ingress`; фільтр Authentication забезпечує operation authorization.
- Операція визначається gateway path і HTTP method; відсутні GENERATED entries деактивуються, повторна поява зберігає ID.
- Ручні service/route/operation записи не привласнюються. Перетини та несумісні конфігурації відхиляються.
- Ручний override route і ADMIN/CENTRAL layers не перезаписуються. Sync володіє GENERATED operations, OPENAPI layer та permission declarations.
- Запис серіалізований спільним advisory lock і перевіркою expectedVersion. Застаріла версія — 409; некоректний snapshot — 422.
- Повтор requestId з ідентичним канонічним вмістом повертає попередню подію; інший payload під тим самим ID — конфлікт. Навіть однаковий specHash не дозволяє підмінити інший snapshot.
- Новий requestId із незмінним станом не створює дублікати routes/operations. Неопублікована адміністративна версія все одно потребує refresh.

`operation_permission` зберігає declarations, а `permission_role` — незалежний центральний mapping. Невідомий permission не відхиляє весь service: його операція залишається заблокованою до конфігурації mapping. Оновлення mapping/admin policies збільшує service version та одразу впливає на рішення Auth.

## Публікація в Gateway

Auth спершу завершує транзакцію конфігурації й durable event, потім виконує HTTP. HTTP 200 sync не означає APPLIED: клієнт перевіряє поле `status` і `error`.

| Статус | Значення |
|---|---|
| PENDING_REFRESH | Дані збережені, але потрібна конфігурація доступу/очікування підтвердження кешу |
| FAILED_REFRESH | HTTP-помилка, timeout або недоступність Gateway; БД не відкочується |
| APPLIED | Кеш одного Gateway має очікуваний набір route IDs та configurationVersion |
| NO_CHANGE | Snapshot не змінив стан і нова публікація не потрібна |
| SUPERSEDED | Подію замінила новіша версія або режим service |

Auth виконує POST `/actuator/gateway/refresh`, потім GET `/actuator/gateway/routes`. Самого успішного POST недостатньо: Actuator оновлює кеш асинхронно. Невідповідний snapshot веде до повтору в наступному циклі Manager. Після перезапуску Manager знаходить незавершену подію в Auth і продовжує її.

Локальні профілі використовують окремий **GATEWAY_REFRESH_KEY**: у Auth це `adaptive.gateway.key`, у Gateway — `gateway.refresh-key`. Він передається через `X-Gateway-Key` і дозволяє лише POST refresh / GET routes. Адміністратор може викликати ці endpoints своїм JWT. Збережений сумісний fallback `adaptive.gateway.bearer-token` не потрібен для нового локального встановлення.

| Властивість Auth | Значення |
|---|---|
| adaptive.sync-key | Ключ Manager; local бере ADAPTIVE_SYNC_KEY |
| adaptive.auto-register | true за замовчуванням; false забороняє автоматичне створення service |
| adaptive.gateway.base-url | local: `http://localhost:8088` |
| adaptive.gateway.key | local: GATEWAY_REFRESH_KEY |
| adaptive.gateway.timeout-ms | 3000 за замовчуванням |

Для HTTPS JVM Auth має довіряти сертифікату Gateway; перевірка TLS не вимикається. Публікація internal routes лишається окремим кешем Gateway. Policy-рішення читаються з БД одразу, тому APPLIED не є глобальною транзакцією чи перевіркою backend availability. Сценарій відновлення перевірено на справжніх процесах: [результати](adaptive-cycle.md).

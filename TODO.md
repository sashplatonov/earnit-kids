- обнови джава i18n по стандартну для джава кваркус проектам
- # Интернационализация в Java + Quarkus

## Как это работает в стандартном Java

### 1. ResourceBundle — базовый механизм

```
src/main/resources/
├── messages.properties          ← английский (дефолт)
├── messages_ru.properties       ← русский
└── messages_en.properties       ← английский явный
```

```properties
# messages.properties (английский дефолт)
auth.invalidPassword=Invalid password
auth.userNotFound=User not found
shop.insufficientFunds=Insufficient coins
shop.purchase.success=Purchase successful
```

```properties
# messages_ru.properties
auth.invalidPassword=Неверный пароль
auth.userNotFound=Пользователь не найден
shop.insufficientFunds=Недостаточно монет
shop.purchase.success=Покупка выполнена успешно
```

```java
// Базовое использование
Locale locale = Locale.forLanguageTag("ru");
ResourceBundle bundle = ResourceBundle.getBundle("messages", locale);
String message = bundle.getString("auth.invalidPassword");
// → "Неверный пароль"
```

---

### 2. MessageFormat — плюрализация и переменные

```properties
# messages.properties
coins.balance=You have {0} {0,choice,1#coin|2#coins}
history.earned=Earned {0} coins on {1,date,medium}

# messages_ru.properties  
coins.balance=У вас {0} монет
history.earned=Получено {0} монет {1,date,medium}
```

```java
MessageFormat fmt = new MessageFormat(
    bundle.getString("coins.balance"),
    locale
);
String result = fmt.format(new Object[]{ 5 });
```

> ⚠️ **Проблема:** стандартный `MessageFormat` не поддерживает русские формы плюрализации
> (`one`, `few`, `many`). Для этого нужен ICU4J.

---

## Quarkus-специфичный подход

### 1. Quarkus Localization через `@MessageBundle`

Quarkus предоставляет типобезопасный способ через расширение `quarkus-qute` (шаблонизатор) и `quarkus-localization`.

```xml
<!-- pom.xml -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-qute</artifactId>
</dependency>
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-localization</artifactId>
</dependency>
```

```
src/main/resources/
├── messages/
│   ├── msg.properties        ← дефолт (английский)
│   └── msg_ru.properties     ← русский
```

```java
// Определяем интерфейс
@MessageBundle
public interface AppMessages {

    @Message("Invalid password")
    String authInvalidPassword();

    @Message("User not found")
    String authUserNotFound();

    @Message("You have {count} coins")
    String coinsBalance(int count);
}
```

```properties
# msg_ru.properties
authInvalidPassword=Неверный пароль
authUserNotFound=Пользователь не найден
coinsBalance=У вас {count} монет
```

```java
// Использование в сервисе
@ApplicationScoped
public class AuthServiceImpl {

    // Quarkus инжектирует нужную локаль автоматически
    AppMessages messages;

    public OperationResult login(String password) {
        if (!valid) {
            return OperationResult.failure(
                messages.authInvalidPassword()
            );
        }
    }
}
```

---

### 2. Передача локали из запроса — реальная архитектура

```
SvelteKit frontend
    │
    │  Accept-Language: ru
    │  X-App-Locale: ru
    ▼
Quarkus JAX-RS Resource
    │
    │  resolveLocale(headers)
    ▼
LocaleContext (CDI RequestScoped)
    │
    ▼
Service → MessageResolver → messages_ru.properties
```

#### Шаг 1 — CDI бин для хранения локали запроса

```java
@RequestScoped
public class LocaleContext {

    private Locale locale = Locale.ENGLISH; // дефолт

    public Locale getLocale() {
        return locale;
    }

    public void setLocale(Locale locale) {
        this.locale = locale;
    }
}
```

#### Шаг 2 — JAX-RS фильтр читает заголовок и пишет в контекст

```java
@Provider
@Priority(Priorities.AUTHENTICATION - 10) // до auth фильтра
public class LocaleResolutionFilter implements ContainerRequestFilter {

    private static final Set<String> SUPPORTED = Set.of("en", "ru");

    @Inject
    LocaleContext localeContext;

    @Override
    public void filter(ContainerRequestContext ctx) {

        Locale resolved = resolveLocale(ctx);
        localeContext.setLocale(resolved);
    }

    private Locale resolveLocale(ContainerRequestContext ctx) {

        // 1. Явный заголовок от SvelteKit прокси
        String appLocale = ctx.getHeaderString("X-App-Locale");
        if (appLocale != null && SUPPORTED.contains(appLocale)) {
            return Locale.forLanguageTag(appLocale);
        }

        // 2. Accept-Language
        List<Locale.LanguageRange> ranges =
            Locale.LanguageRange.parse(
                ctx.getHeaderString(HttpHeaders.ACCEPT_LANGUAGE)
            );
        Locale best = Locale.lookup(ranges,
            SUPPORTED.stream()
                .map(Locale::forLanguageTag)
                .toList()
        );

        // 3. Фолбэк
        return best != null ? best : Locale.ENGLISH;
    }
}
```

#### Шаг 3 — MessageResolver использует локаль из контекста

```java
@ApplicationScoped
public class MessageResolver {

    @Inject
    LocaleContext localeContext;

    private final Map<Locale, ResourceBundle> cache =
        new ConcurrentHashMap<>();

    public String get(String key) {
        Locale locale = localeContext.getLocale();
        ResourceBundle bundle = cache.computeIfAbsent(
            locale,
            l -> ResourceBundle.getBundle("messages", l)
        );
        // Фолбэк к английскому если ключ отсутствует
        if (!bundle.containsKey(key)) {
            bundle = ResourceBundle.getBundle(
                "messages", Locale.ENGLISH
            );
        }
        return bundle.getString(key);
    }

    public String get(String key, Object... args) {
        String pattern = get(key);
        return MessageFormat.format(pattern, args);
    }
}
```

#### Шаг 4 — Сервис использует резолвер

```java
@ApplicationScoped
public class AuthServiceImpl {

    @Inject
    MessageResolver msg;

    public OperationResult login(LoginRequest req) {
        if (!passwordValid(req)) {
            return OperationResult.failure(
                msg.get("auth.invalidPassword")
            );
        }
        if (!userExists(req)) {
            return OperationResult.failure(
                msg.get("auth.userNotFound")
            );
        }
        return OperationResult.success();
    }
}
```

---

### 3. ICU4J для русской плюрализации монет

Стандартный `MessageFormat` не умеет `one/few/many`. Подключаем ICU4J:

```xml
<!-- pom.xml -->
<dependency>
    <groupId>com.ibm.icu</groupId>
    <artifactId>icu4j</artifactId>
    <version>74.2</version>
</dependency>
```

```properties
# messages.properties
coins.count={count, plural,
    one   {# coin}
    other {# coins}
}

# messages_ru.properties
coins.count={count, plural,
    one   {# монета}
    few   {# монеты}
    many  {# монет}
    other {# монеты}
}
```

```java
@ApplicationScoped
public class CoinFormatter {

    @Inject
    LocaleContext localeContext;

    @Inject
    MessageResolver msg;

    public String formatCoins(int count) {
        Locale locale = localeContext.getLocale();
        String pattern = msg.get("coins.count");

        // ICU MessageFormat — не java.text.MessageFormat
        com.ibm.icu.text.MessageFormat fmt =
            new com.ibm.icu.text.MessageFormat(pattern, locale);

        return fmt.format(Map.of("count", count));
    }
}
```

```
formatCoins(1)  → "1 монета"   / "1 coin"
formatCoins(3)  → "3 монеты"   / "3 coins"
formatCoins(5)  → "5 монет"    / "5 coins"
formatCoins(21) → "21 монета"  / "21 coins"
```

---

### 4. Стабильные коды ошибок — не переводить в API-контракте

```java
// Плохо — фронтенд зависит от текста
public record ErrorResponse(String message) {
    public static ErrorResponse of(String message) {
        return new ErrorResponse(message);
    }
}
// { "message": "Неверный пароль" }

// Хорошо — стабильный код + локализованное сообщение
public record ErrorResponse(String code, String message) {
    public static ErrorResponse of(String code, String localizedMsg) {
        return new ErrorResponse(code, localizedMsg);
    }
}
// { "code": "AUTH_INVALID_PASSWORD", "message": "Неверный пароль" }
```

```java
public enum ErrorCode {
    AUTH_INVALID_PASSWORD,
    AUTH_USER_NOT_FOUND,
    SHOP_INSUFFICIENT_FUNDS,
    SHOP_ITEM_NOT_FOUND,
    FAMILY_LIMIT_EXCEEDED,
    VALIDATION_FAILED;

    public String messageKey() {
        // AUTH_INVALID_PASSWORD → "auth.invalidPassword"
        return CaseFormat.UPPER_UNDERSCORE
            .to(CaseFormat.LOWER_CAMEL, name().toLowerCase());
    }
}
```

```java
// Сервис
return OperationResult.failure(
    ErrorCode.AUTH_INVALID_PASSWORD,
    msg.get(ErrorCode.AUTH_INVALID_PASSWORD.messageKey())
);
```

---

### 5. Async-контекст — email и уведомления

В async-методах `@RequestScoped` CDI бин недоступен. Локаль нужно захватить явно:

```java
@ApplicationScoped
public class NotificationService {

    @Inject
    MessageResolver msg;

    // ❌ Плохо — async теряет RequestScoped локаль
    @Async
    public void sendPurchaseNotification(User user) {
        String text = msg.get("shop.purchase.success");
        // msg использует LocaleContext который уже мёртв
    }

    // ✅ Хорошо — передаём локаль явно в момент события
    @Async
    public void sendPurchaseNotification(
        User user,
        Locale capturedLocale  // захвачено в момент запроса
    ) {
        ResourceBundle bundle =
            ResourceBundle.getBundle("messages", capturedLocale);
        String text = bundle.getString("shop.purchase.success");
        emailSender.send(user.email(), text);
    }
}
```

```java
// В Resource захватываем локаль до передачи в async
@POST
@Path("/shop/purchase")
public Response purchase(@Context ContainerRequestContext ctx) {

    Locale locale = localeContext.getLocale(); // пока RequestScoped жив

    purchaseService.process(item, user);
    notificationService.sendPurchaseNotification(user, locale); // передаём

    return Response.ok().build();
}
```

---

### 6. Валидация — Bean Validation с локализацией

```properties
# ValidationMessages.properties
javax.validation.constraints.NotNull.message=Field is required
javax.validation.constraints.Size.message=Must be between {min} and {max} characters

# ValidationMessages_ru.properties
javax.validation.constraints.NotNull.message=Поле обязательно для заполнения
javax.validation.constraints.Size.message=Должно быть от {min} до {max} символов
```

```java
@Provider
public class ConstraintViolationExceptionMapper
    implements ExceptionMapper<ConstraintViolationException> {

    @Inject
    LocaleContext localeContext;

    @Override
    public Response toResponse(ConstraintViolationException ex) {
        Locale locale = localeContext.getLocale();

        List<String> errors = ex.getConstraintViolations()
            .stream()
            .map(cv -> interpolate(cv, locale))
            .toList();

        return Response
            .status(Response.Status.BAD_REQUEST)
            .entity(Map.of(
                "code", "VALIDATION_FAILED",
                "errors", errors
            ))
            .build();
    }

    private String interpolate(
        ConstraintViolation<?> cv,
        Locale locale
    ) {
        // Берём локализованное сообщение из ValidationMessages_{locale}
        return cv.getMessage(); // Bean Validation сам подхватит локаль
                                // если настроить MessageInterpolator
    }
}
```

---

## Итоговая архитектура потока локали

```
HTTP Request
│  Headers:
│    Accept-Language: ru
│    X-App-Locale: ru
│
▼
LocaleResolutionFilter        ← читает заголовки
│  localeContext.setLocale(ru)
▼
LocaleContext @RequestScoped  ← хранит локаль запроса
│
├── MessageResolver           ← читает локаль, грузит bundle
│     └── messages_ru.properties
│
├── CoinFormatter             ← ICU4J плюрализация
│     └── coins.count pattern с one/few/many
│
├── AuthServiceImpl           ← получает текст из MessageResolver
├── FamilyServiceImpl
├── FamilyActionServiceImpl
└── ConstraintViolationMapper ← валидация с локалью
```

---

## Быстрая сводка

| Задача | Инструмент |
|---|---|
| Хранение строк | `ResourceBundle` + `.properties` файлы |
| Типобезопасные сообщения | Quarkus `@MessageBundle` + Qute |
| Русская плюрализация | ICU4J `MessageFormat` |
| Передача локали по запросу | `ContainerRequestFilter` + `@RequestScoped` CDI |
| Async email и уведомления | Явная передача `Locale` в момент события |
| Стабильный API-контракт | `ErrorCode` enum + `{ code, message }` |
| Валидация | `ValidationMessages_ru.properties` + `MessageInterpolator` |
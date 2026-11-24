# Link Shortener

Консольный сервис для сокращения ссылок.  

## Запуск

Из каталога, где находится `pom.xml`:  
```powershell
mvn clean package
java -cp target/classes link_shortener.Main
```

## Зависимости  
- JDK 21  
- Maven  

## Команды

```
create <URL> <limit>                Shorten a URL
open <short-link-or-code>           Open the original URL
list                                Show your links
limit <short-link-or-code> <limit>  Change the visit limit for one of your links
delete <short-link-or-code>         Delete one of your links
use <UUID>                          Resume with an existing UUID
new                                 Start a new user session
help                                Show this help
exit                                Quit

Example: create https://youtu.be/eOrb93UZfPU?si=sk9wBjP2jpc5Yrt2 1
```

## Примеры использования

### Создать ссылку и перейти по ней

```text
> create https://www.opennet.ru/ 10
Your UUID is 530a8cae-e2b9-442c-a0e6-13f9981484a2
Save it and type use <UUID> the next time you start the app.
Short link — https://short.test/9d0JBg
> open https://short.test/9d0JBg
Sent the URL to the browser.
```

### Вернуться к своим ссылкам

```text
> use 530a8cae-e2b9-442c-a0e6-13f9981484a2
Current UUID — 530a8cae-e2b9-442c-a0e6-13f9981484a2
> list
https://short.test/9d0JBg → https://www.opennet.ru/
  active, 1/10 visits, expires at 2026-11-24T07:51:11.757202200Z
```

### Поменять лимит ссылки  

```text
> limit https://short.test/9d0JBg 20
Visit limit updated.
> list
https://short.test/9d0JBg → https://www.opennet.ru/
  active, 1/20 visits, expires at 2026-11-24T07:51:11.757202200Z
```

## Настройки

Cервис читает базовую конфигурацию из файла
[application.properties](src/main/resources/application.properties).  
В нём описаны следующие параметры:

| Параметр | Значение по умолчанию | Назначение |
| --- | --- | --- |
| `short-link.base-url` | `https://short.test/` | Префикс коротких адресов, которые показывает и принимает CLI. |
| `short-link.ttl` | `PT5M` | Срок действия новой ссылки. По умолчанию 5 минут. |
| `short-link.cleanup-interval` | `PT1M` | Интервал между очистками. По умолчанию 1 минута. |
| `short-link.code-length` | `6` | Длина генерируемого кода. |
| `transition-limit.min` | `1` | Минимальный лимит переходов. |
| `transition-limit.max` | `2147483647` | Максимальный лимит переходов. |  

## Архитектура

| Часть | Ответственность |
| --- | --- |
| `domain` | `ShortLink` - основной класс ссылки. |
| `application` | `LinkService` создаёт ссылки, проверяет права и регистрирует переходы. `ExpiredLinkCleaner` удаляет протухшие ссылки. Интерфейсы `Storage` и `ShortCodeGenerator`. |
| `infrastructure` | `LocalFileStorage` сохраняет данные между запусками в файл, `CodeGenerator` генерирует код для ссылки, `PropertiesConfigLoader` загружает конфиг. |
| `cli` | `ConsoleApplication` парсит команды, выводит результаты и планирует очистку. |
| `Main` | точка входа |

Все диаграммы находятся в `docs/uml`.  
# CLAUDE.md

## Project Overview

CommerceLink is a Spring Boot 3.5.10 / Java 21 multi-tenant B2B e-commerce platform. It aggregates inventory from supplier feeds, manages product catalogs, processes orders, and integrates with marketplaces and service providers for payments, invoicing, shipping and label printing.

The platform is fully modularized: `app` is the orchestration core, and every integration (suppliers, marketplaces, payments, invoicing, shipping, printing, PIM) lives behind a domain `*-api` contract library. Concrete adapters are separate private modules consumed as Maven artifacts (GitHub Packages, `commerce-link/*`) and discovered at runtime via `ServiceLoader` — the app has no compile-time references to any adapter and contains zero provider-specific code. Product information lives in a separate PIM microservice consumed over HTTP.

The workspace directory containing `app` may also contain local checkouts of other parts of the system (contract libraries, adapters, shared libraries, the PIM microservice). Each is its own git repository and Maven artifact; adapter modules can be numerous and their set changes over time, so never assume a fixed list — discover what is present when needed.

## Development Commands

```bash
mvn clean compile            # Build (no adapters on classpath)
mvn clean compile -Pdev      # Build with dev adapters (test supplier + dev PIM adapter)
mvn spring-boot:run -Pdev    # Run locally
mvn test -Dtest=ClassName    # Run specific test class
mvn test -Dtest=ClassName#methodName  # Run specific test method
```

### Maven Profiles

- Default (no profile) — contract libraries only, **no adapters**. Provider registries come up empty and `PimCatalogRegistry` fails application startup (it requires exactly one PIM adapter on the classpath), so this is fine for compiling but not for running.
- **`dev`** (`-Pdev`) — adds development adapters: a fictional test supplier and a dev PIM adapter. Use for local development.
- In production, real adapters are added to the runtime classpath as jars outside the app build; `ServiceLoader` picks them up via `META-INF/services/` entries.

### Local Infrastructure

**DynamoDB**: Runs locally via **AWS NoSQL Workbench** at `http://localhost:8000`.
**Other AWS services** (S3, SQS, etc.): Simulated by **LocalStack** at `http://localhost:4566`. Configuration in `application-local.properties`.

**Schema Migration**: Managed by **Mongock** (`io.mongock:mongock-springboot-v3` + `io.mongock:dynamodb-springboot-driver`). Migrations live in `src/main/java/pl/commercelink/migration/` as `@ChangeUnit` classes with an incrementing `V###` prefix (currently V001–V023: table creation, local seeds, optimistic-lock backfill, supplier-connection migration, order-item position backfill, local bootstrap seed, service flag, store registration backfill, taxonomy mappings table, claimed-delivery-id index, order filters table and shipment trackings table (two `V011` classes with distinct change-unit ids), client verifications table, store notifications moved to their own table, marketplace import schedules, daily schedule execution counts table, then `V016`–`V020`: receipt attempts table and table recovery protection (two `V016`), default receipt email template, store-id/status index on Orders, list-key index on Deliveries, list key renamed to `deliveryListSortKey` with a new index, then `V021`: list sort key and `StoreIdRmaListSortKeyIndex` on RMA, then `V022`: discount codes table, `V023`: `DiscountCodeIndex` on Orders; the next one is `V024`). They execute automatically on application startup. Mongock tracks applied changes in the `AppMigrationsHistory` table (configurable via `mongock.migration-repository-name`) and uses `mongockLock` for distributed locking. Mongock autoconfiguration and `DynamoDbMigrationSupport` (helpers like `createTableIfAbsent`) come from the shared starter library.
**Verify**: `aws dynamodb list-tables --endpoint-url http://localhost:8000`

## Coding Conventions

- **Lombok**: Prefer Lombok to remove boilerplate. Use `@RequiredArgsConstructor` (with `access = AccessLevel.PACKAGE`/`PRIVATE` to match the intended constructor visibility) for constructors that are pure `final`-field assignment, and `@Getter`/`@Value`/`@Builder` where they fit. Don't use it where the constructor has real logic (e.g. transforming varargs) or where a `record` already removes the boilerplate. Concretely: `@Slf4j` instead of a hand-written `LoggerFactory` field; `@Getter`/`@Setter` (class-level or per field) on DynamoDB entities and `@DynamoDBDocument` beans instead of hand-written accessors — the mapper needs bean-style accessors and Lombok generates exactly those (keep explicit methods only where they carry logic or `@DynamoDBIgnore`); `@NoArgsConstructor` + `@AllArgsConstructor` for Jackson/DynamoDB beans; `@RequiredArgsConstructor` with `final` fields for Spring beans instead of `@Autowired` field injection when touching a class anyway.
- **Logging**: SLF4J via Lombok `@Slf4j` (field `log`); never `System.out`/`System.err`, never a manual `LoggerFactory`. Sentry (`logback-spring.xml`: `minimumEventLevel=ERROR`, `minimumBreadcrumbLevel=INFO`) turns **ERROR into an alertable event** and INFO/WARN into breadcrumbs only. Therefore: `log.error` for anything an operator must react to (a money-moving decision that was dropped or refused, a message that cannot be processed, a misconfiguration), `log.warn` for expected skip paths (idempotent no-ops, "already sent", "not found — will retry next poll"), no INFO on hot paths. Successes are not logged.
- **Comments**: Code should be self-explanatory — never comment *what* the code does; refactor instead. Comments that explain *why* are welcome when the reason is not visible in the code itself: an ordering that must not change (e.g. "persist before publish, otherwise a failed save leaves a real refund with no record"), an external-system quirk (API returns 422 instead of 404), a deliberate deviation from the obvious approach, or a business rule fixed by the customer. Keep them short, in English, next to the line they protect; prefer a well-named test that pins the behaviour over a comment when both are possible.
- **UI**: Thymeleaf templates in `src/main/resources/templates/`, styled with Bulma CSS. Redesigned dashboard screens use the `cl-*` layer in `static/css/commercelink.css` on top of Bulma.
- **Store settings pages** (`/dashboard/store/**`): one controller per page, view objects in `web/settings/`, form DTOs with a `validate()` returning `Map<field, messageKey>` in `web/dtos/`. The store comes from the session for `ADMIN` (`/dashboard/store/x`) or from the path for `SUPER_ADMIN` (`/dashboard/store/{storeId}/x`), never from the form; build both with `SettingsPaths.store(storeId, "/x")` and answer an unknown store or record with `ResponseStatusException(NOT_FOUND)`. Forms post to the page's own address. A form with `data-cl-async` is sent by `static/js/async-form.js` with `X-Requested-With: fetch` (`SettingsPaths.isAsync`); the controller then returns only the form fragment, 422 with errors or 200 with the saved values, instead of a redirect; each template includes `async-form.js` itself. Outcome messages after a redirect go through `SettingsFlash` (`onRedirect`, or `forNextPage` when the script navigates), shown by `settings-form :: savedAlert`. Destructive actions are confirmed with `fragments/confirm-dialog` and, without JavaScript, the `settings-confirm` page (`ConfirmAction`), so nothing is deleted by a GET; the only write on a GET is giving old records their missing ids (warehouse, shipping), through `OptimisticLockingExecutor`. The visual rules and components are in the local `docs/reference/ui-design-system.md`.
- **Email templates**: Stored per store in DynamoDB (`EmailTemplates` table) and rendered at runtime with Mustache by `EmailClient` — there are no `.mustache` files in resources. A store without its own copy of a template gets the one of the shared pool, the store `default` (`EmailTemplatesRepository.DEFAULT_STORE`); content equal to the default is not copied into the store. `EmailTemplate.type` is stored as text: a record whose type is no longer an `EmailNotificationType` is skipped when a store's templates are listed, and a template without subject or body is not sent. Nothing is sent while the store has no sender name or reply-to address (`ClientNotificationsConfiguration.hasSender`, set on `/dashboard/store/notification`); there is no fallback to the store name or the company e-mail.
- **Localization**: `LocalizedEnum` interface. Polish is primary language. Messages in `messages_pl.properties` / `messages_en.properties`.
- **DTOs**: Controllers use DTOs (in `web/dtos/`) with factory methods like `OrderDto.from(Order order)`.
- **Error handling**: `GlobalExceptionHandler` catches common exceptions. Sentry logs errors automatically.
- **CSV**: Use classes in `starter/csv/` (`CSVLoader`, `CSVWriter`, `CSVReady`).

## Project Structure

### Modules the app depends on

The app depends only on contract and shared libraries, never on adapter implementations:

- `commercelink-starter` — shared infrastructure: AWS autoconfiguration (DynamoDB, S3, SQS, SES, Secrets Manager, Parameter Store, cache, scheduler, Sentry), `DynamoDbRepository<T>`, converters, migration support, localization, security, storage
- `commercelink-commons` — plain-Java shared types (`UnifiedProductIdentifiers`, `WeightInGrams`)
- `rest-client` — shared HTTP/OAuth2 client library (`RestApi`, `RestApiWithRetry`, OAuth2 credential/token stores, device flow); used by `ProviderFactory` to build OAuth2 context for adapters
- `provider-api` — base plugin system: `ProviderDescriptor<T>` with `name()`, `displayName()`, `configurationFields()`, `create(Map)`
- Domain contracts extending it: `supplier-api`, `marketplace-api`, `payments-api`, `invoicing-api`, `shipping-api`, `printing-api`, `pim-api`

### Provider Plugin Pattern

1. `provider-api` defines `ProviderDescriptor<T>`
2. A domain `*-api` library extends it with domain interfaces (e.g. `InvoicingProvider`, `MarketplaceProvider`, `SupplierProvider`)
3. Adapter libraries implement the interfaces and register via `META-INF/services/` for `ServiceLoader` discovery
4. The app's `ProviderFactory` subclasses (e.g. `InvoicingProviderFactory`, `MarketplaceProviderFactory`, `SupplierProviderFactory`, `PaymentProviderFactory`) instantiate per-store providers from the discovered descriptors

A provider's per-store configuration (API keys and other settings) lives in Secrets Manager, and `ProviderConfigurationManager` reads it on every use, without an in-memory cache: a save in the dashboard is handled by one instance, so a copy kept in memory would stay stale on the others until a restart.

### Package Layout (`src/main/java/pl/commercelink/`)

| Package | Purpose |
|---------|---------|
| `baskets/` | Shopping carts: model, repository, REST API, abandoned-basket cleanup |
| `checkout/` | Checkout flow and REST API |
| `demo/` | Demo stores: seeder, demo order generator, cleanup job |
| `documents/` | Document types and reasons |
| `exception/` | Global exception handling |
| `financials/` | Financial reports, exchange rates, exports |
| `inventory/` | Inventory index and caching, feed reload scheduling, auto-discovery matching; `supplier/` holds feed infrastructure (loaders, registry, per-store feeds), `deliveries/` supplier deliveries |
| `invoicing/` | Invoice orchestration via invoicing providers |
| `localdev/` | Local development seed data |
| `marketplace/` | Marketplace orchestration: order import, offer export, provider factory, lifecycle listeners |
| `migration/` | Mongock `@ChangeUnit` schema migrations |
| `offer/` | Customer-facing product offer views and CSV offer import |
| `orders/` | Order core: lifecycle, statuses, fulfilment algorithms, RMA, notifications, events, imports, POS |
| `payments/` | Payment orchestration: `PaymentProviderFactory`, `PaymentWebhookRegistry`; `statements/` bank statement reconciliation |
| `pricelist/` | Pricelist generation, daily snapshots, rolling price aggregates |
| `printing/` | `PrintProviderRegistry` discovers printer adapters; printer profiles per store in `WarehouseConfiguration.printers` |
| `products/` | Store product catalog, pricing strategies, health scoring, filters; `information/` consumes the external PIM |
| `provider/` | Provider infrastructure: `ProviderFactory`, configuration management, event binding |
| `registration/` | New store/user registration |
| `shipping/` | Shipping orchestration: `ShippingService`, provider factory, webhook registry, shipment cancellation |
| `starter/` | Remaining in-app shared utilities: `csv`, `dynamodb`, `email`, `rest`, `security`, `storage`, `util` (see note below) |
| `stores/` | Store (tenant) configuration: branding, checkout, shipping, invoicing, RMA, integrations, printers |
| `taxonomy/` | Category taxonomy: parser, generator, resolver, localization, `mapping/` supplier category mappings |
| `templates/` | Email template storage (DynamoDB-backed) |
| `users/` | AWS Cognito integration (`CognitoConfig`, `CognitoUserService`) |
| `warehouse/` | Built-in warehouse: stock levels, goods in/out, restock suggestions, warehouse fulfilment |
| `web/` | Controllers and REST APIs + `dtos/` |

**Starter split note**: the starter library extraction is partial. The external `commercelink-starter` library and the in-app `starter/` package coexist and share the `pl.commercelink.starter.*` package namespace; a few classes (e.g. `WebSecurityConfiguration`) exist in both places with different content, and the app's version wins on the classpath. Check both locations when working on starter classes.

## Terminology

- **Supplier**: A distributor or retailer that supplies goods/inventory. There is **no `Supplier` enum** — suppliers are identified by name (`String`) with a `SupplierInfo` record (name, type, accuracy score, origin, shipping policy) provided by each adapter's `SupplierProviderDescriptor`. `SupplierRegistry` collects all descriptors from `ServiceLoader` and adds two built-in entries: `Warehouse` (internal), `Other` (fallback). `SupplierType` is `Distributor` or `Retailer`.
- **Provider**: Any pluggable integration (suppliers, marketplaces, payments, shipping, invoicing, printing) using the `provider-api` plugin pattern.

## Architecture

### Multi-Tenancy

The system is organized around `Store` entities. Each store has independent product catalogs, supplier connections, service provider configuration (payment/shipping/invoicing), branding, and RMA settings.

Invoicing and WMS providers are single-instance per store (selected provider stored as a single `Integration` entry in `Store.integrations`, keyed by `IntegrationType`). Shipping has a default integration (the `SHIPPING_PROVIDER` entry, e.g. Furgonetka) plus additional ones listed by adapter name in `ShippingConfiguration.additionalIntegrations` (today only `allegro`, Wysyłam z Allegro; kept out of `IntegrationType` so an older app version can still read the store). `Store.shippingIntegrationNames()` / `hasShippingIntegration(name)` list them, and `ShippingProviders` resolves the provider per shipment (`forShipment` uses `Shipment.provider`, falling back to the default integration for older shipments; `forName` for a chosen integration). Never call `ShippingProviderFactory.get(store)` for an operation on an existing shipment. Marketplace and payment integrations are multi-instance — a store can connect any number of them via `Store.marketplaces` (`List<MarketplaceIntegration>`) and `Store.payments` (`List<PaymentIntegration>`). Payment integrations carry a `default` flag and exactly one is treated as the store's default (used by `Checkout` for now); `Store.getDefaultPaymentIntegration()` resolves it.

Self-registration outside demo mode (`app.registration.demo=false`) creates an empty store on a trial: `Store.trial` (`TrialPeriod`) ends `app.registration.trial-days` days (14) after registration, and a store without it is a full account. A super admin converts the store to a full account on `/dashboard/stores` (`StoreTrialService.convertToFullAccount`); deleting a trial store also deletes its owner's Cognito account, but only while that account still points at the store.

A store is active unless `Store.active` is `false` (no value means active) or its trial has ended; `StoreActivity.isActive` is the one place that decides it. Only the work that starts something new for a store asks it and skips an inactive store with a WARN: the orders and returns imports, offer exports, pricelists, supplier feeds and PIM index requests. Everything that carries on with what already exists runs as usual, so nothing begun before the deactivation is left half-done: fulfilment, supplier purchases and their tracking, goods out, invoices, e-receipts, e-mails, shipment tracking and cancellation, marketplace order and return events, the cleanup of products left in categories switched to dynamic, and the order and RMA lifecycle crons. A new listener or job has to be put on one side or the other. Nothing is disabled in AWS: EventBridge schedules keep firing and their messages are skipped, so reactivation needs no step beyond the flag. The public entry points go through `PublicStoreActivityInterceptor`: the shop API under `/Store/{storeId}` answers 403 `{"error":"store-inactive"}` and the client pages under `/store/{storeId}` show `store-inactive.html`. The webhooks under `/Store/{storeId}/Webhooks` stay open and are processed as usual, because they report what already happened at the provider: a basket paid before the deactivation still becomes an order and is carried on like any other (logged at ERROR, since its owner cannot handle it in the read-only dashboard), and shipping and receipt statuses are still recorded. `PathTenantResolver` reads the path decoded, as the controllers are matched and get their `storeId`, so a percent-encoded path (`/%53tore/%61bc...`) cannot slip past the check. The dashboard of an inactive store is read-only (`DashboardReadOnlyInterceptor`): every GET works, including downloads, while anything else is refused with a message and the banner says why. A super admin deactivates or activates any store on `/dashboard/stores` (`StoreActivationService`); switching off by hand never deletes anything, and a store whose trial ended comes back only by conversion.

`StoreLifecycleSweep` (hourly: `store-lifecycle-sweep-queue` sent by EventBridge Scheduler in prod, `StoreLifecycleSweepScheduler` locally) takes a store whose trial ended through the steps recorded in `Store.deactivation` (`StoreDeactivation`): it is marked `TRIAL_ENDED`, its owner gets one e-mail from CommerceLink (`TrialEndedNotice`, in Polish), and `app.registration.trial-retention-days` (14) after the trial end the store is deleted with `StoreDeletionService.Guard.TRIAL_ONLY`. A failed step stays unrecorded and the next sweep tries it again; a store switched off by hand has no such steps. Marketplace offers are never touched, by the customer's decision: neither the deactivation nor the deletion changes or removes them, and since exports and order imports of an inactive store are skipped, its offers keep their last exported price and stock while the orders they bring are not imported.

### Store PIM Entries and Clones

A store can clone a global PIM entry; the store's products on the source then move onto the clone (`StoreEntryPinning`), so the same product is known by two ids. Every new reader keyed by `pimId` (price lists, baskets, discounts, price history, links) resolves it through `StorePimAliases` (`find`, `aliasesOf`, `sourceOf`, `cloneOf`), and inventory lookups through `StoreEntryKeys`; reading by the raw id misses the other one. The marketplace offer id always comes from `StorePimAliases.offerIdOf`, so a product on a clone keeps the offer published under its source. In the offer export, a product whose `pimId` the index copy does not know (a fresh clone on a stale copy, or a dead pin) is held back only while it protects a previous offer: one with its codes that no product of the run offers, still on the marketplace, and kept for at most `marketplace.export.unknown-entry-max-waiting-runs` runs, after which that offer is reported once at ERROR and removed. In every other case (an offer already under its own id, or nothing to protect) it is exported under its own `pimId` with a WARN to re-pin it, so the hold is always bounded.

### DynamoDB Tables

All entities use `@DynamoDBTable`, `@DynamoDBHashKey`, `@DynamoDBRangeKey` annotations. Repositories extend `DynamoDbRepository<T>`. Tables are created by Mongock migrations.

| Table | Entity | Hash key | Range key | Indexes |
|-------|--------|----------|-----------|---------|
| Stores | `Store` | storeId | — | |
| Orders | `Order` | storeId | orderId | GSI `StoreIdOrderedAtIndex`, GSI `ExternalOrderIdIndex`, GSI `DiscountCodeIndex` (sparse: discountCode + orderedAt) |
| OrderItems | `OrderItem` | orderId | itemId | |
| OrderEvents | `OrderEvent` | orderId | eventId | LSI `NameIndex` |
| Products | `Product` | categoryId | productId | GSI `PimIdIndex` |
| Catalogs | `ProductCatalog` | storeId | catalogId | |
| Baskets | `Basket` | storeId | basketId | GSI `BasketCreatedAtIndex` |
| Deliveries | `Delivery` | storeId | deliveryId | GSI `StoreIdDeliveryListSortKeyIndex` |
| EmailTemplates | `EmailTemplate` | storeId | templateName | |
| RMA | `RMA` | storeId | rmaId | GSI `StoreIdRmaListSortKeyIndex` |
| RMAItems | `RMAItem` | rmaId | rmaItemId | |
| RMACenters | `RMACenter` | storeId | rmaCenterId | |
| WarehouseItems | `WarehouseItem` | storeId | itemId | |
| WarehouseDocuments | `WarehouseDocument` | storeId | documentId | GSI `DeliveryIdIndex`, `CreatedAtIndex`, `OrderIdIndex`, `RMAIdIndex` |
| WarehouseDocumentItems | `WarehouseDocumentItem` | documentId | itemId | GSI `DeliveryIdIndex` |
| WarehouseDocumentSequences | `WarehouseDocumentSequence` | storeId | sequenceKey | |
| TaxonomyCategoryMappings | `CategoryMapping` | supplier | rawCategory | |
| DailyScheduleExecutionCounts | `DailyScheduleExecutionCount` | storeId | counterKey | |
| DiscountCodes | `DiscountCode` | storeId | code | |

Mongock additionally owns `AppMigrationsHistory` and `mongockLock` (no entity classes). Product information tables (PIM index, brands, queue, category matches) live in the PIM microservice, not in the app — the app consumes the index via HTTP (`/PIM/Index`).

### Inventory Suppliers

Supplier adapters implement `SupplierProvider` (a single `download()` returning `FeedData`) with a `SupplierProviderDescriptor` carrying `SupplierInfo` and a `FeedFormat` (CSV with a row parser, or XML with an item class). Feed parsing lives in the app (`CsvProductFeedLoader` / `XmlProductFeedLoader` in `inventory/supplier/`).

Feed loading runs on two tracks:

- **Global feeds** — `FeedReloaderScheduler` runs every 5 minutes, compares each provider's last update with the feed file date in S3, and reloads only suppliers with newer feeds; prices are converted to the local currency, then the inventory index is updated in one pass.
- **Per-store feeds** — `StoreSupplierFeedScheduler` creates an EventBridge Scheduler entry per store+supplier (once daily at a random night hour, Europe/Warsaw) targeting `supplier-feed-import-queue`, consumed by `SqsFeedLoaderEventListener`; `triggerImmediateImport()` allows ad-hoc imports. Active only when `application.env=prod`.

Products are matched across suppliers by EAN and manufacturer code via `InventoryAutoDiscovery`.

### Price Snapshots and Rolling Aggregates

`DailyPriceSnapshotGenerator` (`supplier-daily-price-snapshot-queue`) and `RollingPriceAggregateGenerator` (`supplier-rolling-price-aggregate-queue`, 30 days of snapshots) are prod-only and triggered from outside the app with no payload. Each exists twice, through `PriceDataLocation`: globally in the datalake bucket (`daily-price-snapshot/`, `rolling-price-aggregate/`, from `withGlobalData()`), and per store in the `stores` bucket under `{storeId}/` (from the store's own suppliers only - enabled global suppliers and own or manual feeds included in pricing, `SupplierScope.PRICING` - and not its warehouse), so `StoreDeletionService` wipes them with the rest of the store's files.

A payload-less (or unreadable) message is the global run: it first queues one `{storeId, date}` message per active store on the same queue (`PriceDataFanOut`, so one store's failure is retried alone and a failing global run does not leave the stores without theirs), then does its own work. The store's message carries the day of the run that queued it, so a run past midnight still writes the same day. It is sent as JSON text (`PriceDataMessage.toJson`), never as an object: `SqsTemplate` tags an object with its class (`JavaType`), which the `String` listeners cannot take, and the message ends in the DLQ. An inactive store is skipped (new work, see Multi-Tenancy) and a store without priced offers gets no snapshot. After a store's aggregate the store's snapshots older than 30 days are deleted, and a store with no snapshot in the window loses its aggregate, since the newest aggregate file would otherwise override the global prices for good; the global files are kept.

Readers use `RollingPriceAggregateRepository.loadForStore(storeId)`: the global aggregate with the store's laid over it product by product, so a store without history, or with an unreadable aggregate (logged at ERROR), is priced as before (`AvailabilityAndPriceList` stop-loss, `StockLevels` restock). `loadAll()` is the global one only.

### Order Lifecycle

1. **Order intake**: `Basket` via REST API → payment link from the store's payment provider → `Order` creation; orders are also imported from connected marketplaces (`MarketplaceOrderImporter`)
2. **Fulfilment**: Auto-allocation via `AutomatedOrderFulfilment` or manual via `ManualOrderFulfilment`
3. **Shipping**: label generation and tracking via the store's shipping provider
4. **Invoicing**: `InvoicingService` creates invoices via the store's invoicing provider (proforma, standard, advance, final, credit notes)
5. **Notifications**: Email notifications via SES for each lifecycle stage

**Order statuses** (`OrderStatus`): New, Blocked, Assembly, Assembled, Realization, Shipping, Delivered, Cancelled, Completed
**Fulfilment statuses** (`FulfilmentStatus`): New, Allocation, Ordered, Reserved, Delivered, InRMA, InExternalService, Returned, Replaced, Destroyed
**RMA statuses** (`RMAStatus`): New, Approved, Rejected, WaitingForItems, ItemsReceived, Processing, Completed

### Bank Statement Reconciliation

Customers' transfers are booked from a bank statement on `/dashboard/payments` ("Wgraj wyciąg bankowy", `web/payments/StatementController`). How to read an account's CSV export is stored with the account (`BankAccount.statementFormat`, `BankStatementFormat`: encoding, separator, header row below the bank's preamble, date format, decimal mark, columns, and the payout rows - payers that settle many orders with one transfer, each row a payer's text and the orders it pays by source and payment method, whether the transfer is net of fees, an optional fee limit and optional fee rates, several rows per payer allowed); it is set on `/dashboard/store/payments/bank-accounts/{id}/statement`, where a sample file fills the form in (`BankStatementSniffer`) and is previewed but never stored. Nothing of a statement is stored either: the upload is parsed once (`BankStatementParser`), the operations and the operator's choices travel with the reconciliation page as JSON, and the ticked ones are booked in one post. Only money in is reconciled; money out (deliveries) is left out. Operators upload statements from the last booked operation on, which is tracked outside the app.

`StatementMatcher` proposes orders for each incoming operation against the open orders (the Payments page's read), in this order: its number already on a payment (booked), a transfer from a configured payer (a payout: the orders its title names by order, document or reference number - a gateway such as PayNow paying out each payment less its fee, the difference then being the fee of the one named order, left unticked with a warning when above the row's fee limit (`PayoutChannel.maxFeePercent`, a percentage of what the order owes; none by default) - else a run by date of the orders of one of the payer's rows, or of all of them, adding up to the transfer, net of fees where the order's first row says so - the recorded fee, or with fee rates (`PayoutChannel.feeRates`, e.g. a bank taking 2% or 4% of each loan) each order at one of them, rounded half up to the grosz, proposed only when exactly one run and combination of rates fits; `PayoutSums` keeps the search bounded and gives up past 2000 partial sums), an order number in the title (whole id, 8-character short id, external id; a closed order named there is read by `OrdersRepository.findByShortId`), a document or pending payment reference number in the title, and the amount (with the sender matching the client, or the description when the export has no sender column). Only a number in the title for exactly what is owed (or a payout naming its orders that adds up) is ticked by itself. `StatementBooking` saves each order through `OrderLifecycle.update`: the pending payment is filled (or a payment added) with the operation's number in `bankTransactionNo` and its date in `bankTransactionDate`; a bank without operation numbers gets a fixed one derived from the operation (`OperationNumbers`, "WB-..."). That number makes a repeated booking a no-op. A payout keeps the order's payment method and records the fee the payer kept. A fee given for an order on the reconciliation page (proposed or typed) replaces the one recorded on its payment; an order booked without one keeps its recorded fee (a marketplace's commission, a gateway's fee, one typed in), so a payer marked wrongly (or not configured, the transfer then taken as the customer's) cannot wipe it. A direct transfer for an order waiting for another method turns the pending payment into a bank transfer, refused while an invoice or e-receipt fixes the method. The method of an order is never changed otherwise, and the existing reference number stays.

### Stock Ledger Period Closing

The stock ledger (`/dashboard/reports/stockLedgerExport`, `warehouse/builtin/StockLedgerService`) takes its opening balance (BO) from the last closed period instead of the whole warehouse history. An ADMIN closes a period on `/dashboard/reports` (`StockLedgerPeriodClosing`) by picking a date range that ended before today - usually a calendar month. Closed periods form one chain without gaps: a new period has to start the day after the last closed one ends (the picker offers that month by default), and only the first one can start anywhere, so the history can be closed back in time from its oldest month. A period closes only when every delivery with a goods receipt (PZ) in it has its purchase invoice linked and synced (`Delivery.isMissingInvoiceSync`, the own warehouse exempt as in the deliveries list filters); otherwise the closing is refused and the deliveries are listed. The period's report is stored in the `stores` bucket as `{storeId}/reports/stock-ledger/{from}_{to}.csv` (`StockLedgerClosings`): the file existing is what makes the period closed, and its BZ columns, rounded as printed, are the BO of every report starting after it - the latest closed period ending before `dateFrom`, with the documents between its end and `dateFrom` replayed on top. Without such a period, or with an unreadable file, the report reads the whole history as before.

Nothing is locked after a closing: linking, unlinking or syncing a purchase invoice still rewrites the `unitPrice` of the delivery's warehouse documents in any month (`BuiltInInvoiceSyncHandler`), and the stored report does not follow by itself. Closing the same range again regenerates it ("Wygeneruj ponownie" on the closed periods list), behind the same invoice check. Only that period is regenerated: the closed periods after it start from its closing balance, and generating them again, one after another, is left to the user - when the regenerated closing balance differs from the stored one (or the stored file cannot be read), the page names those later periods as out of date. The same applies to any new code that changes the quantity, price, MFN, type or date of existing warehouse documents.

### Event-Driven Processing

Async work is driven through `@SqsListener` methods. Queue names follow `{domain}-{action}-queue[.fifo]` (e.g. `order-fulfilment-queue.fifo`, `supplier-feed-import-queue`, `marketplace-orders-import-queue`, `basket-cleanup-queue`).

Shipping runs on its own queues, each message naming the integration (`provider`; a message without one belongs to the store's default integration): `shipment-creation-queue`, `shipment-pickup-queue`, `shipment-cancellation-queue` (checks of commands sent to the carrier), `shipment-tracking-queue` (webhook statuses), `shipment-tracking-sweep-queue` and `shipment-tracking-poll-queue` (hourly polling of integrations without webhooks, below).

**Scheduled tasks** (`@Scheduled`):
- Every 5 min: `FeedReloaderScheduler` — reload global inventory feeds
- Every 5 min: `TaxonomyCategoryMatchSweepScheduler` — local-only trigger for the taxonomy category match sweep (`TaxonomyCategoryMatchSweep`); in prod the trigger is instead `taxonomy-category-match-sweep-queue`, sent by EventBridge Scheduler with no payload and consumed by `TaxonomyCategoryMatchSweepListener`
- Hourly: `PimCatalogRegistry` — refresh PIM caches
- Hourly: `DemoStoreCleanupScheduler` — local-only trigger for the expired demo store cleanup (`DemoStoreCleanup`); in prod the trigger is instead `demo-store-cleanup-queue`, sent by EventBridge Scheduler with no payload and consumed by `DemoStoreCleanupListener`. All three exist only with `app.registration.demo=true`, so the schedule is set up only for the demo environment
- Hourly: `DropshipTrackingSweepScheduler` — local-only trigger for the dropship tracking sweep; in prod the trigger is instead `supplier-dropship-tracking-sweep-queue`, sent by EventBridge Scheduler with no payload and consumed by `DropshipTrackingSweepListener`
- Hourly: `ShipmentTrackingSweepScheduler` — local-only trigger for the shipment tracking sweep (parcels of integrations without webhooks, e.g. Wysyłam z Allegro); in prod the trigger is `shipment-tracking-sweep-queue` (EventBridge Scheduler, no payload) consumed by `ShipmentTrackingSweepListener`. The sweep sends one message per due parcel to `shipment-tracking-poll-queue`, handled by `ShipmentTrackingPollListener` → `ShipmentTrackingPoller`
- Hourly: `StoreLifecycleSweepScheduler` — local-only trigger for the store lifecycle sweep (every minute in `localdev`); in prod the trigger is `store-lifecycle-sweep-queue`, sent by EventBridge Scheduler with no payload and consumed by `StoreLifecycleSweepListener`

Every running instance executes every `@Scheduled` method, so as soon as an environment runs more than one instance, a job scheduled that way runs once per instance. A periodic job with effects outside its own instance (calls to the PIM, deletions, supplier polling) therefore gets a local `@Scheduled` trigger conditional on `application.env=localhost` (a missing value counts as local) plus a prod `@SqsListener` on a payload-less queue fed by EventBridge Scheduler, so exactly one instance runs each tick however many are up; the queue and its schedule live in the terraform repositories. `FeedReloaderScheduler` and `PimCatalogRegistry` stay per instance on purpose: they only refill that instance's in-memory caches.

### Report Queue

Closing a stock ledger period reads the whole warehouse history for the opening balance, which can outlast a web request (the load balancer cuts requests off after its idle timeout), so it is ordered, not run in the request: the controller sends `{storeId, reportType, dateFrom, dateTo}` to `report-generation-queue.fifo` (`ReportGenerationPublisher`; FIFO with one message group per store, so a store's closings run in the order they were ordered while stores run in parallel; visibility 900 s, 3 receives, DLQ) and answers "order accepted, come back later and refresh"; `ReportGenerationListener` runs the `ReportGenerator` registered for the `ReportType` (today only `STOCK_LEDGER_CLOSING`, `StockLedgerClosingReportGenerator`). A new queued report is one `ReportGenerator` implementation plus a `ReportType`. The stock ledger export and the other CSV exports are still synchronous.

There is no job table and no status: the closed period appearing in the list (its row shows when the report was last generated, the S3 file's last-modified time) is the result. A report still being generated, or one that failed, is not visible on the page. A failure is logged at ERROR (Sentry) and rethrown, so the message is retried after the visibility timeout and parked in the DLQ after three receives; ordering the same range twice runs it twice (the deduplication id is unique per message, so a regeneration is never dropped as a duplicate). A closing refused in the worker (the data changed while it waited) is a WARN and leaves the period open; the "later periods are out of date" notice after a regeneration is no longer shown.

The closing is checked twice: `StockLedgerPeriodClosing.check` when it is ordered, so a refusal (range, gap, unsettled deliveries) is answered at once on the page, and again inside `close` in the worker. A message already queued is carried on for an inactive store, like the other work that continues what exists; ordering a new one is a POST, which the read-only dashboard refuses.

### Scheduled Execution Counters

Per-store schedules (orders import, returns import, supplier feed, pricelist) are billed by how often they actually ran, so every completed execution is counted in the `DailyScheduleExecutionCounts` table. One item per store, day, execution type and target (the marketplace, supplier or catalog the schedule belongs to), with the day cut in `EventBridgeSchedules.TIMEZONE`, Europe/Warsaw. The range key is `<date>#<TYPE>#<target>`, so a store's day or month is one `begins_with` query:

```
storeId     counterKey                           executionDate  executionType   target        executionCount
uma2dqukxr  2026-09-17#ORDERS_IMPORT#Allegro     2026-09-17     ORDERS_IMPORT   Allegro       144
uma2dqukxr  2026-09-17#ORDERS_IMPORT#Empik       2026-09-17     ORDERS_IMPORT   Empik         3
uma2dqukxr  2026-09-17#SUPPLIER_FEED#Wortmann    2026-09-17     SUPPLIER_FEED   Wortmann      1
```

Totals are never stored: per-type and monthly figures are sums on read (`ScheduleExecutionUsage`, via `ScheduleExecutionUsageService.monthlyUsage` / `dailyUsage`), so they cannot drift from the counts. Enum constant names are part of the key, so renaming a `ScheduledExecution` constant orphans its history.

`ScheduledExecutionCounter.countCompleted(...)` is called as the last step of the success path in each SQS listener, so only runs that did their work are counted:

| `ScheduledExecution` | Counted in | Counted when | Not counted |
|---|---|---|---|
| `ORDERS_IMPORT` | `MarketplaceOrdersImportEventListener` | orders fetched and the store's `lastFetchedAt` saved | store unknown or without an active integration, no provider, fetch or import threw |
| `RETURNS_IMPORT` | `MarketplaceReturnsImportEventListener` | returns fetched and imported | same skips as orders, plus marketplaces whose provider has no returns API, plus the `marketplace.returns.enabled=false` kill switch |
| `SUPPLIER_FEED` | `SqsFeedLoaderEventListener` | the store feed load returned normally | global feeds (no store), configuration-not-ready retries, load threw |
| `PRICELIST` | `PricelistEventListener` | pricelist saved and the pricelist event published | generation, save or publish threw |

Manual triggers that go through the same queues (e.g. `StoreSupplierFeedScheduler.triggerImmediateImport`) count like scheduled runs; there is no way to tell them apart from the payload. Standard SQS queues are at-least-once, so a redelivered message can count a run twice; this is accepted rather than deduplicated.

Each execution is one atomic `UpdateItem` in `DailyScheduleExecutionCountRepository`: `ADD #count :one` raises `executionCount` and `SET` writes the date, type and target, creating the item on the first execution of the day. Concurrent app instances never lose a count. The repository does not extend `DynamoDbRepository`, so there is no `save()` that could overwrite a count from a stale copy. A failed increment is logged at ERROR and swallowed: rethrowing would make SQS redeliver the message and rerun the whole import. Counts are billing data and outlive the store: `StoreDeletionService` keeps them for every store that is not a trial, so a store deleted mid-month can still be invoiced for the days it ran; a trial is never billed, so its counts go with it. The listeners are prod-only (`application.env=prod`); locally they run through the `cl` script's patches.

### Security

- OAuth2 authentication via AWS Cognito (`users/CognitoConfig`, security config in `starter/security/config/`: `WebSecurityConfiguration`, `WebConfig`, `LocalDevOAuth2UserService`)
- Roles (`UserRole`): `USER`, `ADMIN`, `SUPER_ADMIN`
- `@PreAuthorize` on controller methods
- `StoreAccessInterceptor` enforces tenant isolation
- `StoreApiKeyAuthorizationInterceptor` for API key auth

### AWS Services

| Service | Usage |
|---------|-------|
| DynamoDB | Primary database |
| S3 | Feed storage, pricelists, images, stores |
| SQS | Async job queues |
| EventBridge Scheduler | Per-store daily feed imports, recurring generation jobs |
| SES v2 | Email delivery |
| Secrets Manager | External API credentials |
| SSM Parameter Store | Configuration values |

Environment switching: `application.env=localhost` uses local DynamoDB and filesystem; `application.env=prod` uses AWS in eu-central-1.

## Key Entry Points

| Purpose | Path |
|---------|------|
| Application | `Application.java` |
| Security | `starter/security/config/WebSecurityConfiguration.java` |
| Schema migration | `migration/V001_CreateDynamoDbTables.java` and subsequent `V###` classes |
| Provider plugins | `provider/ProviderFactory.java` |
| Orders API | `web/OrdersController.java` |
| Order lifecycle | `orders/OrderLifecycle.java` |
| Fulfilment | `orders/fulfilment/AutomatedOrderFulfilment.java` |
| Inventory | `inventory/Inventory.java` |
| Feed loading | `inventory/FeedReloaderScheduler.java` |
| Supplier registry | `inventory/supplier/SupplierRegistry.java` |
| Product catalog | `products/ProductCatalog.java` |
| PIM | `products/information/PimCatalogRegistry.java` (consumes external PIM service) |
| Pricelist | `pricelist/PricelistEventListener.java` |
| Invoicing | `invoicing/InvoicingService.java` |
| Shipping | `shipping/ShippingService.java` |
| Payments | `payments/PaymentProviderFactory.java` |
| Marketplaces | `marketplace/MarketplaceProviderFactory.java` |
| Warehouse | `warehouse/builtin/BuiltInWarehouse.java` |
| RMA | `orders/rma/RMAManager.java` |
| Store config | `stores/Store.java` |

All paths relative to `src/main/java/pl/commercelink/`.

## External Integrations

All external integrations (payment processors, marketplaces, invoicing services, shipping aggregators, label printers, suppliers) are implemented as adapter modules behind the domain `*-api` contracts and discovered via `ServiceLoader`. The app itself contains no integration-specific code; which integrations are available depends on which adapter jars are on the runtime classpath, and the adapter set grows over time.

## Testing

- JUnit 5 + Mockito
- Prefer `@ExtendWith(MockitoExtension.class)` with `@Mock` / `@InjectMocks` fields over manual `Mockito.mock(...)` calls — always use the annotations when the collaborators map to fixed fields. Fall back to `mock(...)` only for mocks created dynamically (e.g. several instances of the same type produced by a helper)
- Test files in `src/test/java` mirror source structure (~150+ test classes covering most packages)
- `mvn test -Dtest=ClassName#methodName`
- Test method names are camelCase (not snake_case)
- Structure every `@Test` body with `// given` / `// when` / `// then` section comments. Use a combined `// when / then` when a single statement both invokes and asserts (e.g. `assertThrows`). Omit a section when it has no lines — never leave an empty section.

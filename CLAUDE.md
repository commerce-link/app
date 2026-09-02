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

**Schema Migration**: Managed by **Mongock** (`io.mongock:mongock-springboot-v3` + `io.mongock:dynamodb-springboot-driver`). Migrations live in `src/main/java/pl/commercelink/migration/` as `@ChangeUnit` classes with an incrementing `V###` prefix (currently V001–V009: table creation, local seeds, optimistic-lock backfill, supplier-connection migration, order-item position backfill, store registration backfill, taxonomy mappings table). They execute automatically on application startup. Mongock tracks applied changes in the `AppMigrationsHistory` table (configurable via `mongock.migration-repository-name`) and uses `mongockLock` for distributed locking. Mongock autoconfiguration and `DynamoDbMigrationSupport` (helpers like `createTableIfAbsent`) come from the shared starter library.
**Verify**: `aws dynamodb list-tables --endpoint-url http://localhost:8000`

## Coding Conventions

- **Lombok**: Prefer Lombok to remove boilerplate. Use `@RequiredArgsConstructor` (with `access = AccessLevel.PACKAGE`/`PRIVATE` to match the intended constructor visibility) for constructors that are pure `final`-field assignment, and `@Getter`/`@Value`/`@Builder` where they fit. Don't use it where the constructor has real logic (e.g. transforming varargs) or where a `record` already removes the boilerplate.
- **No Logger**: We log all entries to Sentry automatically. Use `System.out`/`System.err` only in rare cases.
- **No comments**: Code should be self-explanatory. Refactor instead of commenting.
- **UI**: Thymeleaf templates in `src/main/resources/templates/`, styled with Bulma CSS.
- **Email templates**: Stored per store in DynamoDB (`EmailTemplates` table) and rendered at runtime with Mustache by `EmailClient` — there are no `.mustache` files in resources.
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
| `payments/` | Payment orchestration: `PaymentProviderFactory`, `PaymentWebhookRegistry` |
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

- **Supplier**: A distributor or retailer that supplies goods/inventory. There is **no `Supplier` enum** — suppliers are identified by name (`String`) with a `SupplierInfo` record (name, type, accuracy score, origin, shipping policy) provided by each adapter's `SupplierProviderDescriptor`. `SupplierRegistry` collects all descriptors from `ServiceLoader` and adds three built-in entries: `Amazon`, `Warehouse` (internal), `Other` (fallback). `SupplierType` is `Distributor` or `Retailer`.
- **Provider**: Any pluggable integration (suppliers, marketplaces, payments, shipping, invoicing, printing) using the `provider-api` plugin pattern.

## Architecture

### Multi-Tenancy

The system is organized around `Store` entities. Each store has independent product catalogs, supplier connections, service provider configuration (payment/shipping/invoicing), branding, and RMA settings.

Shipping, invoicing and WMS providers are single-instance per store (selected provider stored as a single `Integration` entry in `Store.integrations`, keyed by `IntegrationType`). Marketplace and payment integrations are multi-instance — a store can connect any number of them via `Store.marketplaces` (`List<MarketplaceIntegration>`) and `Store.payments` (`List<PaymentIntegration>`). Payment integrations carry a `default` flag and exactly one is treated as the store's default (used by `Checkout` for now); `Store.getDefaultPaymentIntegration()` resolves it.

### DynamoDB Tables

All entities use `@DynamoDBTable`, `@DynamoDBHashKey`, `@DynamoDBRangeKey` annotations. Repositories extend `DynamoDbRepository<T>`. Tables are created by Mongock migrations.

| Table | Entity | Hash key | Range key | Indexes |
|-------|--------|----------|-----------|---------|
| Stores | `Store` | storeId | — | |
| Orders | `Order` | storeId | orderId | GSI `StoreIdOrderedAtIndex`, GSI `ExternalOrderIdIndex` |
| OrderItems | `OrderItem` | orderId | itemId | |
| OrderEvents | `OrderEvent` | orderId | eventId | LSI `NameIndex` |
| Products | `Product` | categoryId | productId | GSI `PimIdIndex` |
| Catalogs | `ProductCatalog` | storeId | catalogId | |
| Baskets | `Basket` | storeId | basketId | GSI `BasketCreatedAtIndex` |
| Deliveries | `Delivery` | storeId | deliveryId | |
| EmailTemplates | `EmailTemplate` | storeId | templateName | |
| RMA | `RMA` | storeId | rmaId | |
| RMAItems | `RMAItem` | rmaId | rmaItemId | |
| RMACenters | `RMACenter` | storeId | rmaCenterId | |
| WarehouseItems | `WarehouseItem` | storeId | itemId | |
| WarehouseDocuments | `WarehouseDocument` | storeId | documentId | GSI `DeliveryIdIndex`, `CreatedAtIndex`, `OrderIdIndex`, `RMAIdIndex` |
| WarehouseDocumentItems | `WarehouseDocumentItem` | documentId | itemId | GSI `DeliveryIdIndex` |
| WarehouseDocumentSequences | `WarehouseDocumentSequence` | storeId | sequenceKey | |
| TaxonomyCategoryMappings | `CategoryMapping` | supplier | rawCategory | |

Mongock additionally owns `AppMigrationsHistory` and `mongockLock` (no entity classes). Product information tables (PIM index, brands, queue, category matches) live in the PIM microservice, not in the app — the app consumes the index via HTTP (`/PIM/Index`).

### Inventory Suppliers

Supplier adapters implement `SupplierProvider` (a single `download()` returning `FeedData`) with a `SupplierProviderDescriptor` carrying `SupplierInfo` and a `FeedFormat` (CSV with a row parser, or XML with an item class). Feed parsing lives in the app (`CsvProductFeedLoader` / `XmlProductFeedLoader` in `inventory/supplier/`).

Feed loading runs on two tracks:

- **Global feeds** — `FeedReloaderScheduler` runs every 5 minutes, compares each provider's last update with the feed file date in S3, and reloads only suppliers with newer feeds; prices are converted to the local currency, then the inventory index is updated in one pass.
- **Per-store feeds** — `StoreSupplierFeedScheduler` creates an EventBridge Scheduler entry per store+supplier (once daily at a random night hour, Europe/Warsaw) targeting `supplier-feed-import-queue`, consumed by `SqsFeedLoaderEventListener`; `triggerImmediateImport()` allows ad-hoc imports. Active only when `application.env=prod`.

Products are matched across suppliers by EAN and manufacturer code via `InventoryAutoDiscovery`.

### Order Lifecycle

1. **Order intake**: `Basket` via REST API → payment link from the store's payment provider → `Order` creation; orders are also imported from connected marketplaces (`MarketplaceOrderImporter`)
2. **Fulfilment**: Auto-allocation via `AutomatedOrderFulfilment` or manual via `ManualOrderFulfilment`
3. **Shipping**: label generation and tracking via the store's shipping provider
4. **Invoicing**: `InvoicingService` creates invoices via the store's invoicing provider (proforma, standard, advance, final, credit notes)
5. **Notifications**: Email notifications via SES for each lifecycle stage

**Order statuses** (`OrderStatus`): New, Blocked, Assembly, Assembled, Realization, Shipping, Delivered, Cancelled, Completed
**Fulfilment statuses** (`FulfilmentStatus`): New, Allocation, Ordered, Reserved, Delivered, InRMA, InExternalService, Returned, Replaced, Destroyed
**RMA statuses** (`RMAStatus`): New, Approved, Rejected, WaitingForItems, ItemsReceived, Processing, Completed

### Event-Driven Processing

Async work is driven through `@SqsListener` methods. Queue names follow `{domain}-{action}-queue[.fifo]` (e.g. `order-fulfilment-queue.fifo`, `supplier-feed-import-queue`, `marketplace-orders-import-queue`, `basket-cleanup-queue`).

**Scheduled tasks** (`@Scheduled`):
- Every 5 min: `FeedReloaderScheduler` — reload global inventory feeds
- Every 5 min: `TaxonomyCategoryMatchScheduler` — taxonomy category match sweep
- Hourly: `PimCatalogRegistry` — refresh PIM caches
- Hourly: `DemoStoreCleanupJob` — clean up demo stores
- Hourly: `DropshipTrackingSweepScheduler` — local-only trigger for the dropship tracking sweep; in prod the trigger is instead `supplier-dropship-tracking-sweep-queue`, sent by EventBridge Scheduler with no payload and consumed by `DropshipTrackingSweepListener`

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

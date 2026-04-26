# CLAUDE.md

## Project Overview

CommerceLink is a Spring Boot 3.5.10 / Java 21 multi-tenant B2B e-commerce platform. It aggregates inventory from 14 suppliers (distributors/retailers), manages product catalogs, processes orders, and integrates with service providers for payments (Stripe, PayNow), invoicing, and shipping (Furgonetka).

The project is being modularized: integrations are extracted into separate libraries following the provider-api plugin pattern. Invoicing is fully extracted behind `invoicing-api`. Shipping is fully extracted behind `shipping-api`. PIM is fully extracted to a separate microservice. Payments, marketplace integrations, and multistore management will all be extracted, making the core platform simpler.

## Development Commands

```bash
mvn clean compile            # Build
mvn clean compile -Pdev      # Build (core only, no adapters)
mvn spring-boot:run          # Run
mvn spring-boot:run -Pdev    # Run (core only, no adapters)
mvn test -Dtest=ClassName    # Run specific test class
mvn test -Dtest=ClassName#methodName  # Run specific test method
```

### Maven Profiles

- **`full`** (default) — includes all dependencies including private supplier adapters
- **`dev`** (`-Pdev`) — core dependencies only, no private adapters. Use for local development when adapter repos aren't built locally.

### Local Infrastructure

**DynamoDB**: Runs locally via **AWS NoSQL Workbench** at `http://localhost:8000`.
**Other AWS services** (S3, SQS, etc.): Simulated by **LocalStack** at `http://localhost:4566`. Configuration in `application-local.properties`.

**Schema Migration**: Tables are created via scripts in `src/main/resources/local-init/dynamodb/`.
**Verify**: `aws dynamodb list-tables --endpoint-url http://localhost:8000`

## Coding Conventions

- **No Logger**: We log all entries to Sentry automatically. Use `System.out`/`System.err` only in rare cases.
- **No comments**: Code should be self-explanatory. Refactor instead of commenting.
- **UI**: Thymeleaf templates in `src/main/resources/templates/`, styled with Bulma CSS.
- **Email templates**: Rendered with Mustache.
- **Localization**: `LocalizedEnum` interface. Polish is primary language. Messages in `messages_pl.properties` / `messages_en.properties`.
- **DTOs**: Controllers use DTOs (in `web/dtos/`) with factory methods like `OrderDto.from(Order order)`.
- **Error handling**: `GlobalExceptionHandler` catches common exceptions. Sentry logs errors automatically.
- **CSV**: Use classes in `starter/csv/` (`CSVLoader`, `CSVWriter`, `CSVReady`).

## Project Structure

### Workspace Layout (`D:/Workspace/commercelink/`)

| Directory | Artifact | Description |
|-----------|----------|-------------|
| `app/` | `commercelink` | Main Spring Boot application |
| `commercelink-starter/` | `commercelink-starter` | Common infrastructure: AWS config, DynamoDB, email, security, REST, CSV, storage |
| `api/` | `commercelink-api` (2.1) | Public API contracts and shared DTOs |
| `pim-api/` | `pim-api` (0.1.0) | PIM client contract: `PimEntry`, `PimCatalog`, `PimIdentifier` — clients must implement this |
| `provider-api/` | `provider-api` (0.1.0) | Base plugin system: `ProviderDescriptor`, `ProviderField` |
| `invoicing-api/` | `invoicing-api` (0.2.0) | Invoicing provider interface: `InvoicingProvider`, `InvoicingProviderDescriptor` |
| `shipping-api/` | `shipping-api` | Shipping provider interface: `ShippingProvider`, `ShippingProviderDescriptor` |
| `localstack/` | - | Local S3 simulation data |

### Library Extraction Pattern

All extracted integrations follow this pattern:
1. `provider-api` defines `ProviderDescriptor<T>` with `name()`, `displayName()`, `configurationFields()`, `create(Map)`
2. Domain-specific API (e.g. `invoicing-api`) extends it with domain interfaces (e.g. `InvoicingProvider`)
3. Implementation libraries implement the interfaces
4. Registered via `META-INF/services/` for `ServiceLoader` discovery
5. Main app uses factory classes (e.g. `InvoicingProviderFactory`) to instantiate per-store providers

### Package Layout (`src/main/java/pl/commercelink/`)

| Package | Purpose |
|---------|---------|
| `starter/` | Extracted to `commercelink-starter` library. AWS config, DynamoDB, email, file ops, security, REST, CSV, localization, storage |
| `inventory/` | Feed loading, auto-discovery, supplier implementations (14 suppliers) |
| `products/` | Product catalog, health scoring, filters. PIM index fetched from external PIM microservice via `/PIM/Index` |
| `orders/` | Order lifecycle, fulfilment algorithms, RMA, notifications, events, imports |
| `invoicing/` | Invoice orchestration using extracted invoicing libraries |
| `payments/` | Stripe and PayNow integration with webhook handlers |
| `shipping/` | Shipping orchestration using extracted shipping libraries |
| `warehouse/` | Built-in warehouse: goods in/out, stock, reservations, document management |
| `pricelist/` | Pricelist generation, daily snapshots, price aggregates |
| `baskets/` | Shopping cart management |
| `checkout/` | Checkout flow and REST API |
| `stores/` | Store (tenant) configuration: branding, checkout, shipping, invoicing, RMA settings |
| `marketplaces/` | Morele (active) marketplace integration |
| `offer/` | Product offers and CSV offer import |
| `taxonomy/` | Product category taxonomy |
| `templates/` | Email template storage (DynamoDB-backed) |
| `documents/` | Document types and reasons |
| `financials/` | Financial reports, exchange rates, orders export |
| `web/` | Controllers and REST APIs |
| `exception/` | Global exception handling |
| `provider/` | Service provider configuration management |

### `starter/` Subpackages (formerly `infrastructure/`)

| Subpackage | Key Classes |
|------------|-------------|
| `autoconfigure/` | `DynamoDBConfig`, `S3Config`, `SqsConfig`, `SesConfig`, `SecretsManagerConfig`, `SchedulerConfig`, `CacheConfig`, `SentryConfig` |
| `dynamodb/` | `DynamoDbRepository<T>` (base), `DynamoDbSchema`, converters, `Metadata` |
| `security/` | `WebSecurityConfiguration`, `CognitoOAuthConfig`, `StoreAccessInterceptor`, `CustomOAuth2UserService`, `UserRole` |
| `email/` | `EmailClient` (SES), `EmailNotification`, `EmailAttachmentBuilder` |
| `rest/` | `RestApi`, `RestApiWithRetry`, `OAuth2AuthorizationService` |
| `csv/` | `CSVLoader`, `CSVWriter`, `CSVReady` |
| `file/` | `FileZipper`, `FtpFileDownloader`, `SftpFileDownloader`, `HttpFileDownloader` |
| `storage/` | `FileStorage`, `FileImageStorage` |
| `secrets/` | `SecretsManager`, `ParameterStore` |
| `localization/` | `LocalizedEnum`, `GlobalLocalizationHandler` |

## Terminology

- **Supplier**: A distributor or retailer that supplies goods/inventory (e.g. AbGroup, Action, IngramMicro). Defined by the `Supplier` enum.
- **Provider**: A service provider for payments, shipping, invoicing, etc. (e.g. Stripe, Furgonetka). Uses the `provider-api` plugin pattern.

## SQS Queue Naming Convention

Queue names follow the pattern: **`{module}-{domain}-{action}-queue[.fifo]`**

- **`{module}`** — module prefix: `app-` for main application, `pim-` for PIM microservice
- **`{domain}`** — business domain: `order`, `marketplace`, `pricing`, `inventory`, `product`, `basket`, `rma`, `taxonomy`, `warehouse`
- **`{action}`** — verb describing the operation: `generate`, `import`, `export`, `cleanup`, `notify`, `fulfil`, `process`, `load`, `submit`, `rebuild`, `sync`, `track`
- **`-queue`** — required suffix
- **`.fifo`** — only for FIFO queues (AWS requirement)

Rules:
- Always use singular nouns (e.g. `order` not `orders`, `notification` not `notifications`)
- Always use verb form for action (e.g. `generate` not `generator`, `notify` not `notifications`)
- Avoid redundancy (e.g. `app-product-cleanup-queue` not `app-product-cleanup-orphaned-products-queue`)
- Domain should reflect the actual business area, not legacy naming (e.g. `pricing` not `distributors`)

See `QUEUE_NAMES.md` for the full migration plan with current → proposed name mappings.

## Architecture

### Multi-Tenancy

The system is organized around `Store` entities. Each store has independent product catalogs, enabled suppliers, service provider configuration (payment/shipping/invoicing), branding, and RMA settings.

### DynamoDB Tables

All entities use `@DynamoDBTable`, `@DynamoDBHashKey`, `@DynamoDBRangeKey` annotations. Repositories extend `DynamoDbRepository<T>`.

| Table | Entity | Keys |
|-------|--------|------|
| Orders | `Order` | hash: storeId, range: orderId |
| OrderItems | `OrderItem` | hash: orderId, range: itemId |
| OrderEvents | `OrderEvent` | Event tracking per order |
| Products | `Product` | Product data |
| Catalogs | `ProductCatalog` | Category definitions |
| Baskets | `Basket` | Shopping carts |
| Stores | `Store` | Tenant configuration |
| Deliveries | `Delivery` | Supplier deliveries |
| RMA | `RMA` | Return merchandise |
| RMAItems | `RMAItem` | RMA line items |
| RMACenters | `RMACenter` | Return centers |
| WarehouseItems | `WarehouseItem` | Warehouse stock |
| WarehouseDocuments | `WarehouseDocument` | Warehouse documents |
| WarehouseDocumentItems | `WarehouseDocumentItem` | Document line items |
| WarehouseDocumentSequences | `WarehouseDocumentSequence` | Document numbering |
| EmailTemplates | `EmailTemplate` | Notification templates |

Product information lives in the external PIM microservice — the main app no longer owns a `PIM` DynamoDB table; it consumes the index via `/PIM/Index`.

### Inventory Suppliers

The `Supplier` enum defines all distributors/retailers with metadata (type, shipping calculator, accuracy score, locality, estimated arrival days):

AbGroup, Acadia, Action, Also, Amazon, Elko, IncomGroup, IngramMicro, Kosatec, Senetic, Wortmann, MxSolution, Proline, Morele, Warehouse (internal), Other (fallback)

Each supplier (except Amazon, Warehouse, Other) has a package under `inventory/suppliers/` with `FeedLoader`, `InventoryItem`, and `ProductFeedParser` implementations.

Feeds are reloaded every 5 minutes by `FeedReloaderScheduler`. Products are matched across suppliers by EAN and manufacturer code via `InventoryAutoDiscovery`.

### Order Lifecycle

1. **Checkout**: `Basket` via REST API → payment link (Stripe/PayNow) → `Order` creation
2. **Fulfilment**: Auto-allocation via `AutomatedOrderFulfilment` (strategies: `CheapestFulfilmentPathSelector`, `ShortestAndCheapestPathSelector`) or manual via `ManualOrderFulfilment`
3. **Shipping**: `Furgonetka` API for label generation and tracking
4. **Invoicing**: `InvoicingService` creates invoices via extracted provider libraries (proforma, standard, advance, final, credit notes)
5. **Notifications**: Email notifications via SES for each lifecycle stage

**Order statuses**: New -> InProgress -> Completed | Cancelled
**Fulfilment statuses**: Pending -> Ordered -> Received -> Allocated -> Shipped -> Delivered | Returned

### Event-Driven Processing

Most async work is driven through SQS listeners (20+ `@SqsListener` methods across the codebase). Key queues handle: feed loading, pricelist generation, PIM indexing, order fulfilment, notifications, marketplace sync, invoice creation, goods out, taxonomy generation, RMA lifecycle, basket cleanup, and more.

**Scheduled tasks** (`@Scheduled`):
- Every 5 min: `FeedReloaderScheduler` - reload inventory feeds
- Hourly: `PIMIndex.refreshCaches()` - refresh PIM cache

### Security

- OAuth2 authentication via AWS Cognito (configured in `CognitoOAuthConfig`)
- Roles: `ADMIN`, `SUPER_ADMIN`
- `@PreAuthorize` on controller methods
- `StoreAccessInterceptor` enforces tenant isolation
- `StoreApiKeyAuthorizationInterceptor` for API key auth

### AWS Services

| Service | Usage |
|---------|-------|
| DynamoDB | Primary database |
| S3 | Feed storage, PIM data, pricelists, images, stores |
| SQS | Async job queues (20+ listeners) |
| EventBridge Scheduler | Recurring pricelist/PIM generation |
| SES v2 | Email delivery |
| Secrets Manager | External API credentials |
| SSM Parameter Store | Configuration values |

Environment switching: `application.env=localhost` uses local DynamoDB and filesystem; `application.env=prod` uses AWS in eu-central-1.

## Key Entry Points

| Purpose | Path                                                                                                  |
|---------|-------------------------------------------------------------------------------------------------------|
| Application | `Application.java`                                                                                    |
| Security | `starter/security/config/WebSecurityConfiguration.java`                                               |
| DynamoDB config | `starter/autoconfigure/DynamoDBConfig.java`                                                           |
| Schema migration | `src/main/resources/local-init/dynamodb/`                                                                |
| Orders API | `web/OrdersController.java`                                                                           |
| Order lifecycle | `orders/OrderLifecycle.java`                                                                          |
| Fulfilment | `orders/fulfilment/AutomatedOrderFulfilment.java`                                                     |
| Inventory | `inventory/Inventory.java`                                                                            |
| Feed loading | `inventory/FeedReloaderScheduler.java`                                                                |
| Product catalog | `products/ProductCatalog.java`                                                                        |
| PIM | `products/information/PIMIndex.java` (fetches from external service) |
| Pricelist | `pricelist/PricelistEventListener.java`                                                               |
| Invoicing | `invoicing/InvoicingService.java`                                                                     |
| Shipping | `shipping/ShippingService.java`                                                                       |
| Payments | `payments/PaymentProviderFactory.java`                                                                |
| Warehouse | `warehouse/builtin/BuiltInWarehouse.java`                                                             |
| RMA | `orders/rma/RMAManager.java`                                                                          |
| Store config | `stores/Store.java`                                                                                   |

All paths relative to `src/main/java/pl/commercelink/`.

## External Integrations

| Service | Purpose | Status |
|---------|---------|--------|
| Furgonetka | Polish shipping aggregator | Extracted (behind `shipping-api`) |
| Stripe | Payment processing | Active (to be extracted) |
| PayNow | Payment processing (Polish) | Active (to be extracted) |
| Morele | Polish marketplace (orders + offers) | Active (to be extracted) |
| Google Sheets | Data import | Deprecated (dependency remains in pom.xml) |

## Testing

- JUnit 5 + Mockito 5.12.0
- Test files in `src/test/java` mirror source structure
- Currently minimal coverage (4 test files: pricelist, products)
- `mvn test -Dtest=ClassName#methodName`

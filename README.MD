# Project Setup

## Prerequisites

- Java 21
- Maven
- Docker
- AWS CLI

## Building Dependencies

The app depends on several CommerceLink libraries that must be cloned and built
locally before the app itself can compile. When using the **`dev`** profile (
`-Pdev`), you only need the core libraries listed below — adapter
implementations (suppliers, shipping, invoicing, payments, marketplaces) are
excluded. 

This will be improved in task https://github.com/commerce-link/.github/issues/3

### Required repositories for `-Pdev`

Clone each repo into a shared workspace directory (e.g.
`~/workspace/commercelink/`) and build them **in order** — each level can be
built in parallel, but all repos in a level must finish before the next level
starts.

#### Level 1 — No internal dependencies (build in parallel)

| Repository             | Artifact             | Version |
|------------------------|----------------------|---------|
| `commercelink-commons` | commercelink-commons | 0.1.0   |
| `rest-client`          | rest-client          | 0.1.0   |
| `provider-api`         | provider-api         | 0.1.0   |
| `commercelink-starter` | commercelink-starter | 0.1.0   |

#### Level 2 — Depend on Level 1 (build in parallel)

| Repository        | Artifact        | Version | Depends on            |
|-------------------|-----------------|---------|-----------------------|
| `pim-api`         | pim-api         | 0.1.0   | commons, provider-api |
| `supplier-api`    | supplier-api    | 0.1.0   | commons               |
| `invoicing-api`   | invoicing-api   | 0.2.0   | provider-api          |
| `shipping-api`    | shipping-api    | 0.1.0   | provider-api          |
| `payments-api`    | payments-api    | 0.1.0   | provider-api          |
| `marketplace-api` | marketplace-api | 0.1.0   | provider-api          |

#### Level 3 — Depends on Level 2

| Repository         | Artifact         | Version | Depends on |
|--------------------|------------------|---------|------------|
| `pim-commercelink` | pim-commercelink | 0.1.0   | pim-api    |

### Build steps

For each repo, in level order:

```sh
cd <repo-directory>
mvn clean install
```

Once all dependencies are installed in your local Maven repository, you can
build and run the app with `-Pdev`.

## AWS CLI Configuration

Configure AWS CLI for local services:

```sh
aws configure set aws_access_key_id test
aws configure set aws_secret_access_key test
aws configure set default.region eu-central-1
```

## Local Infrastructure

Local AWS services (S3, SQS, SES, Cognito, etc.) are simulated by
[Ministack](https://ministack.org/). DynamoDB runs in a separate container on
port 8000.

```sh
docker compose up
```

On first start, the bootstrap script (`localstack-init/01-bootstrap.sh`)
automatically creates S3 buckets, SQS queues, and a Cognito user pool with a
default admin user (`admin@commercelink.local` / `Admin123!`).

The generated Cognito credentials (pool ID, client ID, client secret) are
written to `.ministack-data/.cognito.env` and picked up automatically by
`application-local.properties` via `spring.config.import`.

DynamoDB data is persisted in `.dynamodb-data/` within the repo directory.

**To reset the database** (useful when troubleshooting schema migrations):

1. Stop the container: `docker compose down`
2. Delete `.dynamodb-data/shared-local-instance.db`
3. Start again: `docker compose up`

**After first start, run schema migration:**
Open `DynamoDbSchema.java` in your IDE and run its `main` method to create
tables.

**Verify:**

```sh
aws dynamodb list-tables --endpoint-url http://localhost:8000
curl http://localhost:4566/_localstack/health
```

## Maven Profiles

| Profile | Activation                  | Description                                            |
|---------|-----------------------------|--------------------------------------------------------|
| `full`  | Default (`activeByDefault`) | All dependencies including supplier adapters           |
| `dev`   | `-Pdev`                     | Core only, no private adapters — for local development |

## Running the Application

1. **Build the Project:**
   ```sh
   mvn clean install -Pdev
   ```

2. **Run the Application:**
   ```sh
   mvn spring-boot:run -Pdev
   ```

3. **Access the Application:**
   Open your browser and go to `http://localhost:8080`.

## Troubleshooting

- Verify that DynamoDB Local is running on port 8000.
- Check the application logs for any errors during startup.

For further assistance, refer to the official documentation of each tool.

```

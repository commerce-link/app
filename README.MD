# Project Setup

## Prerequisites

- Java 21
- Maven
- Docker
- AWS CLI

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

On first start, the bootstrap script (`resources/local-init/01-bootstrap.sh`)
automatically creates S3 buckets, SQS queues, and a Cognito user pool with
three default users (all linked to the seeded demo store `uma2dqukxr`):

| Email                              | Password    | Role          |
|------------------------------------|-------------|---------------|
| `admin@commercelink.local`         | `Admin123!` | `SUPER_ADMIN` |
| `store-admin@commercelink.local`   | `Admin123!` | `ADMIN`       |
| `store-user@commercelink.local`    | `User123!`  | `USER`        |

The generated Cognito credentials (pool ID, client ID, client secret) are
written to `.ministack-data/.cognito.env` and picked up automatically by
`application-localdev.properties` via `spring.config.import`.

DynamoDB Local in the default Compose file uses in-memory storage (`-inMemory`),
so table data is cleared when the `commercelink-dynamodb` container is recreated
(for example after `docker compose down`).

**Schema and demo data:** when you start the Spring application with the
`localdev` profile (see [Running the Application](#running-the-application)),
[Mongock](https://docs.mongock.io/) runs against DynamoDB: `V001` creates
tables, and `V002` inserts the demo store `uma2dqukxr` plus a sample order
(and line items), matching the former `02-dynamodb-seed.sh` payload. Without
`localdev`, only `V001` applies (no local seed).

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
   mvn spring-boot:run -Pdev "-Dspring-boot.run.profiles=localdev"
   ```
   The quotes around the `-D` argument are required on PowerShell (Windows) and harmless on bash, so the same command works on both.

3. **Access the Application:**
   Open your browser and go to `http://localhost:8080` and log in with one of the seeded users listed in [Local Infrastructure](#local-infrastructure).

## Troubleshooting

- Verify that DynamoDB Local is running on port 8000.
- Check the application logs for any errors during startup.

For further assistance, refer to the official documentation of each tool.


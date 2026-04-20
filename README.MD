
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

All local services (DynamoDB, S3, SQS, etc.) run via Docker Compose:

```sh
docker compose up
```

DynamoDB data is persisted in `.dynamodb-data/` within the repo directory.

**To reset the database** (useful when troubleshooting schema migrations):
1. Stop the container: `docker compose down`
2. Delete `.dynamodb-data/shared-local-instance.db`
3. Start again: `docker compose up`

**After first start, run schema migration:**
Open `DynamoDbSchema.java` in your IDE and run its `main` method to create tables.

**Verify:**
```sh
aws dynamodb list-tables --endpoint-url http://localhost:8000
curl http://localhost:4566/_localstack/health
```

## Additional Configuration

## Maven Profiles

| Profile | Activation | Description |
|---------|-----------|-------------|
| `full` | Default (`activeByDefault`) | All dependencies including supplier adapters |
| `dev` | `-Pdev` | Core only, no private adapters — for local development |

## Running the Application

1. **Build the Project:**
   ```sh
   mvn clean install
   ```
   Or for local dev without adapters:
   ```sh
   mvn clean install -Pdev
   ```

2. **Run the Application:**
   ```sh
   mvn spring-boot:run
   ```
   Or with dev profile:
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

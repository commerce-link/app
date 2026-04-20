
# Project Setup

## Prerequisites

- Java 21
- Maven
- Docker
- AWS CLI
- DynamoDb (Local)

## Installing Local DynamoDB

1. **Download DynamoDB Local from official AWS :**
   Download the DynamoDB Local .jar file from the [official AWS website](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/DynamoDBLocal.html).

2. **Run DynamoDB Local:**
   ```sh
   java -Djava.library.path=./DynamoDBLocal_lib -jar DynamoDBLocal.jar -sharedDb
   ```

3. **Run Schema Migration:**
   Open the `DynamoDbSchema.java` file in your favorite IDE and run its `main` method. This will set up the necessary tables.

4. **Verify Installation:**
   Open a new terminal and run:
   ```sh
   aws dynamodb list-tables --endpoint-url http://localhost:8000
   ```

Ensure these directories exist before running the application.

## Local AWS Infrastructure Setup with LocalStack
1. **Start LocalStack:**
```shell
docker run -d -p 4566:4566 --name localstack localstack/localstack
```
2. **Configure AWS CLI to Use LocalStack:**
   ```sh
   aws configure set aws_access_key_id test
   aws configure set aws_secret_access_key test
   aws configure set default.region eu-central-1
   ```
3. Create S3 buckets locally using AWS CLI
   ```sh
   aws --endpoint-url=http://localhost:4566 s3 mb s3://feeds
   aws --endpoint-url=http://localhost:4566 s3 mb s3://pim
   aws --endpoint-url=http://localhost:4566 s3 mb s3://pricelists
   aws --endpoint-url=http://localhost:4566 s3 mb s3://stores
   aws --endpoint-url=http://localhost:4566 s3 mb s3://datalake
   ```
4. Upload Local Feed Data to S3 
   Upload feed files from local directories to LocalStack S3:
    ```sh
   aws --endpoint-url=http://localhost:4566 s3 sync ../product-catalogue/feeds/ s3://commercelink-feeds/
   ```
5. Local SQS queues being automatically created by the application when it starts, so no manual setup is needed for SQS.

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

#!/bin/bash
# Seed DynamoDB tables with local development data.
# Runs as a docker-compose service after schema migration.
set -e

ENDPOINT="http://commercelink-dynamodb:8000"
REGION="eu-central-1"

ddb() {
  aws dynamodb "$@" --endpoint-url "$ENDPOINT" --region "$REGION" --no-cli-pager
}

echo "=== Seeding data ==="

STORE_ID="uma2dqukxr"

ddb put-item --table-name Stores --item '{
  "storeId": {"S": "'"$STORE_ID"'"},
  "name": {"S": "Demo Store"},
  "checkoutConfiguration": {"M": {
    "currency": {"S": "pln"},
    "successUrl": {"S": "http://localhost:8080/success/"},
    "cancelUrl": {"S": "http://localhost:8080/cancel"}
  }},
  "fulfilmentConfiguration": {"M": {
    "orderAssemblyDays": {"N": "2"},
    "orderRealizationDays": {"N": "5"},
    "automatedFulfilment": {"BOOL": false},
    "defaultFulfilmentType": {"S": "WarehouseFulfilment"}
  }},
  "billingDetails": {"M": {
    "companyName": {"S": "Demo Store Sp. z o.o."},
    "taxId": {"S": "1234567890"},
    "streetAndNumber": {"S": "ul. Testowa 1"},
    "postalCode": {"S": "00-001"},
    "city": {"S": "Warszawa"},
    "country": {"S": "PL"}
  }}
}'

echo "=== DynamoDB seed complete ==="

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
  "checkout": {"M": {
    "currency": {"S": "pln"},
    "successUrl": {"S": "http://localhost:8080/success/"},
    "cancelUrl": {"S": "http://localhost:8080/cancel"},
    "deliveryOptions": {"L": []}
  }},
  "fulfilment": {"M": {
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
  }},
  "invoicing": {"M": {}},
  "rma": {"M": {}},
  "warehouse": {"M": {}},
  "reporting": {"M": {}},
  "shipping": {"M": {}},
  "branding": {"M": {}},
  "clientNotifications": {"M": {}}
}'

echo "=== Seeding test order with items from acme-feed.csv ==="

ORDER_ID="00000000-1000-0000-0000-000000000001"
ORDER_ITEM_ID_1="00000000-1000-0000-0000-000000000101"
ORDER_ITEM_ID_2="00000000-1000-0000-0000-000000000102"
ORDERED_AT="$(date -u +"%Y-%m-%dT%H:%M:%S")"

ddb put-item --table-name Orders --item '{
  "storeId": {"S": "'"$STORE_ID"'"},
  "orderId": {"S": "'"$ORDER_ID"'"},
  "email": {"S": "test.customer@example.com"},
  "totalPrice": {"N": "1758.00"},
  "orderedAt": {"S": "'"$ORDERED_AT"'"},
  "orderRealizationDays": {"N": "5"},
  "emailNotificationsEnabled": {"BOOL": false},
  "status": {"S": "New"},
  "fulfilmentType": {"S": "WarehouseFulfilment"},
  "source": {"M": {
    "name": {"S": "Test"},
    "type": {"S": "WebStore"}
  }},
  "billingDetails": {"M": {
    "name": {"S": "Jan"},
    "surname": {"S": "Kowalski"},
    "streetAndNumber": {"S": "ul. Klienta 5/2"},
    "postalCode": {"S": "00-002"},
    "city": {"S": "Warszawa"},
    "country": {"S": "PL"},
    "email": {"S": "test.customer@example.com"},
    "phone": {"S": "+48123456789"}
  }},
  "shippingDetails": {"M": {
    "id": {"S": "00000000-1000-0000-0000-000000000201"},
    "name": {"S": "Jan"},
    "surname": {"S": "Kowalski"},
    "streetAndNumber": {"S": "ul. Klienta 5/2"},
    "postalCode": {"S": "00-002"},
    "city": {"S": "Warszawa"},
    "country": {"S": "PL"},
    "email": {"S": "test.customer@example.com"},
    "phone": {"S": "+48123456789"},
    "default": {"BOOL": true}
  }},
  "payments": {"L": [
    {"M": {
      "source": {"S": "BankTransfer"},
      "amount": {"N": "0"},
      "processingFee": {"N": "0"},
      "referenceNo": {"S": ""},
      "name": {"S": ""}
    }}
  ]},
  "shipments": {"L": []},
  "documents": {"L": []}
}'

ddb put-item --table-name OrderItems --item '{
  "orderId": {"S": "'"$ORDER_ID"'"},
  "itemId": {"S": "'"$ORDER_ITEM_ID_1"'"},
  "sku": {"S": "MFN-CLEAR-01"},
  "unitPrice": {"N": "1299.00"},
  "consolidated": {"BOOL": false},
  "category": {"S": "CPU"},
  "name": {"S": "AMD ClearEdge Pro X3D"},
  "qty": {"N": "1"},
  "ean": {"S": "5900000000001"},
  "mfn": {"S": "MFN-CLEAR-01"},
  "unitCost": {"N": "1000.00"},
  "tax": {"N": "1.23"},
  "status": {"S": "New"}
}'

ddb put-item --table-name OrderItems --item '{
  "orderId": {"S": "'"$ORDER_ID"'"},
  "itemId": {"S": "'"$ORDER_ITEM_ID_2"'"},
  "sku": {"S": "MFN-TWIN-01"},
  "unitPrice": {"N": "459.00"},
  "consolidated": {"BOOL": false},
  "category": {"S": "Memory"},
  "name": {"S": "G.Skill TwinMatch 32GB DDR5 Kit"},
  "qty": {"N": "1"},
  "ean": {"S": "5900000000003"},
  "mfn": {"S": "MFN-TWIN-01"},
  "unitCost": {"N": "330.00"},
  "tax": {"N": "1.23"},
  "status": {"S": "New"}
}'

echo "=== DynamoDB seed complete ==="

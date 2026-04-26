#!/bin/bash
# Create DynamoDB tables in local DynamoDB.
# Runs as a docker-compose service that depends on commercelink-dynamodb being healthy.
set -e

ENDPOINT="http://commercelink-dynamodb:8000"
REGION="eu-central-1"

ddb() {
  aws dynamodb "$@" --endpoint-url "$ENDPOINT" --region "$REGION" --no-cli-pager
}

create_table() {
  local name="$1"; shift
  if ddb describe-table --table-name "$name" > /dev/null 2>&1; then
    echo "Table $name already exists, skipping"
    return 0
  fi
  echo "Creating table $name..."
  ddb create-table --table-name "$name" "$@" --billing-mode PAY_PER_REQUEST
}

echo "=== Creating DynamoDB tables ==="

create_table Stores \
  --attribute-definitions AttributeName=storeId,AttributeType=S \
  --key-schema AttributeName=storeId,KeyType=HASH

create_table Products \
  --attribute-definitions \
    AttributeName=categoryId,AttributeType=S \
    AttributeName=productId,AttributeType=S \
    AttributeName=pimId,AttributeType=S \
  --key-schema AttributeName=categoryId,KeyType=HASH AttributeName=productId,KeyType=RANGE \
  --global-secondary-indexes \
    'IndexName=PimIdIndex,KeySchema=[{AttributeName=pimId,KeyType=HASH}],Projection={ProjectionType=INCLUDE,NonKeyAttributes=[productId]}'

create_table Catalogs \
  --attribute-definitions \
    AttributeName=storeId,AttributeType=S \
    AttributeName=catalogId,AttributeType=S \
  --key-schema AttributeName=storeId,KeyType=HASH AttributeName=catalogId,KeyType=RANGE

create_table Orders \
  --attribute-definitions \
    AttributeName=storeId,AttributeType=S \
    AttributeName=orderId,AttributeType=S \
    AttributeName=orderedAt,AttributeType=S \
    AttributeName=externalOrderId,AttributeType=S \
  --key-schema AttributeName=storeId,KeyType=HASH AttributeName=orderId,KeyType=RANGE \
  --global-secondary-indexes \
    'IndexName=StoreIdOrderedAtIndex,KeySchema=[{AttributeName=storeId,KeyType=HASH},{AttributeName=orderedAt,KeyType=RANGE}],Projection={ProjectionType=INCLUDE,NonKeyAttributes=[orderId,status,email,fulfilmentType]}' \
    'IndexName=ExternalOrderIdIndex,KeySchema=[{AttributeName=storeId,KeyType=HASH},{AttributeName=externalOrderId,KeyType=RANGE}],Projection={ProjectionType=INCLUDE,NonKeyAttributes=[orderId]}'

create_table OrderItems \
  --attribute-definitions \
    AttributeName=orderId,AttributeType=S \
    AttributeName=itemId,AttributeType=S \
  --key-schema AttributeName=orderId,KeyType=HASH AttributeName=itemId,KeyType=RANGE

create_table WarehouseItems \
  --attribute-definitions \
    AttributeName=storeId,AttributeType=S \
    AttributeName=itemId,AttributeType=S \
  --key-schema AttributeName=storeId,KeyType=HASH AttributeName=itemId,KeyType=RANGE

create_table Baskets \
  --attribute-definitions \
    AttributeName=storeId,AttributeType=S \
    AttributeName=basketId,AttributeType=S \
    AttributeName=createdAt,AttributeType=S \
  --key-schema AttributeName=storeId,KeyType=HASH AttributeName=basketId,KeyType=RANGE \
  --global-secondary-indexes \
    'IndexName=BasketCreatedAtIndex,KeySchema=[{AttributeName=storeId,KeyType=HASH},{AttributeName=createdAt,KeyType=RANGE}],Projection={ProjectionType=INCLUDE,NonKeyAttributes=[basketId,name,type,expiresAt]}'

create_table Deliveries \
  --attribute-definitions \
    AttributeName=storeId,AttributeType=S \
    AttributeName=deliveryId,AttributeType=S \
  --key-schema AttributeName=storeId,KeyType=HASH AttributeName=deliveryId,KeyType=RANGE

create_table EmailTemplates \
  --attribute-definitions \
    AttributeName=storeId,AttributeType=S \
    AttributeName=templateName,AttributeType=S \
  --key-schema AttributeName=storeId,KeyType=HASH AttributeName=templateName,KeyType=RANGE

create_table RMA \
  --attribute-definitions \
    AttributeName=storeId,AttributeType=S \
    AttributeName=rmaId,AttributeType=S \
  --key-schema AttributeName=storeId,KeyType=HASH AttributeName=rmaId,KeyType=RANGE

create_table RMAItems \
  --attribute-definitions \
    AttributeName=rmaId,AttributeType=S \
    AttributeName=rmaItemId,AttributeType=S \
  --key-schema AttributeName=rmaId,KeyType=HASH AttributeName=rmaItemId,KeyType=RANGE

create_table RMACenters \
  --attribute-definitions \
    AttributeName=storeId,AttributeType=S \
    AttributeName=rmaCenterId,AttributeType=S \
  --key-schema AttributeName=storeId,KeyType=HASH AttributeName=rmaCenterId,KeyType=RANGE

create_table WarehouseDocuments \
  --attribute-definitions \
    AttributeName=storeId,AttributeType=S \
    AttributeName=documentId,AttributeType=S \
    AttributeName=deliveryId,AttributeType=S \
    AttributeName=orderId,AttributeType=S \
    AttributeName=rmaId,AttributeType=S \
    AttributeName=createdAt,AttributeType=S \
  --key-schema AttributeName=storeId,KeyType=HASH AttributeName=documentId,KeyType=RANGE \
  --global-secondary-indexes \
    'IndexName=DeliveryIdIndex,KeySchema=[{AttributeName=storeId,KeyType=HASH},{AttributeName=deliveryId,KeyType=RANGE}],Projection={ProjectionType=ALL}' \
    'IndexName=CreatedAtIndex,KeySchema=[{AttributeName=storeId,KeyType=HASH},{AttributeName=createdAt,KeyType=RANGE}],Projection={ProjectionType=ALL}' \
    'IndexName=OrderIdIndex,KeySchema=[{AttributeName=storeId,KeyType=HASH},{AttributeName=orderId,KeyType=RANGE}],Projection={ProjectionType=ALL}' \
    'IndexName=RMAIdIndex,KeySchema=[{AttributeName=storeId,KeyType=HASH},{AttributeName=rmaId,KeyType=RANGE}],Projection={ProjectionType=ALL}'

create_table WarehouseDocumentSequences \
  --attribute-definitions \
    AttributeName=storeId,AttributeType=S \
    AttributeName=sequenceKey,AttributeType=S \
  --key-schema AttributeName=storeId,KeyType=HASH AttributeName=sequenceKey,KeyType=RANGE

create_table WarehouseDocumentItems \
  --attribute-definitions \
    AttributeName=documentId,AttributeType=S \
    AttributeName=itemId,AttributeType=S \
    AttributeName=deliveryId,AttributeType=S \
  --key-schema AttributeName=documentId,KeyType=HASH AttributeName=itemId,KeyType=RANGE \
  --global-secondary-indexes \
    'IndexName=DeliveryIdIndex,KeySchema=[{AttributeName=deliveryId,KeyType=HASH}],Projection={ProjectionType=ALL}'

create_table OrderEvents \
  --attribute-definitions \
    AttributeName=orderId,AttributeType=S \
    AttributeName=eventId,AttributeType=S \
    AttributeName=name,AttributeType=S \
  --key-schema AttributeName=orderId,KeyType=HASH AttributeName=eventId,KeyType=RANGE \
  --local-secondary-indexes \
    'IndexName=NameIndex,KeySchema=[{AttributeName=orderId,KeyType=HASH},{AttributeName=name,KeyType=RANGE}],Projection={ProjectionType=ALL}'

echo "=== DynamoDB schema migration complete ==="
ddb list-tables

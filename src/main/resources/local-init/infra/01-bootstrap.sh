#!/bin/bash
# Bootstrap AWS resources in LocalStack for local development
# This script runs automatically when LocalStack reaches "ready" state.
set -e

echo "=== Bootstrapping LocalStack resources ==="

# S3 Buckets
awslocal s3 mb s3://feeds           # s3.bucket.feeds
awslocal s3 mb s3://pricelists      # s3.bucket.pricelists
awslocal s3 mb s3://stores          # s3.bucket.stores
awslocal s3 mb s3://datalake        # s3.bucket.datalake
awslocal s3 mb s3://pim             # additional bucket used for PIM data

# S3 Data Sync
# These directories should exist at the repo level alongside docker-compose
awslocal s3 sync /local/s3/datalake s3://datalake/ --exclude ".gitkeep"
awslocal s3 sync /local/s3/feeds s3://feeds/ --exclude ".gitkeep"
awslocal s3 sync /local/s3/pim s3://pim/ --exclude ".gitkeep"
awslocal s3 sync /local/s3/pricelists s3://pricelists/ --exclude ".gitkeep"

# SQS Queues
# Spring Cloud AWS SQS auto-creates queues that are referenced by @SqsListener when the app starts
# so most queues don't need pre-creation in the bootstrap script.

## SQS Queues - FIFO (explicit create to ensure FifoQueue attribute is set)
awslocal sqs create-queue --queue-name order-goods-out-queue.fifo \
  --attributes FifoQueue=true,ContentBasedDeduplication=false

## SQS Queues - PIM-specific queues (manual SqsMessageListenerContainer, no auto-create)
awslocal sqs create-queue --queue-name pim-entry-added-queue
awslocal sqs create-queue --queue-name pim-entry-deleted-queue
awslocal sqs create-queue --queue-name pim-fetch-queue

# Cognito User Pool + App Client for local OAuth2 login
POOL_ID=$(awslocal cognito-idp create-user-pool \
  --pool-name commercelink-local \
  --auto-verified-attributes email \
  --query 'UserPool.Id' --output text)

CLIENT_ID=$(awslocal cognito-idp create-user-pool-client \
  --user-pool-id "$POOL_ID" \
  --client-name commercelink-app \
  --explicit-auth-flows ALLOW_USER_PASSWORD_AUTH ALLOW_REFRESH_TOKEN_AUTH \
  --allowed-o-auth-flows code \
  --allowed-o-auth-scopes openid email profile \
  --allowed-o-auth-flows-user-pool-client \
  --callback-urls '["http://localhost:8080/login/oauth2/code/cognito"]' \
  --logout-urls '["http://localhost:8080/logout-success"]' \
  --generate-secret \
  --query 'UserPoolClient.ClientId' --output text)

CLIENT_SECRET=$(awslocal cognito-idp describe-user-pool-client \
  --user-pool-id "$POOL_ID" \
  --client-id "$CLIENT_ID" \
  --query 'UserPoolClient.ClientSecret' --output text)

# Create default users for local development
awslocal cognito-idp admin-create-user \
  --user-pool-id "$POOL_ID" \
  --username admin@commercelink.local \
  --user-attributes Name=email,Value=admin@commercelink.local Name=email_verified,Value=true Name=name,Value=Admin Name=custom:role,Value=SUPER_ADMIN Name=custom:storeId,Value=uma2dqukxr \
  --temporary-password Admin123!

awslocal cognito-idp admin-set-user-password \
  --user-pool-id "$POOL_ID" \
  --username admin@commercelink.local \
  --password Admin123! \
  --permanent

awslocal cognito-idp admin-create-user \
  --user-pool-id "$POOL_ID" \
  --username store-admin@commercelink.local \
  --user-attributes Name=email,Value=store-admin@commercelink.local Name=email_verified,Value=true Name=name,Value=Store\ Admin Name=custom:role,Value=ADMIN Name=custom:storeId,Value=uma2dqukxr \
  --temporary-password Admin123!

awslocal cognito-idp admin-set-user-password \
  --user-pool-id "$POOL_ID" \
  --username store-admin@commercelink.local \
  --password Admin123! \
  --permanent

awslocal cognito-idp admin-create-user \
  --user-pool-id "$POOL_ID" \
  --username store-user@commercelink.local \
  --user-attributes Name=email,Value=store-user@commercelink.local Name=email_verified,Value=true Name=name,Value=Store\ User Name=custom:role,Value=USER Name=custom:storeId,Value=uma2dqukxr \
  --temporary-password User123!

awslocal cognito-idp admin-set-user-password \
  --user-pool-id "$POOL_ID" \
  --username store-user@commercelink.local \
  --password User123! \
  --permanent

# Write generated values to shared volume so the Spring app can pick them up
cat > /var/lib/localstack/.cognito.env <<EOF
COGNITO_USER_POOL_ID=$POOL_ID
COGNITO_CLIENT_ID=$CLIENT_ID
COGNITO_CLIENT_SECRET=$CLIENT_SECRET
EOF

echo ""
echo "=== Cognito local setup ==="
echo "User Pool ID:    $POOL_ID"
echo "Client ID:       $CLIENT_ID"
echo "Client Secret:   $CLIENT_SECRET"
echo "SUPER_ADMIN:     admin@commercelink.local / Admin123!"
echo "ADMIN:           store-admin@commercelink.local / Admin123! (store: uma2dqukxr)"
echo "USER:            store-user@commercelink.local / User123! (store: uma2dqukxr)"

echo "=== Bootstrap complete ==="
awslocal s3 ls
awslocal sqs list-queues

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
# awslocal s3 sync ./datalake s3://datalake/
# awslocal s3 sync ./feeds s3://feeds/
# awslocal s3 sync ./pim s3://pim/
# awslocal s3 sync ./pricelists s3://pricelists/

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

echo "=== LocalStack bootstrap complete ==="
awslocal s3 ls
awslocal sqs list-queues

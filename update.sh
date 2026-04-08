#!/bin/bash

set -x

source ./update.env

# Configuration Variables
APPLICATION_NAME="CommerceLink-Prod"
ENVIRONMENT_NAME="CommerceLink-Prod-Env"
REGION="eu-central-1"
JAR_PATH="target/*.jar"

# Color codes
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

## Stage changes
read -p "Enter commit message: " commit_message
git add .
git commit -m "$commit_message"
git push origin main

# Maven package
mvn clean package

# Find the JAR file
JAR_FILE=$(ls $JAR_PATH 2>/dev/null | head -1)
if [ -z "$JAR_FILE" ]; then
  echo -e "${RED}No JAR file found in $JAR_PATH${NC}"
  exit 1
fi
JAR_NAME=$(basename "$JAR_FILE")

read -p "Enter application version: " version

# Check if the version already exists
EXISTING_VERSIONS=$(aws elasticbeanstalk describe-application-versions --application-name "$APPLICATION_NAME" --query "ApplicationVersions[*].VersionLabel" --version-labels "$version" --output text)
if [ -n "$EXISTING_VERSIONS" ]; then
  echo -e "${RED}Version $version already exists. Update will be terminated in 5 seconds ...${NC}"
  sleep 5
  exit 1
fi

# Confirm deployment
read -p "$(echo -e "${YELLOW}Deploy version $version to ElasticBeanstalk? (yes/no): ${NC}")" confirm
if [[ ! "$confirm" =~ ^[Yy](es)?$ ]]; then
  echo -e "${RED}Deployment cancelled.${NC}"
  exit 0
fi

## Upload JAR file to S3
aws s3 cp "$JAR_FILE" "s3://$S3_BUCKET/"
if [ $? -ne 0 ]; then
  echo -e "${RED}Failed to upload JAR file to S3${NC}"
  exit 1
fi

sleep 10

# Deploy to ElasticBeanstalk
echo -e "${YELLOW}Deploying to ElasticBeanstalk...${NC}"
aws elasticbeanstalk create-application-version --application-name "$APPLICATION_NAME" --version-label "$version" --source-bundle S3Bucket="$S3_BUCKET",S3Key="$JAR_NAME"
aws elasticbeanstalk update-environment --environment-name "$ENVIRONMENT_NAME" --version-label "$version"

echo -e "${YELLOW}Waiting 30 seconds for deployment to complete...${NC}"
sleep 30

# Loop to check if the environment running version matches $version
while true; do
  CURRENT_VERSION=$(aws elasticbeanstalk describe-environments --environment-names "$ENVIRONMENT_NAME" --query "Environments[0].VersionLabel" --output text)
  if [ "$CURRENT_VERSION" == "$version" ]; then
    echo -e "${GREEN}Deployment successful. Environment is running version $version.${NC}"
    break
  else
    echo -e "${YELLOW}Current version is $CURRENT_VERSION. Waiting for version $version...${NC}"
    sleep 15
  fi
done

echo -e "${YELLOW}Checking if application is available at $ENDPOINT_URL${NC}"
MAX_ATTEMPTS=10  # Try for about 5 minutes
ATTEMPTS=0

while [ $ATTEMPTS -lt $MAX_ATTEMPTS ]; do
  HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" $ENDPOINT_URL)

  if [ "$HTTP_STATUS" == "200" ]; then
    echo -e "${GREEN}Application is up and running! (HTTP Status: $HTTP_STATUS)${NC}"
    break
  else
    ATTEMPTS=$((ATTEMPTS+1))
    echo -e "${YELLOW}Attempt $ATTEMPTS/$MAX_ATTEMPTS: Application not ready yet. HTTP Status: $HTTP_STATUS${NC}"

    if [ $ATTEMPTS -eq $MAX_ATTEMPTS ]; then
      echo -e "${RED}Application did not respond with status 200 after multiple attempts.${NC}"
      echo -e "${RED}Please check the application logs for possible issues.${NC}"
    else
      echo -e "${YELLOW}Waiting 15 seconds before next check...${NC}"
      sleep 15
    fi
  fi
done
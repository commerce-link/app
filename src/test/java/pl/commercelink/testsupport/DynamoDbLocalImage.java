package pl.commercelink.testsupport;

import org.testcontainers.utility.DockerImageName;

/**
 * The dynamodb-local image of every DynamoDB integration test, pinned so a new release cannot change the behaviour of
 * the tests between two builds of the same commit (CI pulls the image anew on every run).
 */
public final class DynamoDbLocalImage {

    public static final DockerImageName IMAGE = DockerImageName.parse("amazon/dynamodb-local:3.3.1");

    private DynamoDbLocalImage() {
    }
}

package com.boycottpro.users;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import com.boycottpro.models.Users;
import com.boycottpro.users.models.LoginForm;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.*;

public class LoginUserHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final String TABLE_NAME = "";
    private final DynamoDbClient dynamoDb;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LoginUserHandler() {
        this.dynamoDb = DynamoDbClient.create();
    }

    public LoginUserHandler(DynamoDbClient dynamoDb) {
        this.dynamoDb = dynamoDb;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        try {
            LoginForm form = objectMapper.readValue(event.getBody(), LoginForm.class);
            String username = form.getUsername_or_email().toLowerCase();
            String password = form.getPassword();
            if (username == null || username.isEmpty()
                    || password == null || password.isEmpty()) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withHeaders(Map.of("Content-Type", "application/json"))
                        .withBody("invalid input fields");
            }
            // Try by username first
            Users user = getUserByField("username", username, "username-index");

            if (user == null) {
                // Try by email
                user = getUserByField("email_addr", username, "emailaddr-index");
            }

            if (user == null) {
                return badRequest("user does not exist");
            }

            return successResponse(user);
        } catch (Exception e) {
            e.printStackTrace();
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("{\"error\": \"Unexpected server error: " + e.getMessage() + "\"}");
        }
    }
    private APIGatewayProxyResponseEvent badRequest(String message) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(400)
                .withHeaders(Map.of("Content-Type", "application/json"))
                .withBody("{\"error\": \"" + message + "\"}");
    }

    private APIGatewayProxyResponseEvent successResponse(Users user) throws JsonProcessingException {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(200)
                .withHeaders(Map.of("Content-Type", "application/json"))
                .withBody(objectMapper.writeValueAsString(user));
    }
    private Users getUserByField(String attribute, String value, String indexName) {
        try {
            QueryRequest queryRequest = QueryRequest.builder()
                    .tableName("users")
                    .indexName(indexName)
                    .keyConditionExpression(attribute + " = :v")
                    .expressionAttributeValues(Map.of(
                            ":v", AttributeValue.builder().s(value).build()
                    ))
                    .limit(1)
                    .build();

            QueryResponse result = dynamoDb.query(queryRequest);
            if (result.hasItems() && result.count() > 0) {
                return convertItemToUser(result.items().get(0));
            }
        } catch (DynamoDbException e) {
            System.err.println("DynamoDB query error: " + e.getMessage());
        }
        return null;
    }
    private Users convertItemToUser(Map<String, AttributeValue> item) {
        Users user = new Users();
        user.setUser_id(null);
        user.setEmail_addr(item.get("email_addr").s());
        user.setUsername(item.get("username").s());
        user.setCreated_ts(Long.parseLong(item.get("created_ts").n()));
        user.setPassword_hash("***");
        user.setPaying_user(item.get("paying_user").bool());
        return user;
    }

}
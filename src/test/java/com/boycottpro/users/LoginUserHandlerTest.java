package com.boycottpro.users;

import com.boycottpro.users.models.LoginForm;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class LoginUserHandlerTest {

    @Mock
    private DynamoDbClient dynamoDb;

    @Mock
    private Context context;

    @InjectMocks
    private LoginUserHandler handler;

    private ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testLoginByUsername_Success() throws Exception {
        LoginForm form = new LoginForm();
        form.setUsername_or_email("schnarbies");
        form.setPassword("irrelevant");

        String body = objectMapper.writeValueAsString(form);
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent().withBody(body);

        Map<String, AttributeValue> fakeUser = Map.of(
                "user_id", AttributeValue.fromS("user123"),
                "email_addr", AttributeValue.fromS("schnarbies@email.com"),
                "username", AttributeValue.fromS("schnarbies"),
                "created_ts", AttributeValue.fromN("100"),
                "paying_user", AttributeValue.fromBool(true)
        );

        QueryResponse mockResponse = QueryResponse.builder().count(1).items(fakeUser).build();
        when(dynamoDb.query(argThat((QueryRequest q) -> "username-index".equals(q.indexName())))).thenReturn(mockResponse);

        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains("schnarbies"));
    }

    @Test
    public void testLoginByEmail_Success() throws Exception {
        LoginForm form = new LoginForm();
        form.setUsername_or_email("s@example.com");
        form.setPassword("irrelevant");

        String body = objectMapper.writeValueAsString(form);
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent().withBody(body);

        // simulate username query returning 0
        QueryResponse emptyUsernameQuery = QueryResponse.builder().count(0).build();
        when(dynamoDb.query(argThat((QueryRequest q) -> q != null && "username-index".equals(q.indexName())))).thenReturn(emptyUsernameQuery);

        // simulate email query returning 1 match
        Map<String, AttributeValue> fakeUser = Map.of(
                "user_id", AttributeValue.builder().s("u-2").build(),
                "username", AttributeValue.builder().s("another").build(),
                "email_addr", AttributeValue.builder().s("s@example.com").build(),
                "created_ts", AttributeValue.fromN("100"),
                "paying_user", AttributeValue.fromBool(true)
        );
        QueryResponse emailResponse = QueryResponse.builder().count(1).items(fakeUser).build();
        when(dynamoDb.query(argThat((QueryRequest q) -> q != null && "emailaddr-index".equals(q.indexName())))).thenReturn(emailResponse);

        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains("another"));
    }

    @Test
    public void testLoginUserNotFound() throws Exception {
        LoginForm form = new LoginForm();
        form.setUsername_or_email("ghost");
        form.setPassword("irrelevant");

        String body = objectMapper.writeValueAsString(form);
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent().withBody(body);

        QueryResponse empty = QueryResponse.builder().count(0).build();
        when(dynamoDb.query(any(QueryRequest.class))).thenReturn(empty);

        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("user does not exist"));
    }
}

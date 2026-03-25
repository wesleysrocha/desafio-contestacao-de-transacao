package com.desafio.prevencao.config;

import io.awspring.cloud.sqs.config.SqsMessageListenerContainerFactory;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestConfiguration
@ActiveProfiles("test")
public class TestSqsConfig {

    @Bean
    @Primary
    public SqsAsyncClient sqsAsyncClient() {
        SqsAsyncClient mockClient = mock(SqsAsyncClient.class);

        when(mockClient.getQueueUrl(any(GetQueueUrlRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        GetQueueUrlResponse.builder()
                                .queueUrl("http://localhost:4566/000000000000/test-queue")
                                .build()));

        when(mockClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        ReceiveMessageResponse.builder().build()));

        when(mockClient.deleteMessage(any(DeleteMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        DeleteMessageResponse.builder().build()));

        when(mockClient.changeMessageVisibility(any(ChangeMessageVisibilityRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        ChangeMessageVisibilityResponse.builder().build()));

        when(mockClient.getQueueAttributes(any(GetQueueAttributesRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        GetQueueAttributesResponse.builder()
                                .attributes(Map.of(
                                        QueueAttributeName.VISIBILITY_TIMEOUT, "30",
                                        QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES, "0"
                                ))
                                .build()));

        when(mockClient.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        SendMessageResponse.builder()
                                .messageId("mock-msg-id")
                                .build()));

        when(mockClient.deleteMessageBatch(any(DeleteMessageBatchRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        DeleteMessageBatchResponse.builder().build()));

        return mockClient;
    }

    @Bean
    @Primary
    public SqsTemplate sqsTemplate(SqsAsyncClient sqsAsyncClient) {
        return mock(SqsTemplate.class);
    }

    @Bean(name = "defaultSqsListenerContainerFactory")
    @Primary
    public SqsMessageListenerContainerFactory<Object> defaultSqsListenerContainerFactory(
            SqsAsyncClient sqsAsyncClient) {
        return SqsMessageListenerContainerFactory
                .builder()
                .sqsAsyncClient(sqsAsyncClient)
                .configure(options -> options
                        .maxConcurrentMessages(1)
                        .maxMessagesPerPoll(1))
                .build();
    }
}

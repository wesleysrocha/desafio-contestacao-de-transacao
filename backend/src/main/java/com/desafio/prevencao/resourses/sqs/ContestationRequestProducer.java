package com.desafio.prevencao.resourses.sqs;

import com.desafio.prevencao.data.dto.SqsContestationMessage;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ContestationRequestProducer {

    private final SqsTemplate sqsTemplate;

    @Value("${sqs.queues.contestation-requests:contestation-requests}")
    private String queueName;

    public void send(SqsContestationMessage message) {
        log.info("[SQS-PRODUCER] Enviando para fila '{}' requestId={} correlationId={}",
                queueName, message.getRequestId(), message.getCorrelationId());

        sqsTemplate.send(to -> to
                .queue(queueName)
                .payload(message)
                .header("correlationId", message.getCorrelationId())
                .header("requestId", message.getRequestId()));

        log.info("[SQS-PRODUCER] Mensagem enviada com sucesso requestId={}", message.getRequestId());
    }
}

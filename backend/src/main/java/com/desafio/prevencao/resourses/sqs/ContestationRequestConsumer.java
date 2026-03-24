package com.desafio.prevencao.resourses.sqs;

import com.desafio.prevencao.data.dto.SqsContestationMessage;
import com.desafio.prevencao.service.ContestationProcessingService;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ContestationRequestConsumer {

    private final ContestationProcessingService processingService;

    @SqsListener("${sqs.queues.contestation-requests:contestation-requests}")
    public void consume(SqsContestationMessage message) {
        MDC.put("correlationId", message.getCorrelationId());
        MDC.put("requestId", message.getRequestId());

        log.info("[SQS-CONSUMER] Recebendo mensagem da fila contestation-requests. " +
                        "requestId={} contestationId={}",
                message.getRequestId(), message.getContestationId());
        try {
            processingService.processContestationRequest(message);
        } finally {
            MDC.remove("correlationId");
            MDC.remove("requestId");
        }
    }
}

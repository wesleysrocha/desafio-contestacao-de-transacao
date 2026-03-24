package com.desafio.prevencao.resourses.sqs;

import com.desafio.prevencao.data.dto.SqsResultMessage;
import com.desafio.prevencao.service.ContestationCallbackService;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ContestationResultConsumer {

    private final ContestationCallbackService callbackService;

    @SqsListener("${sqs.queues.contestation-results:contestation-results}")
    public void consume(SqsResultMessage message) {
        MDC.put("correlationId", message.getCorrelationId());
        MDC.put("requestId", message.getRequestId());

        log.info("[SQS-CONSUMER] Recebendo resultado da fila contestation-results. " +
                        "requestId={} success={}",
                message.getRequestId(), message.isSuccess());
        try {
            callbackService.processResult(message);
        } finally {
            MDC.remove("correlationId");
            MDC.remove("requestId");
        }
    }
}

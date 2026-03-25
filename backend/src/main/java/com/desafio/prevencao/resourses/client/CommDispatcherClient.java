package com.desafio.prevencao.resourses.client;

import com.desafio.prevencao.data.dto.SqsContestationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;


@Component
@RequiredArgsConstructor
@Slf4j
public class CommDispatcherClient {

    private final RestTemplate restTemplate;

    @Value("${integrations.comm-dispatcher.base-url:http://localhost:8081}")
    private String baseUrl;

    @Value("${integrations.comm-dispatcher.max-retries:3}")
    private int maxRetries;

    @Value("${integrations.comm-dispatcher.retry-delay:1000}")
    private long retryDelay;

    public void dispatch(SqsContestationMessage message) {
        String url = baseUrl + "/api/dispatch";
        Map<String, Object> body = buildDispatchBody(message);

        log.info("[COMM-DISPATCHER] Chamando serviço externo url={} requestId={}",
                url, message.getRequestId());

        int attempt = 0;
        while (attempt < maxRetries) {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("X-Correlation-Id", message.getCorrelationId());
                headers.set("X-Request-Id", message.getRequestId());

                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
                ResponseEntity<Void> response = restTemplate.exchange(url, HttpMethod.POST, entity, Void.class);

                if (response.getStatusCode().is2xxSuccessful()) {
                    log.info("[COMM-DISPATCHER] Dispatch realizado com sucesso requestId={}", message.getRequestId());
                    return;
                }
                log.warn("[COMM-DISPATCHER] Resposta não-2xx: {} requestId={}", response.getStatusCode(), message.getRequestId());
            } catch (RestClientException ex) {
                attempt++;
                log.warn("[COMM-DISPATCHER] Tentativa {}/{} falhou para requestId={}: {}",
                        attempt, maxRetries, message.getRequestId(), ex.getMessage());

                if (attempt >= maxRetries) {
                    throw new RuntimeException("Falha ao chamar comm-dispatcher após " + maxRetries + " tentativas", ex);
                }

                sleep(retryDelay * attempt);
            }
        }
    }

    private Map<String, Object> buildDispatchBody(SqsContestationMessage message) {
        Map<String, Object> body = new HashMap<>();
        body.put("requestId", message.getRequestId());
        body.put("contestationId", message.getContestationId());
        body.put("communicationType", message.getCommunicationType());
        body.put("payload", message.getPayload());
        body.put("correlationId", message.getCorrelationId());
        return body;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

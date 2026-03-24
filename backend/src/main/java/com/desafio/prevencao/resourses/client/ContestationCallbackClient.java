package com.desafio.prevencao.resourses.client;

import com.desafio.prevencao.data.dto.SqsResultMessage;
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
public class ContestationCallbackClient {

    private final RestTemplate restTemplate;

    @Value("${integrations.contestation-callback.base-url:http://localhost:8082}")
    private String baseUrl;

    @Value("${integrations.contestation-callback.max-retries:3}")
    private int maxRetries;

    @Value("${integrations.contestation-callback.retry-delay:1000}")
    private long retryDelay;

    public boolean executeCallback(SqsResultMessage result) {
        String url = baseUrl + "/api/callback";
        Map<String, Object> body = buildCallbackBody(result);

        log.info("[CALLBACK] Executando callback url={} requestId={}",
                url, result.getRequestId());

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("X-Correlation-Id", result.getCorrelationId());
                headers.set("X-Request-Id", result.getRequestId());

                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
                ResponseEntity<Void> response = restTemplate.exchange(url, HttpMethod.POST, entity, Void.class);

                if (response.getStatusCode().is2xxSuccessful()) {
                    log.info("[CALLBACK] Callback executado com sucesso requestId={}", result.getRequestId());
                    return true;
                }

                log.warn("[CALLBACK] Tentativa {}/{} retornou status {} para requestId={}",
                        attempt, maxRetries, response.getStatusCode(), result.getRequestId());

            } catch (RestClientException ex) {
                log.warn("[CALLBACK] Tentativa {}/{} falhou para requestId={}: {}",
                        attempt, maxRetries, result.getRequestId(), ex.getMessage());
            }

            if (attempt < maxRetries) {
                long delay = retryDelay * (long) Math.pow(2, attempt - 1);
                log.info("[CALLBACK] Aguardando {}ms antes da próxima tentativa...", delay);
                sleep(delay);
            }
        }

        log.error("[CALLBACK] Falha no callback após {} tentativas para requestId={}",
                maxRetries, result.getRequestId());
        return false;
    }

    private Map<String, Object> buildCallbackBody(SqsResultMessage result) {
        Map<String, Object> body = new HashMap<>();
        body.put("requestId", result.getRequestId());
        body.put("contestationId", result.getContestationId());
        body.put("correlationId", result.getCorrelationId());
        body.put("success", result.isSuccess());
        body.put("resultDetails", result.getResultDetails());
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

package com.desafio.prevencao.integration;

import com.desafio.prevencao.config.TestSqsConfig;
import com.desafio.prevencao.data.dto.CreateContestationRequest;
import com.desafio.prevencao.data.dto.CreateContestationResponse;
import com.desafio.prevencao.data.dto.SqsResultMessage;
import com.desafio.prevencao.domain.entity.ContestationRequest;
import com.desafio.prevencao.domain.enums.ContestationStatus;
import com.desafio.prevencao.domain.enums.ContestationType;
import com.desafio.prevencao.repositories.ContestationAuditLogRepository;
import com.desafio.prevencao.repositories.ContestationRequestRepository;
import com.desafio.prevencao.resourses.client.ContestationCallbackClient;
import com.desafio.prevencao.resourses.sqs.ContestationRequestProducer;
import com.desafio.prevencao.service.ContestationCallbackService;
import com.desafio.prevencao.service.ContestationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestSqsConfig.class)
@DisplayName("Contestation - Testes de Integração")
class ContestationIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ContestationRequestRepository requestRepository;

    @Autowired
    private ContestationAuditLogRepository auditLogRepository;

    @Autowired
    private ContestationService contestationService;

    @Autowired
    private ContestationCallbackService callbackService;

    @MockBean
    private ContestationRequestProducer producer;

    @MockBean
    private ContestationCallbackClient callbackClient;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        // Limpa banco a cada teste
        auditLogRepository.deleteAll();
        requestRepository.deleteAll();
    }


    @Nested
    @DisplayName("Fluxo completo: criar → consultar → cancelar")
    class FluxoCompleto {

        @Test
        @DisplayName("cria contestação, consulta por ID, cancela com sucesso")
        void criaConsultaECancela() throws Exception {
            // Arrange
            doNothing().when(producer).send(any());

            // 1. Cria contestação
            String createPayload = objectMapper.writeValueAsString(
                    buildCreateRequest("CONT-INT-001", new BigDecimal("250.00")));

            String responseBody = mockMvc.perform(post("/api/v1/contestations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createPayload))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.requestId").isNotEmpty())
                    .andExpect(jsonPath("$.status").value("EM_ANDAMENTO"))
                    .andReturn().getResponse().getContentAsString();

            String requestId = objectMapper.readTree(responseBody).get("requestId").asText();

            // Verifica persistência no banco
            assertThat(requestRepository.findById(requestId)).isPresent();
            assertThat(requestRepository.findById(requestId).get().getCommunicationStatus())
                    .isEqualTo(ContestationStatus.EM_ANDAMENTO);
            assertThat(auditLogRepository.findByRequestIdOrderByCreatedAtAsc(requestId)).hasSize(1);

            // 2. Consulta por requestId
            mockMvc.perform(get("/api/v1/contestations/{id}", requestId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.requestId").value(requestId))
                    .andExpect(jsonPath("$.contestationId").value("CONT-INT-001"))
                    .andExpect(jsonPath("$.communicationStatus").value("EM_ANDAMENTO"))
                    .andExpect(jsonPath("$.auditHistory").isArray());

            // 3. Cancela
            mockMvc.perform(post("/api/v1/contestations/{id}/cancel", requestId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.communicationStatus").value("CANCELADO"));

            ContestationRequest finalEntity = requestRepository.findById(requestId).orElseThrow();
            assertThat(finalEntity.getCommunicationStatus()).isEqualTo(ContestationStatus.CANCELADO);
        }
    }

    @Nested
    @DisplayName("Unicidade do contestationId")
    class Unicidade {

        @Test
        @DisplayName("contestationId duplicado retorna 409 e não persiste segundo registro")
        void duplicado_retorna409_naoPersisteSegundo() throws Exception {
            // Arrange
            doNothing().when(producer).send(any());

            String payload = objectMapper.writeValueAsString(
                    buildCreateRequest("CONT-DUP-INT", new BigDecimal("100.00")));

            // Primeira criação — deve ter sucesso
            mockMvc.perform(post("/api/v1/contestations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isAccepted());

            // Segunda criação com mesmo contestationId — deve retornar 409
            mockMvc.perform(post("/api/v1/contestations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("CONT-DUP-INT")));

            // Apenas um registro no banco
            assertThat(requestRepository.findAll()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Validação de campos obrigatórios")
    class ValidacaoCampos {

        @Test
        @DisplayName("amount inválido (zero) retorna 400")
        void amountZero_retorna400() throws Exception {
            mockMvc.perform(post("/api/v1/contestations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"contestationId\": \"CONT-VAL-001\", \"amount\": 0}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));

            assertThat(requestRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("contestationId em branco retorna 400")
        void contestationIdEmBranco_retorna400() throws Exception {
            mockMvc.perform(post("/api/v1/contestations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"contestationId\": \"\", \"amount\": 100}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));

            assertThat(requestRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("body vazio retorna 400")
        void bodyVazio_retorna400() throws Exception {
            mockMvc.perform(post("/api/v1/contestations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Cancelamento — regras de negócio")
    class CancelamentoRegras {

        @Test
        @DisplayName("cancelar contestação já cancelada é idempotente (retorna 200 com CANCELADO)")
        void cancelarJaCancelada_idempotente() throws Exception {
            // Arrange
            doNothing().when(producer).send(any());

            String payload = objectMapper.writeValueAsString(
                    buildCreateRequest("CONT-CANCEL-IDEM", new BigDecimal("50.00")));

            String responseBody = mockMvc.perform(post("/api/v1/contestations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isAccepted())
                    .andReturn().getResponse().getContentAsString();

            String requestId = objectMapper.readTree(responseBody).get("requestId").asText();

            // Cancela uma vez
            mockMvc.perform(post("/api/v1/contestations/{id}/cancel", requestId))
                    .andExpect(status().isOk());

            // Cancela de novo — deve ser idempotente
            mockMvc.perform(post("/api/v1/contestations/{id}/cancel", requestId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.communicationStatus").value("CANCELADO"));
        }

        @Test
        @DisplayName("cancelar contestação inexistente retorna 404")
        void cancelarInexistente_retorna404() throws Exception {
            mockMvc.perform(post("/api/v1/contestations/req-nao-existe/cancel"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Listagem paginada")
    class Listagem {

        @Test
        @DisplayName("sem registros: retorna página vazia com paginacao correta")
        void semRegistros_retornaPaginaVazia() throws Exception {
            mockMvc.perform(get("/api/v1/contestations")
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.pagination.totalElements").value(0))
                    .andExpect(jsonPath("$.pagination.pageNumber").value(0));
        }

        @Test
        @DisplayName("com múltiplos registros: retorna listagem e contagem correta")
        void comRegistros_retornaListagemCorreta() throws Exception {
            // Arrange — cria 3 contestações
            doNothing().when(producer).send(any());

            for (int i = 1; i <= 3; i++) {
                String payload = objectMapper.writeValueAsString(
                        buildCreateRequest("CONT-LIST-00" + i, new BigDecimal("100.00")));
                mockMvc.perform(post("/api/v1/contestations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                        .andExpect(status().isAccepted());
            }

            // Act & Assert
            mockMvc.perform(get("/api/v1/contestations")
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.pagination.totalElements").value(3))
                    .andExpect(jsonPath("$.pagination.totalPages").value(1));
        }

        @Test
        @DisplayName("com filtro de status EM_ANDAMENTO: retorna apenas correspondentes")
        void filtroStatus_retornaApenasCombinantes() throws Exception {
            // Arrange
            doNothing().when(producer).send(any());

            // Cria 2 contestações
            for (int i = 1; i <= 2; i++) {
                String payload = objectMapper.writeValueAsString(
                        buildCreateRequest("CONT-FILT-00" + i, new BigDecimal("100.00")));
                mockMvc.perform(post("/api/v1/contestations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                        .andExpect(status().isAccepted());
            }

            // Filtra por EM_ANDAMENTO
            mockMvc.perform(get("/api/v1/contestations")
                            .param("status", "EM_ANDAMENTO"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pagination.totalElements").value(2));

            // Filtra por SUCESSO (nenhum)
            mockMvc.perform(get("/api/v1/contestations")
                            .param("status", "SUCESSO"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pagination.totalElements").value(0));
        }
    }

    @Nested
    @DisplayName("Callback mock — processamento do resultado SQS")
    class CallbackMock {

        @Test
        @DisplayName("callback com sucesso: atualiza status para SUCESSO no banco")
        void callbackSucesso_atualizaStatusParaSucesso() throws Exception {
            // Arrange — cria contestação no banco
            doNothing().when(producer).send(any());
            when(callbackClient.executeCallback(any())).thenReturn(true);

            String payload = objectMapper.writeValueAsString(
                    buildCreateRequest("CONT-CALLBACK-OK", new BigDecimal("300.00")));

            String responseBody = mockMvc.perform(post("/api/v1/contestations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andReturn().getResponse().getContentAsString();

            String requestId = objectMapper.readTree(responseBody).get("requestId").asText();

            // Simula chegada da mensagem de resultado do SQS
            SqsResultMessage resultMessage = SqsResultMessage.builder()
                    .requestId(requestId)
                    .contestationId("CONT-CALLBACK-OK")
                    .correlationId("corr-test")
                    .success(true)
                    .resultDetails("Processado com sucesso pelo sistema externo")
                    .build();

            // Act — processa o resultado via serviço (como faria o @SqsListener)
            callbackService.processResult(resultMessage);

            // Assert — verifica no banco
            ContestationRequest entity = requestRepository.findById(requestId).orElseThrow();
            assertThat(entity.getCommunicationStatus()).isEqualTo(ContestationStatus.SUCESSO);
            assertThat(entity.getLastError()).isNull();

            // Verifica que o log de auditoria foi criado
            assertThat(auditLogRepository.findByRequestIdOrderByCreatedAtAsc(requestId)).hasSizeGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("callback com falha: atualiza status para CALLBACK_FALHA com lastError")
        void callbackFalha_atualizaParaCallbackFalha() throws Exception {
            // Arrange
            doNothing().when(producer).send(any());
            when(callbackClient.executeCallback(any())).thenReturn(false);

            String payload = objectMapper.writeValueAsString(
                    buildCreateRequest("CONT-CALLBACK-FAIL", new BigDecimal("150.00")));

            String responseBody = mockMvc.perform(post("/api/v1/contestations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andReturn().getResponse().getContentAsString();

            String requestId = objectMapper.readTree(responseBody).get("requestId").asText();

            SqsResultMessage resultMessage = SqsResultMessage.builder()
                    .requestId(requestId)
                    .contestationId("CONT-CALLBACK-FAIL")
                    .correlationId("corr-test")
                    .success(false)
                    .resultDetails("Timeout no sistema de destino")
                    .build();

            // Act
            callbackService.processResult(resultMessage);

            // Assert
            ContestationRequest entity = requestRepository.findById(requestId).orElseThrow();
            assertThat(entity.getCommunicationStatus()).isEqualTo(ContestationStatus.CALLBACK_FALHA);
            assertThat(entity.getLastError()).isNotBlank();
        }

        @Test
        @DisplayName("callback para requestId inexistente: não lança exceção nem cria registro")
        void callbackRequestIdInexistente_ignoraSemExcecao() {
            // Arrange
            SqsResultMessage resultMessage = SqsResultMessage.builder()
                    .requestId("req-inexistente-abc")
                    .contestationId("CONT-NAO-EXISTE")
                    .correlationId("corr-test")
                    .success(true)
                    .resultDetails("Processado")
                    .build();

            // Act & Assert — não deve lançar exceção
            org.assertj.core.api.Assertions.assertThatNoException()
                    .isThrownBy(() -> callbackService.processResult(resultMessage));

            verify(callbackClient, never()).executeCallback(any());
        }
    }

    private CreateContestationRequest buildCreateRequest(String contestationId, BigDecimal amount) {
        CreateContestationRequest req = new CreateContestationRequest();
        req.setContestationId(contestationId);
        req.setAmount(amount);
        return req;
    }
}

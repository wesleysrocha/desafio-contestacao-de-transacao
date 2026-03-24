package com.desafio.prevencao.controller;

import com.desafio.prevencao.data.dto.*;
import com.desafio.prevencao.domain.enums.ContestationStatus;
import com.desafio.prevencao.domain.enums.ContestationType;
import com.desafio.prevencao.middleware.GlobalExceptionHandler;
import com.desafio.prevencao.middleware.exception.BusinessException;
import com.desafio.prevencao.middleware.exception.ConflictException;
import com.desafio.prevencao.middleware.exception.ResourceNotFoundException;
import com.desafio.prevencao.service.ContestationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ContestationController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("ContestationController - Testes @WebMvcTest")
class ContestationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ContestationService contestationService;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Nested
    @DisplayName("POST /api/v1/contestations")
    class CreateContestation {

        @Test
        @DisplayName("sucesso: retorna 202 Accepted com requestId e status")
        void sucesso_retorna202() throws Exception {
            // Arrange
            CreateContestationRequest request = buildCreateRequest("CONT-001", new BigDecimal("150.00"));
            CreateContestationResponse response = CreateContestationResponse.builder()
                    .requestId("req-uuid")
                    .status(ContestationStatus.EM_ANDAMENTO)
                    .correlationId("corr-001")
                    .idempotent(false)
                    .build();

            when(contestationService.createContestation(any())).thenReturn(response);

            // Act & Assert
            mockMvc.perform(post("/api/v1/contestations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.requestId").value("req-uuid"))
                    .andExpect(jsonPath("$.status").value("EM_ANDAMENTO"))
                    .andExpect(jsonPath("$.correlationId").value("corr-001"))
                    .andExpect(jsonPath("$.idempotent").value(false));
        }

        @Test
        @DisplayName("conflito: contestationId duplicado retorna 409")
        void conflito_retorna409() throws Exception {
            // Arrange
            CreateContestationRequest request = buildCreateRequest("CONT-DUP", new BigDecimal("100.00"));
            when(contestationService.createContestation(any()))
                    .thenThrow(new ConflictException("Já existe uma contestação com contestationId: CONT-DUP"));

            // Act & Assert
            mockMvc.perform(post("/api/v1/contestations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.message").value(containsString("CONT-DUP")));
        }

        @Test
        @DisplayName("validação: contestationId ausente retorna 400")
        void validacao_contestationIdAusente_retorna400() throws Exception {
            // Act & Assert
            mockMvc.perform(post("/api/v1/contestations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"amount\": 100.00}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("validação: amount ausente retorna 400")
        void validacao_amountAusente_retorna400() throws Exception {
            // Act & Assert
            mockMvc.perform(post("/api/v1/contestations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"contestationId\": \"CONT-001\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("validação: amount zero retorna 400")
        void validacao_amountZero_retorna400() throws Exception {
            // Act & Assert
            mockMvc.perform(post("/api/v1/contestations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"contestationId\": \"CONT-001\", \"amount\": 0}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("validação: amount negativo retorna 400")
        void validacao_amountNegativo_retorna400() throws Exception {
            // Act & Assert
            mockMvc.perform(post("/api/v1/contestations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"contestationId\": \"CONT-001\", \"amount\": -10.00}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/contestations/{requestId}")
    class GetById {

        @Test
        @DisplayName("encontrado: retorna 200 com dados da contestação")
        void encontrado_retorna200() throws Exception {
            // Arrange
            ContestationResponse response = buildContestationResponse("req-001", "CONT-001",
                    ContestationStatus.EM_ANDAMENTO);
            when(contestationService.findById("req-001")).thenReturn(response);

            // Act & Assert
            mockMvc.perform(get("/api/v1/contestations/req-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.requestId").value("req-001"))
                    .andExpect(jsonPath("$.contestationId").value("CONT-001"))
                    .andExpect(jsonPath("$.communicationStatus").value("EM_ANDAMENTO"));
        }

        @Test
        @DisplayName("não encontrado: retorna 404")
        void naoEncontrado_retorna404() throws Exception {
            // Arrange
            when(contestationService.findById("req-404"))
                    .thenThrow(new ResourceNotFoundException("Contestação não encontrada: req-404"));

            // Act & Assert
            mockMvc.perform(get("/api/v1/contestations/req-404"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/contestations")
    class ListContestations {

        @Test
        @DisplayName("sem filtros: retorna 200 com página de resultados")
        void semFiltros_retorna200ComPagina() throws Exception {
            // Arrange
            ContestationResponse item = buildContestationResponse("req-001", "CONT-001",
                    ContestationStatus.EM_ANDAMENTO);
            Page<ContestationResponse> page = new PageImpl<>(List.of(item));

            when(contestationService.listContestations(anyInt(), anyInt(), any(), any(), any(), any()))
                    .thenReturn(page);

            // Act & Assert
            mockMvc.perform(get("/api/v1/contestations")
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.pagination.totalElements").value(1))
                    .andExpect(jsonPath("$.pagination.pageNumber").value(0))
                    .andExpect(jsonPath("$.pagination.pageSize").value(1));
        }

        @Test
        @DisplayName("com filtro de status: repassa filtro ao service")
        void comFiltroStatus_repassaAoService() throws Exception {
            // Arrange
            Page<ContestationResponse> empty = new PageImpl<>(List.of());
            when(contestationService.listContestations(anyInt(), anyInt(), any(), any(), any(), any()))
                    .thenReturn(empty);

            // Act & Assert
            mockMvc.perform(get("/api/v1/contestations")
                            .param("status", "SUCESSO")
                            .param("contestationId", "CONT-XYZ"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pagination.totalElements").value(0));

            verify(contestationService).listContestations(
                    eq(0), eq(10),
                    eq(ContestationStatus.SUCESSO),
                    eq("CONT-XYZ"),
                    isNull(), isNull());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/contestations/{requestId}/cancel")
    class CancelContestation {

        @Test
        @DisplayName("sucesso: retorna 200 com status CANCELADO")
        void sucesso_retorna200() throws Exception {
            // Arrange
            ContestationResponse response = buildContestationResponse("req-001", "CONT-001",
                    ContestationStatus.CANCELADO);
            when(contestationService.cancelContestation("req-001")).thenReturn(response);

            // Act & Assert
            mockMvc.perform(post("/api/v1/contestations/req-001/cancel"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.communicationStatus").value("CANCELADO"));
        }

        @Test
        @DisplayName("não encontrado: retorna 404")
        void naoEncontrado_retorna404() throws Exception {
            // Arrange
            when(contestationService.cancelContestation("req-404"))
                    .thenThrow(new ResourceNotFoundException("Contestação não encontrada: req-404"));

            // Act & Assert
            mockMvc.perform(post("/api/v1/contestations/req-404/cancel"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("status inválido (SUCESSO): retorna 422 Unprocessable Entity")
        void statusSucesso_retorna422() throws Exception {
            // Arrange
            when(contestationService.cancelContestation("req-001"))
                    .thenThrow(new BusinessException("Não é possível cancelar contestação com status SUCESSO"));

            // Act & Assert
            mockMvc.perform(post("/api/v1/contestations/req-001/cancel"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.status").value(422))
                    .andExpect(jsonPath("$.message").value(containsString("SUCESSO")));
        }

        @Test
        @DisplayName("status CALLBACK_FALHA: retorna 422 Unprocessable Entity")
        void statusCallbackFalha_retorna422() throws Exception {
            // Arrange
            when(contestationService.cancelContestation("req-001"))
                    .thenThrow(new BusinessException("Não é possível cancelar contestação com status CALLBACK_FALHA"));

            // Act & Assert
            mockMvc.perform(post("/api/v1/contestations/req-001/cancel"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.status").value(422));
        }
    }


    private CreateContestationRequest buildCreateRequest(String contestationId, BigDecimal amount) {
        CreateContestationRequest req = new CreateContestationRequest();
        req.setContestationId(contestationId);
        req.setAmount(amount);
        return req;
    }

    private ContestationResponse buildContestationResponse(String requestId, String contestationId,
                                                            ContestationStatus status) {
        return ContestationResponse.builder()
                .requestId(requestId)
                .contestationId(contestationId)
                .communicationType(ContestationType.CONTESTACAO_ABERTA)
                .communicationStatus(status)
                .payload("{\"amount\":100.0}")
                .correlationId("corr-test")
                .receivedAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .auditHistory(List.of())
                .build();
    }
}

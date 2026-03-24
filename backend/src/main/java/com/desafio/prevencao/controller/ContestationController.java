package com.desafio.prevencao.controller;

import com.desafio.prevencao.data.dto.*;
import com.desafio.prevencao.domain.enums.ContestationStatus;
import com.desafio.prevencao.service.ContestationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import com.desafio.prevencao.data.dto.PagedResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/contestations")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Contestations", description = "API de gerenciamento de contestações de fraude")
public class ContestationController {

    private final ContestationService contestationService;

    @PostMapping
    @Operation(
            summary = "Criar nova contestação",
            description = "Cria uma contestação do tipo CONTESTACAO_ABERTA. " +
                    "Retorna 202 Accepted com requestId para acompanhamento. "
    )
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Contestação recebida e enfileirada"),
            @ApiResponse(responseCode = "400", description = "Payload inválido"),
            @ApiResponse(responseCode = "422", description = "Erro de regra de negócio")
    })
    public ResponseEntity<CreateContestationResponse> createContestation(
            @Valid @RequestBody CreateContestationRequest request) {

        log.info("[CONTROLLER] POST /api/v1/contestations");
        CreateContestationResponse response = contestationService.createContestation(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/{requestId}")
    @Operation(
            summary = "Buscar contestação por requestId",
            description = "Retorna detalhes completos da contestação incluindo status, " +
                    "timestamps, lastError e histórico de auditoria."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contestação encontrada"),
            @ApiResponse(responseCode = "404", description = "Contestação não encontrada")
    })
    public ResponseEntity<ContestationResponse> getById(
            @Parameter(description = "ID único da contestação (requestId)", required = true)
            @PathVariable String requestId) {

        log.info("[CONTROLLER] GET /api/v1/contestations/{}", requestId);
        return ResponseEntity.ok(contestationService.findById(requestId));
    }

    @GetMapping
    @Operation(
            summary = "Listar contestações paginadas",
            description = "Lista contestações com paginação, ordenação por data de recebimento (DESC) " +
                    "e filtros opcionais por status, contestationId, customerId e intervalo de datas."
    )
    @ApiResponse(responseCode = "200", description = "Lista paginada retornada com sucesso")
    public ResponseEntity<PagedResponse<ContestationResponse>> listContestations(
            @Parameter(description = "Número da página (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamanho da página") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Filtro por status") @RequestParam(required = false) ContestationStatus status,
            @Parameter(description = "Filtro por contestationId") @RequestParam(required = false) String contestationId,
            @Parameter(description = "Data inicial (ISO format)") @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @Parameter(description = "Data final (ISO format)") @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate) {

        log.info("[CONTROLLER] GET /api/v1/contestations page={} size={} status={}", page, size, status);
        Page<ContestationResponse> result = contestationService.listContestations(
                page, size, status, contestationId, fromDate, toDate);
        return ResponseEntity.ok(PagedResponse.of(result));
    }

    @PostMapping("/{requestId}/cancel")
    @Operation(
            summary = "Cancelar contestação",
            description = "Cancela uma contestação com status EM_ANDAMENTO. " +
                    "Retorna 409 se já estiver em estado terminal que impede cancelamento."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contestação cancelada"),
            @ApiResponse(responseCode = "404", description = "Contestação não encontrada"),
            @ApiResponse(responseCode = "422", description = "Status não permite cancelamento")
    })
    public ResponseEntity<ContestationResponse> cancelContestation(
            @Parameter(description = "ID único da contestação", required = true)
            @PathVariable String requestId) {

        log.info("[CONTROLLER] POST /api/v1/contestations/{}/cancel", requestId);
        return ResponseEntity.ok(contestationService.cancelContestation(requestId));
    }
}

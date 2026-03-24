package com.desafio.prevencao.controller;

import com.desafio.prevencao.service.ContestationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin", description = "Endpoints administrativos para operações de suporte")
public class AdminController {

    private final ContestationService contestationService;

    @PostMapping("/replay/{requestId}")
    @Operation(
            summary = "Reenfileirar contestação para reprocessamento",
            description = "Reenfileira uma contestação na fila SQS contestation-requests para reprocessamento. " +
                    "Útil para recuperação manual de falhas. Registra auditoria da operação."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Replay iniciado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Contestação não encontrada")
    })
    public ResponseEntity<Map<String, String>> replayContestation(
            @Parameter(description = "ID da contestação para replay", required = true)
            @PathVariable String requestId) {

        log.info("[ADMIN] Replay solicitado para requestId={}", requestId);
        contestationService.replayContestation(requestId);

        return ResponseEntity.ok(Map.of(
                "message", "Replay iniciado com sucesso",
                "requestId", requestId
        ));
    }
}

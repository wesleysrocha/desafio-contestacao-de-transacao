package com.desafio.prevencao.domain.enums;

/**
 * Status da contestação no ciclo de vida do processamento.
 */
public enum ContestationStatus {
    /**
     * Contestação recebida e em processamento assíncrono.
     */
    EM_ANDAMENTO,

    /**
     * Contestação cancelada manualmente antes de ser processada.
     */
    CANCELADO,

    /**
     * Processamento concluído com sucesso (callback OK).
     */
    SUCESSO,

    /**
     * Falha no callback para o sistema de contestações após retries esgotados.
     */
    CALLBACK_FALHA
}

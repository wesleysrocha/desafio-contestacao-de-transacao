package com.desafio.prevencao.resourses.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Desafio Prevenção a Fraudes API")
                        .version("1.0.0")
                        .description("""
                                API REST para gerenciamento de contestações de fraude.

                                **Fluxo:**
                                1. Cria contestação (POST /api/v1/contestations) → status EM_ANDAMENTO
                                2. Backend enfileira na SQS contestation-requests
                                3. Consumer chama serviço externo (mock-comm-dispatcher)
                                4. Mock publica resultado em contestation-results
                                5. Consumer executa callback (mock-contestation-callback)
                                6. Status final: SUCESSO ou CALLBACK_FALHA

                                **Tipos:** CONTESTACAO_ABERTA

                                **Status:** EM_ANDAMENTO | CANCELADO | SUCESSO | CALLBACK_FALHA
                                """)
                        .contact(new Contact()
                                .name("Desafio Prevenção")
                                .email("dev@desafio.com"))
                        .license(new License().name("Internal")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Servidor Local")
                ));
    }
}

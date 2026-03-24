# 🛡️ Desafio Prevenção a Fraudes

Sistema completo de gerenciamento de **contestações de fraude**, composto por uma API REST em Java (Spring Boot), um frontend em Angular, filas assíncronas via SQS (LocalStack) e serviços externos simulados por mocks Node.js.

---

## 📋 Sumário

- [Visão Geral](#-visão-geral)
- [Arquitetura](#-arquitetura)
- [Tecnologias](#-tecnologias)
- [Estrutura do Projeto](#-estrutura-do-projeto)
- [Pré-requisitos](#-pré-requisitos)
- [Configuração de Ambiente](#️-configuração-de-ambiente)
- [Como Executar](#-como-executar)
- [API REST](#-api-rest)
- [Exemplos cURL](#-exemplos-de-curl)
- [Fluxo de Processamento](#-fluxo-de-processamento)
- [Status das Contestações](#-status-das-contestações)
- [Banco de Dados](#-banco-de-dados)
- [Mensageria SQS e DLQ](#-mensageria-sqs-e-dlq)
- [Testes](#-testes)
- [Variáveis de Ambiente](#-variáveis-de-ambiente)
- [Diagramas](#-diagramas)
- [Simulação de Falhas](#-simulação-de-falhas)

---

## 🎯 Visão Geral

O sistema permite criar, acompanhar e cancelar **contestações de fraude** de forma assíncrona. Quando uma contestação é submetida pela API ou pelo frontend, ela percorre o seguinte ciclo:

1. **Recebimento**: a API valida os dados, verifica unicidade do `contestationId` e persiste no banco SQLite com status `EM_ANDAMENTO`. Também verifica se o `amount` é maior que 0.
2. **Enfileiramento**: a mensagem é publicada na fila SQS `contestation-requests`.
3. **Processamento**: um consumer SQS lê a mensagem e chama o `mock-comm-dispatcher` via HTTP.
4. **Disparo externo**: o dispatcher simula o processamento por um sistema externo e publica o resultado na fila `contestation-results`.
5. **Callback**: um segundo consumer SQS lê o resultado, executa callback HTTP para o `mock-contestation-callback` com retry automático e atualiza o status final para `SUCESSO`.

Todo o ciclo é rastreado com audit logs de transição de status e um `correlationId` para rastreamento.

---

## 🏗️ Arquitetura

```
┌─────────────────────────────────────────────────────────────────┐
│                   FRONTEND (Angular :4200)                      │
│   Lista / Detalhe / Criação / Cancelamento de contestações      │
└─────────────────────────┬───────────────────────────────────────┘
                          │ HTTP (proxy :4200 → :8080)
┌─────────────────────────▼───────────────────────────────────────┐
│                  BACKEND (Spring Boot :8080)                    │
│   REST API · Spring Data JPA · SQS Producer/Consumer           │
│   Banco: SQLite (app.bb)                                        │
└───────┬─────────────────────────────────────┬───────────────────┘
        │ SQS Publish                          │ SQS Consume
        ▼                                      ▼
┌───────────────────────┐          ┌──────────────────────────────┐
│  contestation-        │          │  contestation-results        │
│  requests (+ DLQ)     │          │  (+ DLQ)                     │
└───────┬───────────────┘          └──────────────┬───────────────┘
        │ HTTP POST /api/dispatch                  │ HTTP POST /api/callback
        ▼                                         ▼
┌───────────────────────┐          ┌──────────────────────────────┐
│  mock-comm-dispatcher │──SQS────▶│  mock-contestation-callback  │
│  (Node.js :8081)      │  Publish │  (Node.js :8082)             │
└───────────────────────┘          └──────────────────────────────┘
             ▲
             └──── Todos via LocalStack SQS (:4566)
```

---

## 🔧 Tecnologias utilizadas 

| Camada | Tecnologia | Versão |
|--------|-----------|--------|
| Backend | Java + Spring Boot | 21 / 3.4.1 |
| Persistência | Spring Data JPA + SQLite | Hibernate 6 |
| Mensageria | Spring Cloud AWS SQS | 3.2.1 |
| Frontend | Angular | 15 |
| Mocks externos | Node.js + Express | 18+ |
| Infraestrutura local | LocalStack (SQS) | 3.0 |
| Orquestração | Docker Compose | 3.8 |
| Testes | JUnit 5 + Mockito + @WebMvcTest | Spring Boot 3.4.1 |
| Documentação API | Springdoc OpenAPI / Swagger UI | 2.7.0 |

---

## 📦 Pré-requisitos

| Software | Versão mínima | Verificar |
|----------|--------------|-----------|
| **Java JDK** | 21 | `java -version` |
| **Maven** | 3.9+ | `mvn -version` |
| **Node.js** | 18+ | `node -version` |
| **npm** | 9+ | `npm -version` |
| **Angular CLI** | 15 | `ng version` |
| **Docker** | 24+ | `docker -version` |
| **Docker Compose** | 2.x | `docker compose version` |

Instalar o Angular CLI globalmente (se necessário):

```bash
npm install -g @angular/cli@15
```

> **Windows/PowerShell:** se o `npm install` bloquear por política de execução:
> ```powershell
> Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
> ```

---

## ⚙️ Configuração de Ambiente

### Copiar arquivo de variáveis

Na **raiz do projeto**:

```bash
cp .env.example .env
```

Para o **backend** (opcional — os valores padrão já funcionam):

```bash
cp backend/.env.example backend/.env
```

O arquivo `.env` já contém todos os valores padrão para desenvolvimento local. Não é necessário alterar nada para executar o projeto pela primeira vez.

---

## 🚀 Como Executar

> Execute os passos **nesta ordem** para garantir que todos os serviços estejam prontos.

### 1. Infraestrutura (Docker)

Sobe o LocalStack (SQS), cria as filas automaticamente e inicia os dois mocks:

```bash
# Na raiz do projeto
docker compose up -d
```

Aguardar os serviços ficarem saudáveis:

```bash
docker compose ps
```

Saída esperada:

```
localstack-sqs              Up (healthy)   0.0.0.0:4566->4566/tcp
mock-comm-dispatcher        Up (healthy)   0.0.0.0:8081->8081/tcp
mock-contestation-callback  Up (healthy)   0.0.0.0:8082->8082/tcp
init-sqs                     
```


### 2. Backend (Spring Boot)
Executar projeto  
**Observação:** O banco SQLite é criado automaticamente em `backend/app.bb` na primeira execução. Foi utilizado SQLite para facilitar o desenvolvimento e ter persistência de dados. Em uma próxima versão, pode ser expandido para MySQL.

**URLs do backend:**

| Recurso | URL |
|---------|-----|
| API Base | http://localhost:8080/api/v1 |
| Swagger UI | http://localhost:8080/swagger-ui/index.html |

---

### 3. Frontend (Angular)

```bash
cd frontend
npm install
npm start 
ou 
ng serve
```

O comando `npm start` executa `ng serve --proxy-config proxy.conf.json`, que encaminha todas as chamadas `/api/*` para `http://localhost:8080`.

**Frontend disponível em:** http://localhost:4200

---

### Resumo de Portas

| Serviço | Porta | URL |
|---------|-------|-----|
| Frontend Angular | 4200 | http://localhost:4200 |
| Backend Spring Boot | 8080 | http://localhost:8080 |
| Swagger UI | 8080 | http://localhost:8080/swagger-ui/index.html |
| Mock Dispatcher | 8081 | http://localhost:8081/health |
| Mock Callback | 8082 | http://localhost:8082/health |
| LocalStack SQS | 4566 | http://localhost:4566 |

---

## 📡 API REST

Base URL: `http://localhost:8080/api/v1`

### Criar Contestação

```
POST /api/v1/contestations
```

**Body:**

```json
{
    "contestationId": "CONT-155",
    "description": "Transação não reconhecida",
    "amount": 10,
    "channel": "APP"
}
```

> Campos além de `contestationId` e `amount` são aceitos livremente e armazenados no campo `payload`.

**Validações:**
- `contestationId` — obrigatório, não pode ser em branco
- `amount` — obrigatório, deve ser maior que `0.00`
- `contestationId` duplicado retorna `409 Conflict`

**Resposta `202 Accepted`:**

```json
{
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "EM_ANDAMENTO",
  "correlationId": "uuid-gerado",
  "receivedAt": "2024-01-15T10:30:00",
  "idempotent": false
}
```

---

### Buscar por requestId

```
GET /api/v1/contestations/{requestId}
```

Retorna `200 OK` com dados completos + histórico de auditoria, ou `404` se não encontrado.

---

### Listar Contestações (paginado)

```
GET /api/v1/contestations?page=0&size=10&status=EM_ANDAMENTO&contestationId=CONT-123
```

| Parâmetro | Tipo | Padrão | Descrição |
|-----------|------|--------|-----------|
| `page` | int | 0 | Número da página |
| `size` | int | 10 | Itens por página |
| `status` | enum | — | Filtro por status |
| `contestationId` | string | — | Filtro por contestationId (contém) |
| `fromDate` | ISO datetime | — | Data/hora inicial |
| `toDate` | ISO datetime | — | Data/hora final |

**Resposta `200 OK`:**

```json
{
  "content": [ ... ],
  "pagination": {
    "pageNumber": 0,
    "pageSize": 10,
    "totalElements": 42,
    "totalPages": 5
  }
}
```

---

### Cancelar Contestação

```
POST /api/v1/contestations/{requestId}/cancel
```

Retorna `200 OK` com status `CANCELADO`. Erros: `404` não encontrado, `422` se já em `SUCESSO` ou `CALLBACK_FALHA`.

---

### Reprocessar (Replay)

```
POST /api/v1/contestations/{requestId}/replay
```

Reenfileira a contestação no SQS para reprocessamento. Útil para `CALLBACK_FALHA`.

---

### Códigos de Resposta

| Código | Situação |
|--------|----------|
| `202` | Contestação criada com sucesso |
| `400` | Campos inválidos (contestationId em branco, amount ≤ 0) |
| `404` | Contestação não encontrada |
| `409` | contestationId já existe |
| `422` | Regra de negócio violada (cancelar terminal) |
| `500` | Erro interno |

---

## 🔍 Exemplos de cURL

### Criar contestação

```bash
curl -X POST http://localhost:8080/api/v1/contestations \
  -H "Content-Type: application/json" \
  -H "X-Correlation-Id: minha-correlacao-123" \
  -d '{
    "contestationId": "CONT-155",
    "description": "Transação não reconhecida",
    "amount": 10,
    "channel": "APP"
}'
```

### Listar com filtros

```bash
# Lista básica
curl "http://localhost:8080/api/v1/contestations?page=0&size=10"

# Por status
curl "http://localhost:8080/api/v1/contestations?status=EM_ANDAMENTO"

# Por intervalo de datas
curl "http://localhost:8080/api/v1/contestations?fromDate=2024-01-01T00:00:00&toDate=2024-12-31T23:59:59"
```

### Consultar detalhe

```bash
curl http://localhost:8080/api/v1/contestations/550e8400-e29b-41d4-a716-446655440000
```

### Cancelar

```bash
curl -X POST http://localhost:8080/api/v1/contestations/550e8400-.../cancel
```

### Reprocessar

```bash
curl -X POST http://localhost:8080/api/v1/contestations/550e8400-.../replay
```

---

## 🔄 Fluxo de Processamento

```
[Cliente / Frontend]
       │
       │ POST /api/v1/contestations
       ▼
[ContestationController]
       ├─ Valida contestationId (único) e amount (> 0)
       ├─ Persiste: status = EM_ANDAMENTO
       ├─ Publica em SQS: contestation-requests
       └─ Retorna 202 com requestId
       │
       ▼ @SqsListener (contestation-requests)
[ContestationProcessingService]
       ├─ Busca entity no banco
       ├─ Se CANCELADO: ignora
       └─ HTTP POST → mock-comm-dispatcher:8081/api/dispatch
              │
              │ (processamento assíncrono 0.5–2.5s, 90% sucesso)
              └─ Publica resultado em SQS: contestation-results
       │
       ▼ @SqsListener (contestation-results)
[ContestationCallbackService]
       ├─ Busca entity no banco
       ├─ Se SUCESSO ou CANCELADO: ignora (idempotente)
       ├─ HTTP POST → mock-contestation-callback:8082/api/callback
       │   (retry com backoff exponencial, 3 tentativas)
       │   ├─ Sucesso: status = SUCESSO, lastError = null
       │   └─ Falha: status = CALLBACK_FALHA, lastError = detalhe
       └─ Persiste + Grava audit log
       │
       ▼
[SQLite] ← status final atualizado
```

---

## 📊 Status das Contestações

| Status | Descrição | Terminal? |
|--------|-----------|:---------:|
| `EM_ANDAMENTO` | Recebida e em processamento | ❌ |
| `SUCESSO` | Processamento e callback concluídos | ✅ |
| `CANCELADO` | Cancelada manualmente | ✅ |
| `CALLBACK_FALHA` | Callback falhou após todos os retries | ❌ |

> Contestações em `SUCESSO` ou `CANCELADO` não são reprocessadas. `CALLBACK_FALHA` pode ser reprocessada via `/replay`.

---

## 🗄️ Banco de Dados

O projeto usa **SQLite embarcado**. O arquivo `backend/app.bb` é criado automaticamente na primeira execução.

### Tabela `contestation_request`

| Coluna | Tipo | Descrição |
|--------|------|-----------|
| `id` | TEXT (UUID PK) | requestId único |
| `contestation_id` | TEXT UNIQUE | ID do sistema de origem |
| `communication_type` | TEXT | Sempre `CONTESTACAO_ABERTA` |
| `communication_status` | TEXT | Status atual |
| `payload` | TEXT (JSON) | Dados adicionais da contestação |
| `last_error` | TEXT | Último erro registrado |
| `correlation_id` | TEXT | ID de rastreamento distribuído |
| `created_at` | TEXT (ISO-8601) | Data/hora de criação |
| `updated_at` | TEXT (ISO-8601) | Data/hora da última atualização |

### Tabela `contestation_audit_log`

| Coluna | Tipo | Descrição |
|--------|------|-----------|
| `id` | TEXT (UUID PK) | ID do log |
| `request_id` | TEXT (FK) | Referência à contestação |
| `from_status` | TEXT | Status anterior |
| `to_status` | TEXT | Novo status |
| `message` | TEXT | Descrição da transição |
| `created_at` | TEXT (ISO-8601) | Data/hora da transição |

**Backup:** copie `backend/app.bb`.
**Reset:** apague `backend/app.bb` — será recriado na próxima inicialização.

---

## 📬 Mensageria SQS e DLQ

As filas são criadas automaticamente pelo `init-sqs.sh` na inicialização do Docker:

| Fila principal | DLQ | Uso |
|----------------|-----|-----|
| `contestation-requests` | `contestation-requests-dlq` | Novas contestações para processamento |
| `contestation-results` | `contestation-results-dlq` | Resultados do dispatcher para callback |

`maxReceiveCount=3` (configurável via `SQS_MAX_RECEIVE_COUNT`).

**Verificar fila contestation-requests via Docker:**

```bash
 docker exec localstack-sqs awslocal sqs get-queue-attributes   --queue-url http://localhost:4566/000000000000/contestation-requests   --attribute-names ApproximateNumberOfMessages ApproximateNumberOfMessagesNotVisible   --region us-east-1

```


---

## 🧪 Testes

Os testes **não precisam de Docker** — usam SQLite em memória e beans SQS mockados.

## 🌍 Variáveis de Ambiente

| Variável | Padrão | Descrição |
|----------|--------|-----------|
| `AWS_REGION` | `us-east-1` | Região AWS (LocalStack) |
| `AWS_ACCESS_KEY_ID` | `test` | Credencial fake para LocalStack |
| `AWS_SECRET_ACCESS_KEY` | `test` | Credencial fake para LocalStack |
| `LOCALSTACK_ENDPOINT` | `http://localhost:4566` | Endpoint SQS |
| `SQLITE_PATH` | `app.bb` | Caminho do arquivo SQLite |
| `COMM_DISPATCHER_BASE_URL` | `http://localhost:8081` | URL mock dispatcher |
| `CONTESTATION_CALLBACK_BASE_URL` | `http://localhost:8082` | URL mock callback |
| `SQS_MAX_RECEIVE_COUNT` | `3` | Tentativas antes de ir para DLQ |
| `CALLBACK_FAILURE_RATE` | `0` | Taxa de falha do callback (0 = nunca, 1.0 = sempre) |

---

## 📐 Diagramas

Os diagramas abaixo estão disponíveis na raiz do projeto:

| Arquivo | Conteúdo |
|---------|----------|
| `usecase.png` | Diagrama de casos de uso |
| `database-diagram.png` | Diagrama relacional do banco de dados |
| `api-architecture.png` | Arquitetura interna da API com fluxo |
| `flow-architecture.png` | Fluxo completo de processamento |
| `api-frontend-architecture.png` | Integração API ↔ Frontend |
| `system-architecture.png` | Arquitetura completa do sistema |

---

## 🔥 Simulação de Falhas

### Testar status CALLBACK_FALHA

Edite o `.env` para 100% de falha no callback:

```bash
CALLBACK_FAILURE_RATE=1.0
```

Reinicie o container:

```bash
docker compose up -d mock-contestation-callback
```

Crie uma contestação. Após o processamento, o status será `CALLBACK_FALHA` com `lastError` preenchido. Para reprocessar, use o endpoint `/replay`.

### Testar DLQ

Configure `SQS_MAX_RECEIVE_COUNT=1` no `.env` e reinicie a infraestrutura:

```bash
docker compose down && docker compose up -d
```

Após uma falha no processamento, a mensagem vai direto para a DLQ após 1 tentativa.

---

## ⚠️ Observações


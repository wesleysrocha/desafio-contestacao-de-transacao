#!/bin/bash
# Script de inicialização das filas SQS no LocalStack
# Executado automaticamente durante o startup do container localstack

set -e

ENDPOINT="http://localhost:4566"
REGION="us-east-1"
MAX_RECEIVE_COUNT="${SQS_MAX_RECEIVE_COUNT:-3}"

echo "========================================"
echo "[INIT-SQS] Iniciando criação das filas SQS..."
echo "[INIT-SQS] Endpoint: $ENDPOINT"
echo "[INIT-SQS] Região: $REGION"
echo "[INIT-SQS] MaxReceiveCount DLQ: $MAX_RECEIVE_COUNT"
echo "========================================"

# Aguarda LocalStack estar pronto
until awslocal sqs list-queues --region "$REGION" > /dev/null 2>&1; do
    echo "[INIT-SQS] Aguardando LocalStack..."
    sleep 1
done

echo "[INIT-SQS] LocalStack está pronto!"

# ==========================================
# Criar DLQs primeiro
# ==========================================
echo "[INIT-SQS] Criando DLQ: contestation-requests-dlq"
awslocal sqs create-queue \
    --queue-name "contestation-requests-dlq" \
    --region "$REGION" \
    --attributes '{
        "MessageRetentionPeriod": "1209600",
        "VisibilityTimeout": "60"
    }' || echo "[INIT-SQS] contestation-requests-dlq já existe"

echo "[INIT-SQS] Criando DLQ: contestation-results-dlq"
awslocal sqs create-queue \
    --queue-name "contestation-results-dlq" \
    --region "$REGION" \
    --attributes '{
        "MessageRetentionPeriod": "1209600",
        "VisibilityTimeout": "60"
    }' || echo "[INIT-SQS] contestation-results-dlq já existe"

# ==========================================
# Obter ARNs das DLQs
# ==========================================
DLQ_REQUESTS_URL=$(awslocal sqs get-queue-url --queue-name "contestation-requests-dlq" --region "$REGION" --query 'QueueUrl' --output text)
DLQ_RESULTS_URL=$(awslocal sqs get-queue-url --queue-name "contestation-results-dlq" --region "$REGION" --query 'QueueUrl' --output text)

DLQ_REQUESTS_ARN=$(awslocal sqs get-queue-attributes --queue-url "$DLQ_REQUESTS_URL" --attribute-names QueueArn --region "$REGION" --query 'Attributes.QueueArn' --output text)
DLQ_RESULTS_ARN=$(awslocal sqs get-queue-attributes --queue-url "$DLQ_RESULTS_URL" --attribute-names QueueArn --region "$REGION" --query 'Attributes.QueueArn' --output text)

echo "[INIT-SQS] DLQ requests ARN: $DLQ_REQUESTS_ARN"
echo "[INIT-SQS] DLQ results ARN: $DLQ_RESULTS_ARN"

# ==========================================
# Criar filas principais com Redrive Policy
# ==========================================
echo "[INIT-SQS] Criando fila principal: contestation-requests"
awslocal sqs create-queue \
    --queue-name "contestation-requests" \
    --region "$REGION" \
    --attributes "{
        \"VisibilityTimeout\": \"60\",
        \"MessageRetentionPeriod\": \"86400\",
        \"RedrivePolicy\": \"{\\\"deadLetterTargetArn\\\":\\\"${DLQ_REQUESTS_ARN}\\\",\\\"maxReceiveCount\\\":\\\"${MAX_RECEIVE_COUNT}\\\"}\"
    }" || echo "[INIT-SQS] contestation-requests já existe"

echo "[INIT-SQS] Criando fila principal: contestation-results"
awslocal sqs create-queue \
    --queue-name "contestation-results" \
    --region "$REGION" \
    --attributes "{
        \"VisibilityTimeout\": \"60\",
        \"MessageRetentionPeriod\": \"86400\",
        \"RedrivePolicy\": \"{\\\"deadLetterTargetArn\\\":\\\"${DLQ_RESULTS_ARN}\\\",\\\"maxReceiveCount\\\":\\\"${MAX_RECEIVE_COUNT}\\\"}\"
    }" || echo "[INIT-SQS] contestation-results já existe"

# ==========================================
# Listar filas criadas
# ==========================================
echo ""
echo "========================================"
echo "[INIT-SQS] Filas criadas com sucesso!"
echo "========================================"
awslocal sqs list-queues --region "$REGION"
echo "========================================"

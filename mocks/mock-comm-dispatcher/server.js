const express = require('express');
const { SQSClient, SendMessageCommand } = require('@aws-sdk/client-sqs');

const app = express();
app.use(express.json());

const PORT = process.env.PORT || 8081;
const LOCALSTACK_ENDPOINT = process.env.LOCALSTACK_ENDPOINT || 'http://localstack:4566';
const AWS_REGION = process.env.AWS_REGION || 'us-east-1';
const RESULTS_QUEUE_URL = process.env.RESULTS_QUEUE_URL ||
    `${LOCALSTACK_ENDPOINT}/000000000000/contestation-results`;

const sqsClient = new SQSClient({
    endpoint: LOCALSTACK_ENDPOINT,
    region: AWS_REGION,
    credentials: {
        accessKeyId: process.env.AWS_ACCESS_KEY_ID || 'test',
        secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY || 'test',
    },
});

/**
 * Recebe a solicitação de disparo do backend e simula o processamento.
 * Publica o resultado na fila SQS contestation-results.
 */
app.post('/api/dispatch', async (req, res) => {
    const { requestId, contestationId, communicationType, payload, correlationId } = req.body;

    console.log(`[MOCK-DISPATCHER] Recebendo disparo: requestId=${requestId} contestationId=${contestationId}`);

    if (!requestId || !contestationId) {
        console.error('[MOCK-DISPATCHER] Payload inválido');
        return res.status(400).json({ error: 'requestId e contestationId são obrigatórios' });
    }

    // Simula processamento assíncrono com delay
    const processingDelay = Math.floor(Math.random() * 2000) + 500;
    console.log(`[MOCK-DISPATCHER] Processando em ${processingDelay}ms...`);

    setTimeout(async () => {
        try {
            // Simula sucesso em 90% dos casos
            const success = Math.random() > 0.1;

            const resultMessage = {
                requestId,
                contestationId,
                correlationId,
                success,
                resultDetails: success
                    ? `Contestação ${contestationId} processada com sucesso pelo serviço externo`
                    : `Falha temporária no processamento da contestação ${contestationId}`
            };

            const command = new SendMessageCommand({
                QueueUrl: RESULTS_QUEUE_URL,
                MessageBody: JSON.stringify(resultMessage),
                MessageAttributes: {
                    correlationId: {
                        DataType: 'String',
                        StringValue: correlationId || 'no-correlation',
                    },
                    requestId: {
                        DataType: 'String',
                        StringValue: requestId,
                    },
                },
            });

            await sqsClient.send(command);
            console.log(`[MOCK-DISPATCHER] Resultado publicado em contestation-results: requestId=${requestId} success=${success}`);

        } catch (err) {
            console.error(`[MOCK-DISPATCHER] Erro ao publicar resultado na SQS: ${err.message}`);
        }
    }, processingDelay);

    // Responde imediatamente ao backend
    res.status(202).json({
        message: 'Disparo recebido e em processamento',
        requestId,
        acceptedAt: new Date().toISOString()
    });
});

app.get('/health', (req, res) => {
    res.json({ status: 'UP', service: 'mock-comm-dispatcher', timestamp: new Date().toISOString() });
});

app.listen(PORT, () => {
    console.log(`[MOCK-DISPATCHER] Servidor rodando na porta ${PORT}`);
    console.log(`[MOCK-DISPATCHER] LocalStack endpoint: ${LOCALSTACK_ENDPOINT}`);
    console.log(`[MOCK-DISPATCHER] Results queue: ${RESULTS_QUEUE_URL}`);
});

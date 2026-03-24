const express = require('express');

const app = express();
app.use(express.json());

const PORT = process.env.PORT || 8082;

// Controle para simular falhas (configurável via variável de ambiente)
// FAILURE_RATE=0.3 = 30% de falhas (para testar CALLBACK_FALHA)
const FAILURE_RATE = parseFloat(process.env.FAILURE_RATE || '0');

// Contador de callbacks recebidos (para debug/observabilidade)
const receivedCallbacks = [];

/**
 * Recebe o callback do backend com o resultado final da contestação.
 * Pode simular falhas para testar o fluxo de CALLBACK_FALHA.
 */
app.post('/api/callback', (req, res) => {
    const { requestId, contestationId, correlationId, success, resultDetails } = req.body;

    console.log(`[MOCK-CALLBACK] Callback recebido: requestId=${requestId} success=${success}`);
    console.log(`[MOCK-CALLBACK] Detalhes: contestationId=${contestationId}`);

    // Registra o callback
    const callbackRecord = {
        requestId,
        contestationId,
        correlationId,
        success,
        resultDetails,
        receivedAt: new Date().toISOString()
    };
    receivedCallbacks.push(callbackRecord);

    // Simula falha aleatória baseada em FAILURE_RATE
    if (Math.random() < FAILURE_RATE) {
        console.warn(`[MOCK-CALLBACK] SIMULANDO FALHA para requestId=${requestId} (FAILURE_RATE=${FAILURE_RATE})`);
        return res.status(503).json({
            error: 'Serviço temporariamente indisponível (simulação)',
            requestId
        });
    }

    console.log(`[MOCK-CALLBACK] Callback processado com sucesso requestId=${requestId}`);
    res.status(200).json({
        message: 'Callback recebido e processado com sucesso',
        requestId,
        processedAt: new Date().toISOString()
    });
});

// Endpoint para visualizar callbacks recebidos (debug/observabilidade)
app.get('/api/callbacks', (req, res) => {
    res.json({
        total: receivedCallbacks.length,
        callbacks: receivedCallbacks.slice(-50) // últimos 50
    });
});

// Endpoint para configurar a taxa de falha em runtime
app.post('/api/config/failure-rate', (req, res) => {
    const { rate } = req.body;
    if (typeof rate !== 'number' || rate < 0 || rate > 1) {
        return res.status(400).json({ error: 'rate deve ser um número entre 0 e 1' });
    }
    process.env.FAILURE_RATE = String(rate);
    console.log(`[MOCK-CALLBACK] Taxa de falha atualizada para ${rate * 100}%`);
    res.json({ message: `Taxa de falha atualizada para ${rate * 100}%`, rate });
});

app.get('/health', (req, res) => {
    res.json({
        status: 'UP',
        service: 'mock-contestation-callback',
        failureRate: FAILURE_RATE,
        totalCallbacksReceived: receivedCallbacks.length,
        timestamp: new Date().toISOString()
    });
});

app.listen(PORT, () => {
    console.log(`[MOCK-CALLBACK] Servidor rodando na porta ${PORT}`);
    console.log(`[MOCK-CALLBACK] Taxa de falha simulada: ${FAILURE_RATE * 100}%`);
    console.log(`[MOCK-CALLBACK] Para simular CALLBACK_FALHA, defina FAILURE_RATE=1.0`);
});

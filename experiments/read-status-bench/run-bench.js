const { Kafka } = require('kafkajs');
const { Pool } = require('pg');
const { performance } = require('perf_hooks');

const MSG_COUNT = 5000;
const BATCH_SIZE = 100;

// Configuration
const dbConfig = {
    user: 'user', host: 'localhost', database: 'benchdb', password: 'password', port: 5432,
    max: 50 // Connection pool size
};

const kafka = new Kafka({ clientId: 'bench-app', brokers: ['localhost:9092'], logLevel: 0 }); 
const producer = kafka.producer({
    allowAutoTopicCreation: true,
    transactionTimeout: 30000
});
const consumer = kafka.consumer({ groupId: 'bench-group' });
// Optimization: Send as batch
// const producer = kafka.producer();

async function sleep(ms) { return new Promise(resolve => setTimeout(resolve, ms)); }

async function setupDB(pool) {
    await pool.query(`DROP TABLE IF EXISTS read_receipts`);
    await pool.query(`
        CREATE TABLE read_receipts (
            user_id INT,
            channel_id INT,
            ts BIGINT,
            PRIMARY KEY (user_id, channel_id)
        )
    `);
}

// ---------------------------------------------------------
// Scenario A: Direct DB (Write-Through)
// ---------------------------------------------------------
async function runDirectDB(pool) {
    console.log(`
[Scenario A] Direct DB UPSERT (${MSG_COUNT} requests)...`);
    
    const start = performance.now();
    const promises = [];

    for (let i = 0; i < MSG_COUNT; i++) {
        // Simple UPSERT
        const query = `
            INSERT INTO read_receipts (user_id, channel_id, ts) 
            VALUES ($1, $2, $3) 
            ON CONFLICT (user_id, channel_id) DO UPDATE SET ts = EXCLUDED.ts
        `;
        promises.push(pool.query(query, [i, 1, Date.now()]));
    }

    await Promise.all(promises);
    const end = performance.now();
    return end - start;
}

// ---------------------------------------------------------
// Scenario B: Kafka + Batch Consumer (Write-Behind)
// ---------------------------------------------------------
async function runKafkaBatch(pool) {
    console.log(`
[Scenario B] Kafka Produce + Batch Consumer...`);

    // 1. Measure Producer Latency (API Response Time)
    const startProduce = performance.now();
    
    // Batch produce for realistic throughput testing
    // Sending 5000 individual requests sequentially is network-bound, not broker-bound
    const BATCH_SIZE_PRODUCE = 500;
    const producePromises = [];

    for (let i = 0; i < MSG_COUNT; i += BATCH_SIZE_PRODUCE) {
        const messages = [];
        for (let j = 0; j < BATCH_SIZE_PRODUCE && (i + j) < MSG_COUNT; j++) {
             messages.push({ value: JSON.stringify({ u: i+j, c: 1, t: Date.now() }) });
        }
        producePromises.push(producer.send({
            topic: 'read-receipts',
            messages: messages,
            acks: 1 // Only wait for leader to acknowledge, best for high-volume status updates
        }));
    }
    await Promise.all(producePromises);
    const endProduce = performance.now();
    const producerTime = endProduce - startProduce;
    console.log(`   -> Producer Time (Client wait): ${producerTime.toFixed(0)}ms`);

    // 2. Measure Consumer Lag (Time until DB is fully consistent)
    // We start the timer from when production started, because that's when the "clock" began for consistency lag.
    
    return new Promise(async (resolve) => {
        let processedCount = 0;
        let batchBuffer = [];

        await consumer.subscribe({ topic: 'read-receipts', fromBeginning: true });

        const startConsume = performance.now();

        await consumer.run({
            eachBatchAutoResolve: true,
            eachBatch: async ({ batch, resolveOffset, heartbeat }) => {
                for (let message of batch.messages) {
                    batchBuffer.push(JSON.parse(message.value.toString()));
                    processedCount++;
                }

                // Simulate Batch Insert
                if (batchBuffer.length > 0) {
                    // Construct Bulk Insert Query
                    // INSERT INTO ... VALUES (..), (..), (..) ...
                    const values = batchBuffer.map((r, idx) => `($1, $2, $3)`).join(","); // Simplified for demo, assume prepared statements logic
                    // Actually, pg library requires flat array for params
                    // Let's do a simple loop for batch simulation to be fair with SQL overhead, or proper unnest
                    // To be fair and simple: Use UNNEST or Multi-row insert
                    
                    const flatParams = [];
                    const valueClauses = batchBuffer.map((r, idx) => {
                        const offset = idx * 3;
                        flatParams.push(r.u, r.c, r.t);
                        return `($${offset+1}, $${offset+2}, $${offset+3})`;
                    }).join(",");

                    const query = `
                        INSERT INTO read_receipts (user_id, channel_id, ts) 
                        VALUES ${valueClauses}
                        ON CONFLICT (user_id, channel_id) DO UPDATE SET ts = EXCLUDED.ts
                    `;
                    
                    await pool.query(query, flatParams);
                    batchBuffer = []; // Clear buffer
                }

                if (processedCount >= MSG_COUNT) {
                    const endConsume = performance.now();
                    const totalLag = endConsume - startProduce; // From start of produce to end of consume
                    // consumer.disconnect(); // Don't disconnect inside callback to avoid errors
                    resolve({ producerTime, totalLag });
                }
            }
        });
    });
}

async function main() {
    const pool = new Pool(dbConfig);
    
    try {
        // Wait for infra to wake up
        await sleep(3000); 
        await setupDB(pool);
        await producer.connect();
        await consumer.connect();

        // Run A
        const directTime = await runDirectDB(pool);
        console.log(`   -> Total Time: ${directTime.toFixed(0)}ms`);
        console.log(`   -> Throughput: ${(MSG_COUNT / (directTime/1000)).toFixed(0)} ops/s`);

        // Cleanup for B
        await setupDB(pool); // Reset table

        // Run B
        const { producerTime, totalLag } = await runKafkaBatch(pool);
        console.log(`   -> Total Lag (Consistency): ${totalLag.toFixed(0)}ms`);
        console.log(`   -> Producer Throughput: ${(MSG_COUNT / (producerTime/1000)).toFixed(0)} ops/s`);

        console.log('\n--- Final Verdict ---');
        console.log(`1. User Perception (API Latency): Kafka is ${(directTime / producerTime).toFixed(1)}x faster.`);
        console.log(`2. System Capacity (Throughput):  Kafka handles ${(MSG_COUNT / (producerTime/1000) / (MSG_COUNT / (directTime/1000))).toFixed(1)}x more traffic.`);
        console.log(`3. Consistency Cost (Lag):        DB is instant. Kafka lags by ${(totalLag - producerTime).toFixed(0)}ms.`);

    } catch (e) {
        console.error(e);
    } finally {
        await producer.disconnect();
        await consumer.disconnect();
        await pool.end();
        process.exit(0);
    }
}

main();
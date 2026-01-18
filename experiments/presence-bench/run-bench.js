const Redis = require('ioredis');
const { performance } = require('perf_hooks');

const redis = new Redis({ host: 'localhost', port: 6380 }); // Using existing project Redis port
const COUNT = 10000;
const BATCH_SIZE = 100;

async function runIndividualSetex() {
    const start = performance.now();
    for (let i = 0; i < COUNT; i++) {
        await redis.setex(`u:${i}`, 60, '1');
    }
    const end = performance.now();
    return end - start;
}

async function runPipelineSetex() {
    const start = performance.now();
    const pipeline = redis.pipeline();
    for (let i = 0; i < COUNT; i++) {
        pipeline.setex(`u:${i}`, 60, '1');
        if (i % BATCH_SIZE === 0) {
            await pipeline.exec(); // Execute every 100 items to keep memory sane
            // Note: In ioredis, exec clears the pipeline queue
        }
    }
    // Flush remaining
    if (pipeline.length > 0) await pipeline.exec();
    
    const end = performance.now();
    return end - start;
}

async function runBatchZadd() {
    const start = performance.now();
    for (let i = 0; i < COUNT; i += BATCH_SIZE) {
        const args = ['presence_set'];
        const now = Date.now();
        for (let j = 0; j < BATCH_SIZE; j++) {
            args.push(now, `u:${i+j}`); // Score, Member
        }
        await redis.zadd(...args);
    }
    const end = performance.now();
    return end - start;
}

async function main() {
    // Warmup
    await redis.set('warmup', '1');
    await redis.del('presence_set');

    console.log(`--- Presence Benchmark (N=${COUNT}) ---`);

    // 1. Individual SETEX
    console.log('Running Individual SETEX...');
    const t1 = await runIndividualSetex();
    console.log(`1. Individual SETEX: ${(COUNT / (t1/1000)).toFixed(0)} ops/sec`);

    await redis.flushdb();

    // 2. Pipeline SETEX
    console.log('Running Pipeline SETEX...');
    const t2 = await runPipelineSetex();
    console.log(`2. Pipeline SETEX:   ${(COUNT / (t2/1000)).toFixed(0)} ops/sec`);

    await redis.flushdb();

    // 3. Batch ZADD
    console.log('Running Batch ZADD...');
    const t3 = await runBatchZadd();
    console.log(`3. Batch ZADD:       ${(COUNT / (t3/1000)).toFixed(0)} ops/sec`);

    console.log('------------------------------------------------');
    console.log(`Improvement (Batch ZADD vs Individual): ${(t1/t3).toFixed(1)}x faster`);
    
    redis.disconnect();
}

main();

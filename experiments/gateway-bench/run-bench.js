const axios = require('axios');
const grpc = require('@grpc/grpc-js');
const protoLoader = require('@grpc/proto-loader');
const path = require('path');
const { performance } = require('perf_hooks');

// Import servers to start them automatically
const startRestServer = require('./server-rest');
const startGrpcServer = require('./server-grpc');

// Configuration
const MSG_COUNT = 10000;
const PARALLELISM = 10; // Connection pool simulation

const PAYLOAD = {
  id: "msg_1234567890",
  channel_id: "ch_general_001",
  user_id: "user_999888",
  content: "This is a benchmark payload meant to simulate a typical chat message content with some metadata attached to it. ".repeat(10), // ~800-900 bytes
  timestamp: Date.now()
};

// gRPC Client Setup
const PROTO_PATH = path.join(__dirname, 'protos/gateway.proto');
const packageDefinition = protoLoader.loadSync(PROTO_PATH, {
  keepCase: true, longs: String, enums: String, defaults: true, oneofs: true
});
const gatewayProto = grpc.loadPackageDefinition(packageDefinition).gateway;

// ---------------------------------------------------------
// Utilities
// ---------------------------------------------------------

function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

// ---------------------------------------------------------
// REST Benchmark
// ---------------------------------------------------------
async function runRestBench() {
  const url = 'http://localhost:3000/push';
  // Use httpAgent to keep connections alive, similar to gRPC channel
  const agent = new (require('http').Agent)({ keepAlive: true });
  const client = axios.create({ httpAgent: agent });

  const start = performance.now();
  
  const promises = [];
  for (let i = 0; i < MSG_COUNT; i++) {
    promises.push(client.post(url, PAYLOAD));
    if (promises.length >= PARALLELISM) {
        await Promise.all(promises);
        promises.length = 0;
    }
  }
  if (promises.length > 0) await Promise.all(promises);

  const end = performance.now();
  return end - start;
}

// ---------------------------------------------------------
// gRPC Benchmark (Unary)
// ---------------------------------------------------------
async function runGrpcBench() {
  const client = new gatewayProto.GatewayService('localhost:50051', grpc.credentials.createInsecure());
  
  // Promisify the push function
  const pushAsync = (data) => {
    return new Promise((resolve, reject) => {
      client.PushMessage(data, (err, response) => {
        if (err) reject(err);
        else resolve(response);
      });
    });
  };

  const start = performance.now();

  const promises = [];
  for (let i = 0; i < MSG_COUNT; i++) {
    promises.push(pushAsync(PAYLOAD));
    if (promises.length >= PARALLELISM) {
        await Promise.all(promises);
        promises.length = 0;
    }
  }
  if (promises.length > 0) await Promise.all(promises);

  const end = performance.now();
  
  client.close();
  return end - start;
}

// ---------------------------------------------------------
// gRPC Benchmark (Streaming)
// ---------------------------------------------------------
async function runGrpcStreamBench() {
  const client = new gatewayProto.GatewayService('localhost:50051', grpc.credentials.createInsecure());
  
  const start = performance.now();

  return new Promise((resolve, reject) => {
      const stream = client.PushStream((err, response) => {
          if (err) reject(err);
          else {
              const end = performance.now();
              client.close();
              resolve(end - start);
          }
      });

      for (let i = 0; i < MSG_COUNT; i++) {
          stream.write(PAYLOAD);
      }
      stream.end();
  });
}

// ---------------------------------------------------------
// Main Runner
// ---------------------------------------------------------
async function main() {
  console.log(`Starting Benchmark: ${MSG_COUNT} messages, Parallelism ${PARALLELISM}, Payload size: ~${JSON.stringify(PAYLOAD).length} bytes`);

  // Start Servers
  const restServer = startRestServer(3000);
  const grpcServer = startGrpcServer();
  
  // Warmup
  await sleep(2000);

  // Run REST
  console.log('Running REST Benchmark...');
  try {
      const restTime = await runRestBench();
      console.log(`REST Total Time: ${restTime.toFixed(2)}ms`);
      console.log(`REST Throughput: ${(MSG_COUNT / (restTime / 1000)).toFixed(2)} req/sec`);
      
      // Cooldown
      await sleep(1000);

      // Run gRPC Unary
      console.log('Running gRPC Unary Benchmark...');
      const grpcTime = await runGrpcBench();
      console.log(`gRPC Unary Total Time: ${grpcTime.toFixed(2)}ms`);
      console.log(`gRPC Unary Throughput: ${(MSG_COUNT / (grpcTime / 1000)).toFixed(2)} req/sec`);

      // Cooldown
      await sleep(1000);

      // Run gRPC Streaming
      console.log('Running gRPC Streaming Benchmark...');
      const grpcStreamTime = await runGrpcStreamBench();
      console.log(`gRPC Stream Total Time: ${grpcStreamTime.toFixed(2)}ms`);
      console.log(`gRPC Stream Throughput: ${(MSG_COUNT / (grpcStreamTime / 1000)).toFixed(2)} req/sec`);

      // Summary
      console.log('------------------------------------------------');
      console.log(`REST:          ${(MSG_COUNT / (restTime / 1000)).toFixed(0)} req/sec`);
      console.log(`gRPC (Unary):  ${(MSG_COUNT / (grpcTime / 1000)).toFixed(0)} req/sec (${(restTime / grpcTime).toFixed(2)}x speedup)`);
      console.log(`gRPC (Stream): ${(MSG_COUNT / (grpcStreamTime / 1000)).toFixed(0)} req/sec (${(restTime / grpcStreamTime).toFixed(2)}x speedup)`);
      console.log('------------------------------------------------');

  } catch (e) {
      console.error("Benchmark failed", e);
  } finally {
      restServer.close();
      grpcServer.forceShutdown();
      process.exit(0);
  }
}

main();

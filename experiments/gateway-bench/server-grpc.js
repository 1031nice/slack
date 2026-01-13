const grpc = require('@grpc/grpc-js');
const protoLoader = require('@grpc/proto-loader');
const path = require('path');

const PROTO_PATH = path.join(__dirname, 'protos/gateway.proto');
const packageDefinition = protoLoader.loadSync(PROTO_PATH, {
  keepCase: true,
  longs: String,
  enums: String,
  defaults: true,
  oneofs: true
});
const gatewayProto = grpc.loadPackageDefinition(packageDefinition).gateway;

function pushMessage(call, callback) {
  // Simulate minimal processing
  const msg = call.request;
  callback(null, { success: true });
}

function pushStream(call, callback) {
  call.on('data', (msg) => {
    // Simulate minimal processing per message
  });
  call.on('end', () => {
    callback(null, { success: true });
  });
}

function startServer() {
  const server = new grpc.Server();
  server.addService(gatewayProto.GatewayService.service, { 
    pushMessage: pushMessage,
    pushStream: pushStream
  });
  
  server.bindAsync('0.0.0.0:50051', grpc.ServerCredentials.createInsecure(), () => {
    // console.log('gRPC Server running at 0.0.0.0:50051');
    server.start();
  });
  return server;
}

if (require.main === module) {
    startServer();
}

module.exports = startServer;

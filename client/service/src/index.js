import { loadConfig } from './config.js';
import { ClientService } from './service.js';

const config = loadConfig();
const service = new ClientService(config);

await service.start();

const shutdown = () => {
  service.stop();
  process.exit(0);
};

process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);

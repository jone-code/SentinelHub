import { contextBridge } from 'electron';

contextBridge.exposeInMainWorld('sentinelhub', {
  platform: process.platform,
  version: '0.1.0-dev',
});

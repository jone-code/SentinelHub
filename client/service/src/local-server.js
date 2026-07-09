import http from 'node:http';

/**
 * Local IPC HTTP server for Flutter desktop UI.
 * Binds 127.0.0.1 only — not exposed to LAN.
 */
export class LocalServer {
  /**
   * @param {object} opts
   * @param {string} opts.host
   * @param {number} opts.port
   * @param {() => object} opts.getStatus
   * @param {() => object | null} opts.getAssets
   * @param {() => object | null} opts.getPolicy
   */
  constructor({ host, port, getStatus, getAssets, getPolicy }) {
    this.host = host;
    this.port = port;
    this.getStatus = getStatus;
    this.getAssets = getAssets;
    this.getPolicy = getPolicy;
    /** @type {http.Server | null} */
    this.server = null;
  }

  start() {
    this.server = http.createServer((req, res) => {
      if (req.method !== 'GET') {
        res.writeHead(405, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: 'method not allowed' }));
        return;
      }

      if (req.url === '/local/status') {
        this.json(res, this.getStatus());
        return;
      }

      if (req.url === '/local/assets') {
        const assets = this.getAssets();
        if (!assets) {
          res.writeHead(404, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ error: 'assets not collected yet' }));
          return;
        }
        this.json(res, assets);
        return;
      }

      if (req.url === '/local/policy') {
        const policy = this.getPolicy?.();
        if (!policy) {
          res.writeHead(404, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ error: 'no policy cached' }));
          return;
        }
        this.json(res, policy);
        return;
      }

      res.writeHead(404, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: 'not found' }));
    });

    return new Promise((resolve, reject) => {
      this.server.listen(this.port, this.host, () => {
        console.log(`[sentinel-service] local IPC http://${this.host}:${this.port}`);
        resolve();
      });
      this.server.on('error', reject);
    });
  }

  /** @param {http.ServerResponse} res @param {object} body */
  json(res, body) {
    res.writeHead(200, {
      'Content-Type': 'application/json',
      'Access-Control-Allow-Origin': '*',
    });
    res.end(JSON.stringify(body));
  }

  stop() {
    return new Promise((resolve) => {
      if (!this.server) {
        resolve();
        return;
      }
      this.server.close(() => {
        this.server = null;
        resolve();
      });
    });
  }
}

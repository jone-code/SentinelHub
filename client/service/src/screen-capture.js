/**
 * Cross-platform desktop screen capture → raw RGBA frames.
 * Linux backends: x11grab | ffmpeg-pipewire | grim (Wayland) — auto-detected.
 * macOS: avfoundation | Windows: gdigrab
 */

import { spawn, spawnSync } from 'node:child_process';
import { platform } from 'node:os';

/** @typedef {'auto' | 'x11' | 'pipewire' | 'grim' | 'wf-recorder'} LinuxBackend */

/**
 * @typedef {object} ScreenCaptureOptions
 * @property {number} [width]
 * @property {number} [height]
 * @property {number} [fps]
 * @property {LinuxBackend | string} [backend]
 * @property {(frame: { width: number; height: number; data: Uint8ClampedArray }) => void} onFrame
 * @property {(err: Error) => void} [onError]
 * @property {(code: number | null) => void} [onClose]
 */

function commandExists(cmd) {
  const r = spawnSync('which', [cmd], { stdio: 'ignore' });
  return r.status === 0;
}

function ffmpegFormats() {
  const r = spawnSync('ffmpeg', ['-hide_banner', '-formats'], { encoding: 'utf8' });
  return `${r.stdout ?? ''}${r.stderr ?? ''}`;
}

function ffmpegSupportsPipewire() {
  return ffmpegFormats().includes('pipewire');
}

function isWaylandSession() {
  return process.env.XDG_SESSION_TYPE === 'wayland' || !!process.env.WAYLAND_DISPLAY;
}

/**
 * Pick Linux capture backend.
 * @param {LinuxBackend | string} [forced]
 * @returns {Exclude<LinuxBackend, 'auto'>}
 */
export function resolveLinuxBackend(forced = 'auto') {
  if (forced && forced !== 'auto') {
    return /** @type {Exclude<LinuxBackend, 'auto'>} */ (forced);
  }
  if (isWaylandSession()) {
    if (ffmpegSupportsPipewire()) return 'pipewire';
    if (commandExists('wf-recorder')) return 'wf-recorder';
    if (commandExists('grim')) return 'grim';
  }
  return 'x11';
}

/**
 * Ordered backends to try when probing.
 * @param {LinuxBackend | string} [forced]
 */
export function linuxBackendProbeOrder(forced = 'auto') {
  if (forced && forced !== 'auto') {
    return [/** @type {Exclude<LinuxBackend, 'auto'>} */ (forced)];
  }
  if (isWaylandSession()) {
    const order = [];
    if (ffmpegSupportsPipewire()) order.push('pipewire');
    if (commandExists('wf-recorder')) order.push('wf-recorder');
    if (commandExists('grim')) order.push('grim');
    order.push('x11');
    return order;
  }
  return ['x11', 'pipewire', 'grim'];
}

/**
 * Build ffmpeg arguments for the selected backend.
 * @param {{ width: number; height: number; fps: number; backend?: LinuxBackend | string }} opts
 */
export function buildFfmpegCaptureArgs({ width, height, fps, backend }) {
  const scale = `scale=${width}:${height}`;
  const os = platform();

  if (os === 'darwin') {
    const device = process.env.REMOTE_CAPTURE_MAC_DEVICE || '1:none';
    return {
      backend: 'avfoundation',
      args: [
        '-hide_banner', '-loglevel', 'error',
        '-f', 'avfoundation', '-framerate', String(fps),
        '-i', device,
        '-vf', scale, '-pix_fmt', 'rgba', '-f', 'rawvideo', '-',
      ],
    };
  }

  if (os === 'win32') {
    const input = process.env.REMOTE_CAPTURE_WIN_INPUT || 'desktop';
    return {
      backend: 'gdigrab',
      args: [
        '-hide_banner', '-loglevel', 'error',
        '-f', 'gdigrab', '-framerate', String(fps),
        '-i', input,
        '-vf', scale, '-pix_fmt', 'rgba', '-f', 'rawvideo', '-',
      ],
    };
  }

  const linuxBackend = resolveLinuxBackend(backend);

  if (linuxBackend === 'pipewire') {
    const target = process.env.REMOTE_CAPTURE_PIPEWIRE_TARGET || 'screen-capture';
    return {
      backend: 'pipewire',
      args: [
        '-hide_banner', '-loglevel', 'error',
        '-f', 'pipewire', '-framerate', String(fps),
        '-i', target,
        '-vf', scale, '-pix_fmt', 'rgba', '-f', 'rawvideo', '-',
      ],
    };
  }

  if (linuxBackend === 'wf-recorder') {
    const output = process.env.REMOTE_CAPTURE_WAYLAND_OUTPUT || '';
    const input = output ? output : 'eDP-1';
    return {
      backend: 'wf-recorder',
      args: [
        '-hide_banner', '-loglevel', 'error',
        '-f', 'rawvideo', '-pixel_format', 'bgra',
        '-video_size', `${width}x${height}`, '-framerate', String(fps),
        '-i', 'pipe:0',
        '-vf', scale, '-pix_fmt', 'rgba', '-f', 'rawvideo', '-',
      ],
      prelude: ['wf-recorder', '-f', '-', '-o', input, '-c', 'rawvideo', '-p', 'bgra', '-m', 'slurp'],
    };
  }

  const display = process.env.DISPLAY || ':0.0';
  const videoSize = process.env.REMOTE_CAPTURE_VIDEO_SIZE;
  const args = [
    '-hide_banner', '-loglevel', 'error',
    '-f', 'x11grab', '-framerate', String(fps),
  ];
  if (videoSize) args.push('-video_size', videoSize);
  args.push('-i', display, '-vf', scale, '-pix_fmt', 'rgba', '-f', 'rawvideo', '-');
  return { backend: 'x11', args };
}

/**
 * Decode a PNG buffer to RGBA via ffmpeg (used by grim backend).
 */
function decodePngToRgba(png, width, height) {
  return new Promise((resolve, reject) => {
    const ff = spawn('ffmpeg', [
      '-hide_banner', '-loglevel', 'error',
      '-f', 'image2pipe', '-i', 'pipe:0',
      '-vf', `scale=${width}:${height}`,
      '-pix_fmt', 'rgba', '-frames:v', '1', '-f', 'rawvideo', '-',
    ], { stdio: ['pipe', 'pipe', 'pipe'] });

    const chunks = [];
    ff.stdout.on('data', (c) => chunks.push(c));
    ff.on('close', (code) => {
      if (code !== 0) {
        reject(new Error('png decode failed'));
        return;
      }
      const buf = Buffer.concat(chunks);
      resolve(new Uint8ClampedArray(buf.buffer, buf.byteOffset, buf.byteLength));
    });
    ff.on('error', reject);
    ff.stdin.write(png);
    ff.stdin.end();
  });
}

/**
 * Wayland grim loop — periodic full-screen PNG capture.
 */
function createGrimCapture(opts) {
  const width = opts.width ?? 1280;
  const height = opts.height ?? 720;
  const fps = opts.fps ?? 10;
  const intervalMs = Math.max(50, Math.floor(1000 / fps));
  let stopped = false;
  let timer = null;
  let capturing = false;

  const ready = new Promise((resolve) => {
    setTimeout(resolve, 300);
  });

  const tick = async () => {
    if (stopped || capturing) return;
    capturing = true;
    try {
      const png = await captureGrimPng();
      const data = await decodePngToRgba(png, width, height);
      opts.onFrame({ width, height, data });
    } catch (err) {
      opts.onError?.(err instanceof Error ? err : new Error(String(err)));
    } finally {
      capturing = false;
    }
  };

  timer = setInterval(() => {
    tick().catch(() => {});
  }, intervalMs);
  tick().catch(() => {});

  return {
    ready,
    stop() {
      stopped = true;
      if (timer) clearInterval(timer);
    },
  };
}

function captureGrimPng() {
  return new Promise((resolve, reject) => {
    const region = process.env.REMOTE_CAPTURE_GRIM_REGION;
    const args = region ? ['-g', region, '-'] : ['-'];
    const proc = spawn('grim', args, { stdio: ['ignore', 'pipe', 'pipe'] });
    const chunks = [];
    let stderr = '';
    proc.stdout.on('data', (c) => chunks.push(c));
    proc.stderr.on('data', (c) => { stderr += c.toString(); });
    proc.on('close', (code) => {
      if (code !== 0) {
        reject(new Error(stderr.trim() || `grim exited ${code}`));
        return;
      }
      resolve(Buffer.concat(chunks));
    });
    proc.on('error', reject);
  });
}

/**
 * Spawn ffmpeg (and optional prelude process piped into ffmpeg stdin).
 */
function spawnFfmpegPipeline(spec, opts, frameBytes) {
  let buffer = Buffer.alloc(0);
  let stopped = false;
  let stderr = '';
  /** @type {import('node:child_process').ChildProcess | null} */
  let preludeProc = null;
  /** @type {import('node:child_process').ChildProcess} */
  const proc = spawn('ffmpeg', spec.args, { stdio: ['pipe', 'pipe', 'pipe'] });

  if (spec.prelude) {
    preludeProc = spawn(spec.prelude[0], spec.prelude.slice(1), { stdio: ['ignore', 'pipe', 'pipe'] });
    preludeProc.stdout?.pipe(proc.stdin);
    preludeProc.stderr?.on('data', (c) => { stderr += c.toString(); });
    preludeProc.on('error', (err) => opts.onError?.(err));
  } else {
    proc.stdin?.end();
  }

  const ready = new Promise((resolve, reject) => {
    let settled = false;
    const finishReady = () => {
      if (settled) return;
      settled = true;
      clearTimeout(timeout);
      resolve();
    };
    const timeout = setTimeout(finishReady, 2000);

    proc.stderr?.on('data', (c) => { stderr += c.toString(); });
    proc.on('error', (err) => {
      if (!settled) {
        settled = true;
        clearTimeout(timeout);
        opts.onError?.(err);
        reject(err);
      }
    });
    proc.stdout?.once('data', finishReady);
    proc.on('close', (code) => {
      clearTimeout(timeout);
      if (!stopped && code !== 0 && code !== null) {
        opts.onError?.(new Error(stderr.trim() || `ffmpeg exited ${code}`));
      }
      opts.onClose?.(code);
    });
  });

  proc.stdout?.on('data', (chunk) => {
    buffer = Buffer.concat([buffer, chunk]);
    while (buffer.length >= frameBytes) {
      const slice = buffer.subarray(0, frameBytes);
      buffer = buffer.subarray(frameBytes);
      opts.onFrame({
        width: opts.width ?? 1280,
        height: opts.height ?? 720,
        data: new Uint8ClampedArray(slice.buffer, slice.byteOffset, slice.byteLength),
      });
    }
  });

  return {
    ready,
    stop() {
      stopped = true;
      if (preludeProc && !preludeProc.killed) preludeProc.kill('SIGTERM');
      if (!proc.killed) proc.kill('SIGTERM');
    },
  };
}

/**
 * Start streaming screen capture.
 * @param {ScreenCaptureOptions} opts
 */
export function createScreenCapture(opts) {
  const width = opts.width ?? 1280;
  const height = opts.height ?? 720;
  const fps = opts.fps ?? 10;
  const backend = opts.backend ?? process.env.REMOTE_CAPTURE_BACKEND ?? 'auto';

  if (platform() === 'linux' && resolveLinuxBackend(backend) === 'grim') {
    return createGrimCapture({ ...opts, width, height, fps });
  }

  const spec = buildFfmpegCaptureArgs({ width, height, fps, backend });
  return spawnFfmpegPipeline(spec, { ...opts, width, height }, width * height * 4);
}

/**
 * Probe a single backend for one frame.
 */
async function probeBackend(backend, width, height) {
  const os = platform();

  if (os === 'linux' && backend === 'grim') {
    if (!commandExists('grim')) return false;
    try {
      const png = await captureGrimPng();
      const data = await decodePngToRgba(png, width, height);
      return data.length >= width * height * 4;
    } catch {
      return false;
    }
  }

  const spec = buildFfmpegCaptureArgs({
    width,
    height,
    fps: 2,
    backend: os === 'linux' ? backend : 'auto',
  });
  const args = [...spec.args];
  const outIdx = args.lastIndexOf('-');
  if (outIdx !== -1) args.splice(outIdx, 0, '-frames:v', '1');

  return new Promise((resolve) => {
    let bytes = 0;
    let preludeProc = null;
    const proc = spawn('ffmpeg', args, { stdio: ['pipe', 'pipe', 'pipe'] });
    if (spec.prelude) {
      preludeProc = spawn(spec.prelude[0], spec.prelude.slice(1), { stdio: ['ignore', 'pipe', 'pipe'] });
      preludeProc.stdout?.pipe(proc.stdin);
    } else {
      proc.stdin?.end();
    }
    proc.stdout?.on('data', (c) => { bytes += c.length; });
    proc.on('close', () => resolve(bytes >= width * height * 4));
    proc.on('error', () => resolve(false));
    setTimeout(() => {
      proc.kill('SIGTERM');
      preludeProc?.kill('SIGTERM');
      resolve(bytes >= width * height * 4);
    }, 8000);
  });
}

/**
 * Probe capture — tries backends in order, returns first working backend name or null.
 * @returns {Promise<string | null>}
 */
export async function probeScreenCapture(width = 320, height = 240, backend = 'auto') {
  const os = platform();
  if (os === 'darwin' || os === 'win32') {
    const ok = await probeBackend('auto', width, height);
    return ok ? (os === 'darwin' ? 'avfoundation' : 'gdigrab') : null;
  }

  for (const b of linuxBackendProbeOrder(backend)) {
    if (await probeBackend(b, width, height)) {
      return b;
    }
  }
  return null;
}

/**
 * @returns {Promise<{ backend: string; width: number; height: number } | null>}
 */
export async function detectScreenCapture(width = 320, height = 240, backend = 'auto') {
  const resolved = await probeScreenCapture(width, height, backend);
  if (!resolved) return null;
  return { backend: resolved, width, height };
}

/**
 * Cross-platform desktop screen capture via ffmpeg → raw RGBA frames.
 * Linux: x11grab | macOS: avfoundation | Windows: gdigrab
 */

import { spawn } from 'node:child_process';
import { platform } from 'node:os';

/**
 * @typedef {object} ScreenCaptureOptions
 * @property {number} [width]
 * @property {number} [height]
 * @property {number} [fps]
 * @property {(frame: { width: number; height: number; data: Uint8ClampedArray }) => void} onFrame
 * @property {(err: Error) => void} [onError]
 * @property {(code: number | null) => void} [onClose]
 */

/**
 * Build ffmpeg arguments for the current platform.
 * @param {{ width: number; height: number; fps: number }} opts
 */
export function buildFfmpegCaptureArgs({ width, height, fps }) {
  const scale = `scale=${width}:${height}`;
  const os = platform();

  if (os === 'darwin') {
    const device = process.env.REMOTE_CAPTURE_MAC_DEVICE || '1:none';
    return [
      '-hide_banner',
      '-loglevel',
      'error',
      '-f',
      'avfoundation',
      '-framerate',
      String(fps),
      '-i',
      device,
      '-vf',
      scale,
      '-pix_fmt',
      'rgba',
      '-f',
      'rawvideo',
      '-',
    ];
  }

  if (os === 'win32') {
    const input = process.env.REMOTE_CAPTURE_WIN_INPUT || 'desktop';
    return [
      '-hide_banner',
      '-loglevel',
      'error',
      '-f',
      'gdigrab',
      '-framerate',
      String(fps),
      '-i',
      input,
      '-vf',
      scale,
      '-pix_fmt',
      'rgba',
      '-f',
      'rawvideo',
      '-',
    ];
  }

  const display = process.env.DISPLAY || ':0.0';
  const videoSize = process.env.REMOTE_CAPTURE_VIDEO_SIZE;
  const args = [
    '-hide_banner',
    '-loglevel',
    'error',
    '-f',
    'x11grab',
    '-framerate',
    String(fps),
  ];
  if (videoSize) {
    args.push('-video_size', videoSize);
  }
  args.push('-i', display, '-vf', scale, '-pix_fmt', 'rgba', '-f', 'rawvideo', '-');
  return args;
}

/**
 * Start streaming screen capture.
 * @param {ScreenCaptureOptions} opts
 * @returns {{ stop: () => void; ready: Promise<void> }}
 */
export function createScreenCapture(opts) {
  const width = opts.width ?? 1280;
  const height = opts.height ?? 720;
  const fps = opts.fps ?? 10;
  const frameBytes = width * height * 4;

  const args = buildFfmpegCaptureArgs({ width, height, fps });
  const proc = spawn('ffmpeg', args, { stdio: ['ignore', 'pipe', 'pipe'] });

  let buffer = Buffer.alloc(0);
  let stopped = false;
  let stderr = '';

  const ready = new Promise((resolve, reject) => {
    let settled = false;
    const finishReady = () => {
      if (settled) return;
      settled = true;
      clearTimeout(timeout);
      resolve();
    };
    const timeout = setTimeout(finishReady, 2000);

    proc.stderr?.on('data', (chunk) => {
      stderr += chunk.toString();
    });

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
        const msg = stderr.trim() || `ffmpeg exited with code ${code}`;
        opts.onError?.(new Error(msg));
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
        width,
        height,
        data: new Uint8ClampedArray(slice.buffer, slice.byteOffset, slice.byteLength),
      });
    }
  });

  return {
    ready,
    stop() {
      stopped = true;
      if (!proc.killed) {
        proc.kill('SIGTERM');
      }
    },
  };
}

/**
 * Quick probe — returns true if ffmpeg can capture one frame.
 */
export async function probeScreenCapture(width = 320, height = 240) {
  const args = buildFfmpegCaptureArgs({ width, height, fps: 2 });
  const outIdx = args.lastIndexOf('-');
  if (outIdx !== -1) {
    args.splice(outIdx, 0, '-frames:v', '1');
  }

  return new Promise((resolve) => {
    const proc = spawn('ffmpeg', args, { stdio: ['ignore', 'pipe', 'pipe'] });
    let bytes = 0;
    proc.stdout?.on('data', (chunk) => {
      bytes += chunk.length;
    });
    proc.on('close', () => {
      resolve(bytes >= width * height * 4);
    });
    proc.on('error', () => resolve(false));
    setTimeout(() => {
      proc.kill('SIGTERM');
      resolve(bytes >= width * height * 4);
    }, 8000);
  });
}

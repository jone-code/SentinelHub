import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

describe('config', () => {
  it('loadConfig reads remote capture env vars', async () => {
    process.env.REMOTE_CAPTURE_WIDTH = '1920';
    process.env.REMOTE_CAPTURE_HEIGHT = '1080';
    process.env.REMOTE_CAPTURE_FPS = '15';
    process.env.REMOTE_CAPTURE_BACKEND = 'pipewire';
    process.env.REMOTE_CAPTURE_SYNTHETIC = 'true';

    const { loadConfig } = await import('../src/config.js');
    const config = loadConfig();

    assert.equal(config.remoteCaptureWidth, 1920);
    assert.equal(config.remoteCaptureHeight, 1080);
    assert.equal(config.remoteCaptureFps, 15);
    assert.equal(config.remoteCaptureBackend, 'pipewire');
    assert.equal(config.remoteCaptureSynthetic, true);

    delete process.env.REMOTE_CAPTURE_WIDTH;
    delete process.env.REMOTE_CAPTURE_HEIGHT;
    delete process.env.REMOTE_CAPTURE_FPS;
    delete process.env.REMOTE_CAPTURE_BACKEND;
    delete process.env.REMOTE_CAPTURE_SYNTHETIC;
  });
});

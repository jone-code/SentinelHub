import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import {
  buildFfmpegCaptureArgs,
  linuxBackendProbeOrder,
  resolveLinuxBackend,
} from '../src/screen-capture.js';

describe('screen-capture', () => {
  it('buildFfmpegCaptureArgs for linux uses x11grab by default', () => {
    const { backend, args } = buildFfmpegCaptureArgs({ width: 640, height: 480, fps: 10, backend: 'x11' });
    assert.equal(backend, 'x11');
    assert.ok(args.includes('x11grab'));
    assert.ok(args.includes('scale=640:480'));
    assert.ok(args.includes('rgba'));
  });

  it('buildFfmpegCaptureArgs for pipewire backend', () => {
    const { backend, args } = buildFfmpegCaptureArgs({ width: 1280, height: 720, fps: 15, backend: 'pipewire' });
    assert.equal(backend, 'pipewire');
    assert.ok(args.includes('pipewire'));
  });

  it('resolveLinuxBackend honors forced backend', () => {
    assert.equal(resolveLinuxBackend('grim'), 'grim');
    assert.equal(resolveLinuxBackend('x11'), 'x11');
  });

  it('linuxBackendProbeOrder returns single entry when forced', () => {
    assert.deepEqual(linuxBackendProbeOrder('pipewire'), ['pipewire']);
  });

  it('linuxBackendProbeOrder includes x11 fallback in auto mode on linux', () => {
    const order = linuxBackendProbeOrder('auto');
    assert.ok(order.includes('x11'));
    assert.ok(order.length >= 1);
  });
});

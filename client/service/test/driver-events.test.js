import test from 'node:test';
import assert from 'node:assert/strict';
import { driverEventDedupKey, normalizeDriverEvents } from '../src/driver-events.js';

test('normalizeDriverEvents maps file and process daemon events', () => {
  const events = normalizeDriverEvents({
    file_events: [{ path: '/home/u/x.pem', pid: 10, blocked: true, action: 'block' }],
    process_events: [{ process: 'nc', pid: 20, blocked: false, action: 'alert' }],
    kernel_events: [],
  });

  assert.equal(events.length, 2);
  assert.equal(events[0].event_type, 'driver.file_blocked');
  assert.equal(events[0].severity, 'critical');
  assert.equal(events[1].event_type, 'driver.process_exec');
  assert.equal(events[1].detail.process, 'nc');
});

test('normalizeDriverEvents maps kernel ring events', () => {
  const events = normalizeDriverEvents({
    file_events: [],
    process_events: [],
    kernel_events: [{ type: 4, pid: 99, blocked: 1, path: 'nc' }],
  });

  assert.equal(events.length, 1);
  assert.equal(events[0].event_type, 'driver.process_blocked');
  assert.equal(events[0].detail.source, 'kernel');
  assert.equal(events[0].detail.process, 'nc');
});

test('driverEventDedupKey is stable', () => {
  const event = {
    event_type: 'driver.file_blocked',
    detail: { pid: 1, path: '/tmp/a.pem' },
  };
  assert.equal(driverEventDedupKey(event), 'driver.file_blocked:1:/tmp/a.pem');
});

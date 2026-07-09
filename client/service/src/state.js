import { readFile, writeFile, mkdir } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const STATE_FILE = path.resolve(__dirname, '../.state/client.json');

export async function loadClientState() {
  try {
    const raw = await readFile(STATE_FILE, 'utf8');
    return JSON.parse(raw);
  } catch {
    return {};
  }
}

/** @param {{ client_id?: string }} state */
export async function saveClientState(state) {
  await mkdir(path.dirname(STATE_FILE), { recursive: true });
  await writeFile(STATE_FILE, JSON.stringify(state, null, 2));
}

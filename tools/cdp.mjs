// Evaluates a JS expression in the TV's WebView via the DevTools protocol (debug build only).
// Setup:  adb forward tcp:9222 localabstract:webview_devtools_remote_<pid of io.github.tsnaketech.orangetv>
// Usage:  node tools/cdp.mjs <file.js>
import { readFileSync } from 'node:fs';
const expr = readFileSync(process.argv[2], 'utf8');
const targets = await (await fetch('http://127.0.0.1:9222/json')).json();
const page = targets.find(t => t.type === 'page');
if (!page) { console.error('aucune page', targets); process.exit(1); }
const ws = new WebSocket(page.webSocketDebuggerUrl);
ws.onopen = () => ws.send(JSON.stringify({ id: 1, method: 'Runtime.evaluate', params: { expression: expr, returnByValue: true, awaitPromise: true } }));
ws.onmessage = e => {
  const m = JSON.parse(e.data);
  if (m.id !== 1) return;
  const r = m.result?.result;
  console.log(r?.type === 'string' ? r.value : JSON.stringify(r?.value ?? m, null, 1));
  ws.close();
};

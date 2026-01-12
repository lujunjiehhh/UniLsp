#!/usr/bin/env node
'use strict';

const fs = require('fs');
const net = require('net');
const os = require('os');
const path = require('path');
const {spawn} = require('child_process');
const readline = require('readline');
const {DapConnection} = require('../lib/dap-conn');

function usage() {
  const text = `
Usage:
  dap-cli repl [options]
  dap-cli <command> [options]
  dap-cli --script <path> [options]

Options:
  --transport <tcp|uds|stdio>   Transport type (default: tcp)
  --host <host>                 TCP host (default: 127.0.0.1)
  --port <port>                 TCP port (required for tcp)
  --socket <path>               UDS socket path (required for uds)
  --adapter <cmd>               Adapter command for stdio (required for stdio)
  --adapter-arg <arg>           Adapter arg (repeatable)
  --args <json|@file>           Request arguments JSON or @file
  --script <path>               Run commands from a script file
  --discover                    Auto-discover DAP endpoint from ~/.intellij-lsp
  --project <path>              Project path for discovery (optional)
  --format <json|markdown|xml>  Output format (default: json)
  --pretty                      Pretty-print output JSON
  --quiet-events                Do not print events automatically
  --event-filter <a,b,c>        Only print matching event names
  --no-output-events            Suppress "output" events in auto print
  --show-output-events          Print "output" events (overrides --no-output-events)
  --event-log <path>            Write auto-printed events to a file
  --timeout <ms>                Request/event timeout (default: 10000)
  --wait-event <name>           Wait for an event after request
  --show-requests               Print server->client requests
  --auto-reply                  Auto-reply to server requests with failure
  --help                        Show this help

REPL input:
  <command> [<jsonArgs>]
  <command> @<argsFile.json>
  attach-current
  wait-event <name> [timeoutMs]
  format <json|markdown|xml>
  pretty <on|off>
  timeout <ms>
  help
  exit

Multi-line JSON args (REPL):
  If a JSON parse fails with "Unexpected end of JSON input", dap-cli will prompt with "...> "
  and keep reading lines until the JSON is complete.
`;
  console.log(text.trim());
}

function parseArgs(argv) {
  const opts = {
    transport: 'tcp',
    host: '127.0.0.1',
    port: null,
    socket: null,
    adapter: null,
    adapterArgs: [],
    args: null,
    script: null,
    discover: false,
    projectPath: null,
    format: 'json',
    pretty: false,
    quietEvents: false,
    eventFilter: null,
    suppressOutputEvents: true,
    eventLogPath: null,
    timeout: 10000,
    waitEvent: null,
    showRequests: false,
    autoReply: false,
    command: null,
    repl: false
  };

  const args = argv.slice();
  if (args[0] === 'repl') {
    opts.repl = true;
    args.shift();
  }

  for (let i = 0; i < args.length; i++) {
    const a = args[i];
    if (a === '--help' || a === '-h') {
      opts.help = true;
      continue;
    }
    if (a.startsWith('--')) {
      const key = a.slice(2);
      switch (key) {
        case 'transport':
          opts.transport = args[++i];
          break;
        case 'host':
          opts.host = args[++i];
          break;
        case 'port':
          opts.port = parseInt(args[++i], 10);
          break;
        case 'socket':
          opts.socket = args[++i];
          break;
        case 'adapter':
          opts.adapter = args[++i];
          break;
        case 'adapter-arg':
          opts.adapterArgs.push(args[++i]);
          break;
        case 'args':
          opts.args = args[++i];
          break;
        case 'script':
          opts.script = args[++i];
          break;
        case 'discover':
          opts.discover = true;
          break;
        case 'project':
          opts.projectPath = args[++i];
          break;
        case 'format':
          opts.format = args[++i];
          break;
        case 'pretty':
          opts.pretty = true;
          break;
        case 'quiet-events':
          opts.quietEvents = true;
          break;
        case 'event-filter':
          opts.eventFilter = args[++i];
          break;
        case 'no-output-events':
          opts.suppressOutputEvents = true;
          break;
        case 'show-output-events':
          opts.suppressOutputEvents = false;
          break;
        case 'event-log':
          opts.eventLogPath = args[++i];
          break;
        case 'timeout':
          opts.timeout = parseInt(args[++i], 10);
          break;
        case 'wait-event':
          opts.waitEvent = args[++i];
          break;
        case 'show-requests':
          opts.showRequests = true;
          break;
        case 'auto-reply':
          opts.autoReply = true;
          break;
        default:
          console.error(`Unknown option: ${a}`);
          opts.help = true;
          break;
      }
      continue;
    }

    if (!opts.command && !opts.repl) {
      opts.command = a;
    } else if (opts.repl && !opts.command) {
      opts.command = a;
    } else {
      console.error(`Unexpected argument: ${a}`);
      opts.help = true;
    }
  }

  return opts;
}

function readJsonArg(value) {
  if (!value) return undefined;
  if (value.startsWith('@')) {
    const file = value.slice(1);
    const content = fs.readFileSync(file, 'utf8');
    return JSON.parse(content);
  }
  return JSON.parse(value);
}

function formatOutput(obj, format, pretty, label) {
  if (format === 'markdown') {
    const json = JSON.stringify(obj, null, pretty ? 2 : 0);
    const header = label ? `## ${label}\n` : '';
    return `${header}\n\`\`\`json\n${json}\n\`\`\`\n`;
  }
  if (format === 'xml') {
    const header = label ? `<!-- ${label} -->\n` : '';
    return header + toXml(obj);
  }
  return JSON.stringify(obj, null, pretty ? 2 : 0);
}

function toXml(value, indent = '') {
  const next = indent + '  ';
  if (value === null || value === undefined) {
    return `${indent}<null/>\n`;
  }
  if (typeof value === 'string') {
    return `${indent}<string>${escapeXml(value)}</string>\n`;
  }
  if (typeof value === 'number' || typeof value === 'boolean') {
    return `${indent}<value>${value}</value>\n`;
  }
  if (Array.isArray(value)) {
    let out = `${indent}<array>\n`;
    for (const item of value) {
      out += `${next}<item>\n${toXml(item, next + '  ')}${next}</item>\n`;
    }
    out += `${indent}</array>\n`;
    return out;
  }
  if (typeof value === 'object') {
    let out = `${indent}<object>\n`;
    for (const [k, v] of Object.entries(value)) {
      out += `${next}<${escapeXml(k)}>\n${toXml(v, next + '  ')}${next}</${escapeXml(k)}>\n`;
    }
    out += `${indent}</object>\n`;
    return out;
  }
  return `${indent}<value>${escapeXml(String(value))}</value>\n`;
}

function escapeXml(text) {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
}

function takeBufferedEvent(buffer, name, exactEvent) {
  if (!buffer || buffer.length === 0) return null;
  if (exactEvent) {
    const idx = buffer.indexOf(exactEvent);
    if (idx >= 0) {
      return buffer.splice(idx, 1)[0];
    }
    return null;
  }
  const idx = buffer.findIndex((evt) => !name || evt.event === name);
  if (idx >= 0) {
    return buffer.splice(idx, 1)[0];
  }
  return null;
}

function waitForEvent(conn, name, timeoutMs, buffer) {
  const existing = takeBufferedEvent(buffer, name);
  if (existing) {
    return Promise.resolve(existing);
  }
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      cleanup();
      reject(new Error(`Timed out waiting for event: ${name}`));
    }, timeoutMs);

    function onEvent(evt) {
      if (!name || evt.event === name) {
        cleanup();
        takeBufferedEvent(buffer, name, evt);
        resolve(evt);
      }
    }

    function cleanup() {
      clearTimeout(timer);
      conn.off('event', onEvent);
    }

    conn.on('event', onEvent);
  });
}

function withTimeout(promise, timeoutMs, label) {
  if (!timeoutMs) return promise;
  return Promise.race([
    promise,
    new Promise((_, reject) => {
      setTimeout(() => reject(new Error(`Timeout after ${timeoutMs}ms${label ? ` (${label})` : ''}`)), timeoutMs);
    })
  ]);
}

function connect(opts) {
  if (opts.discover) {
    applyDiscovery(opts);
  }
  if (opts.transport === 'tcp') {
    if (!opts.port) {
      throw new Error('TCP transport requires --port');
    }
    const socket = net.connect({host: opts.host, port: opts.port});
    return {input: socket, output: socket, close: () => socket.end()};
  }
  if (opts.transport === 'uds') {
    if (!opts.socket) {
      throw new Error('UDS transport requires --socket');
    }
    const socket = net.connect({path: opts.socket});
    return {input: socket, output: socket, close: () => socket.end()};
  }
  if (opts.transport === 'stdio') {
    if (!opts.adapter) {
      throw new Error('stdio transport requires --adapter');
    }
    const child = spawn(opts.adapter, opts.adapterArgs, {stdio: ['pipe', 'pipe', 'pipe']});
    child.stderr.on('data', (data) => process.stderr.write(data));
    return {input: child.stdout, output: child.stdin, close: () => child.kill()};
  }
  throw new Error(`Unknown transport: ${opts.transport}`);
}

function applyDiscovery(opts) {
  const dir = path.join(os.homedir(), '.intellij-lsp');
  let discoveryFile = null;
  if (opts.projectPath) {
    const hash = javaStringHashCode(normalizePath(opts.projectPath));
    const hex = hash < 0 ? `-${Math.abs(hash).toString(16)}` : hash.toString(16);
    discoveryFile = path.join(dir, `dap-project-${hex}.json`);
    if (!fs.existsSync(discoveryFile)) {
      throw new Error(`Discovery file not found for project: ${discoveryFile}`);
    }
  } else {
    if (!fs.existsSync(dir)) {
      throw new Error(`Discovery directory not found: ${dir}`);
    }
    const entries = fs.readdirSync(dir)
      .filter((name) => name.startsWith('dap-project-') && name.endsWith('.json'))
      .map((name) => {
        const full = path.join(dir, name);
        const stat = fs.statSync(full);
        return {full, mtimeMs: stat.mtimeMs};
      })
      .sort((a, b) => b.mtimeMs - a.mtimeMs);
    if (entries.length === 0) {
      throw new Error(`No discovery files found in ${dir}`);
    }
    discoveryFile = entries[0].full;
  }

  const content = fs.readFileSync(discoveryFile, 'utf8');
  const info = JSON.parse(content);
  if (info.transport === 'tcp') {
    opts.transport = 'tcp';
    opts.host = opts.host || '127.0.0.1';
    opts.port = info.port;
  } else if (info.transport === 'uds') {
    opts.transport = 'uds';
    opts.socket = info.socketPath;
  } else {
    throw new Error(`Unsupported discovered transport: ${info.transport}`);
  }
}

function javaStringHashCode(text) {
  let hash = 0;
  for (let i = 0; i < text.length; i++) {
    hash = ((hash * 31) + text.charCodeAt(i)) | 0;
  }
  return hash;
}

function normalizePath(inputPath) {
  return inputPath.replace(/\\+/g, '\\');
}

function shouldPrintEvent(evt, opts) {
  if (opts.quietEvents) return false;
  if (opts.suppressOutputEvents && evt.event === 'output') return false;
  if (opts.eventFilter) {
    const allowed = opts.eventFilter.split(',').map((s) => s.trim()).filter(Boolean);
    if (allowed.length > 0 && !allowed.includes(evt.event)) {
      return false;
    }
  }
  return true;
}

function getEventLogStream(opts) {
  if (!opts.eventLogPath) return null;
  return fs.createWriteStream(opts.eventLogPath, {flags: 'a'});
}

function attachDiagnostics(conn, opts, eventBuffer, printLine) {
  const eventLogStream = getEventLogStream(opts);
  conn.on('request', (req) => {
    if (opts.showRequests) {
      const output = formatOutput(req, opts.format, opts.pretty, `Request: ${req.command}`);
      if (printLine) {
        printLine(output);
      } else {
        process.stdout.write(output + '\n');
      }
    }
    if (opts.autoReply) {
      conn.reply(req, false, undefined, 'Client does not support server-initiated requests');
    }
  });

  conn.on('event', (evt) => {
    if (eventBuffer) {
      eventBuffer.push(evt);
    }
    if (opts.repl && shouldPrintEvent(evt, opts)) {
      const output = formatOutput(evt, opts.format, opts.pretty, `Event: ${evt.event}`);
      if (eventLogStream) {
        eventLogStream.write(output + '\n');
      } else {
        if (printLine) {
          printLine(output);
        } else {
          process.stdout.write(output + '\n');
        }
      }
    }
  });

  conn.on('close', (err) => {
    if (err) {
      console.error(`Connection closed: ${err.message}`);
    }
    if (eventLogStream) {
      eventLogStream.end();
    }
    process.exitCode = 1;
  });
}

async function handleCommandLine(conn, opts, line, printLine, eventBuffer, onIncompleteJson) {
  const input = line.trim();
  if (!input) return true;
  if (input.startsWith('#') || input.startsWith('//')) return true;
  if (input === 'exit' || input === 'quit') {
    return false;
  }
  if (input === 'help') {
    usage();
    return true;
  }

  const parts = input.split(' ');
  const cmd = parts[0];
  const rest = input.slice(cmd.length).trim();

  if (cmd === 'attach-current') {
    const response = await withTimeout(conn.sendRequest('attach', {}), opts.timeout, 'attach');
    const output = formatOutput(response, opts.format, opts.pretty, 'Response: attach');
    printLine(output);
    return true;
  }

  if (cmd === 'wait-event') {
    const [name, timeoutRaw] = rest.split(' ').filter(Boolean);
    const timeoutMs = timeoutRaw ? parseInt(timeoutRaw, 10) : opts.timeout;
    try {
      const evt = await waitForEvent(conn, name || null, timeoutMs, eventBuffer);
      const output = formatOutput(evt, opts.format, opts.pretty, `Event: ${evt.event}`);
      printLine(output);
    } catch (err) {
      console.error(err.message);
    }
    return true;
  }
  if (cmd === 'format') {
    if (rest) opts.format = rest;
    printLine(`format=${opts.format}`);
    return true;
  }
  if (cmd === 'pretty') {
    opts.pretty = rest === 'on' || rest === 'true' || rest === '';
    printLine(`pretty=${opts.pretty}`);
    return true;
  }
  if (cmd === 'timeout') {
    const next = parseInt(rest, 10);
    if (!Number.isNaN(next)) {
      opts.timeout = next;
    }
    printLine(`timeout=${opts.timeout}`);
    return true;
  }

  let args = undefined;
  if (rest) {
    try {
      if (rest.startsWith('@')) {
        args = readJsonArg(rest);
      } else {
        args = JSON.parse(rest);
      }
    } catch (err) {
      if (
        onIncompleteJson &&
        !rest.startsWith('@') &&
        /Unexpected end of JSON input/i.test(err.message || '')
      ) {
        onIncompleteJson(cmd, rest);
        return true;
      }
      console.error(`Invalid JSON args: ${err.message}`);
      console.error('Tip: use @file or --script for large payloads.');
      return true;
    }
  }

  try {
    const response = await withTimeout(conn.sendRequest(cmd, args), opts.timeout, cmd);
    const output = formatOutput(response, opts.format, opts.pretty, `Response: ${cmd}`);
    printLine(output);
  } catch (err) {
    console.error(err.message);
  }
  return true;
}

async function runSingle(opts) {
  const connInfo = connect(opts);
  const conn = new DapConnection(connInfo.input, connInfo.output);
  const eventBuffer = [];
  attachDiagnostics(conn, opts, eventBuffer);

  const args = opts.args ? readJsonArg(opts.args) : undefined;
  const response = await withTimeout(conn.sendRequest(opts.command, args), opts.timeout, opts.command);
  const output = formatOutput(response, opts.format, opts.pretty, `Response: ${opts.command}`);
  process.stdout.write(output + '\n');

  if (opts.waitEvent) {
    const evt = await withTimeout(
      waitForEvent(conn, opts.waitEvent, opts.timeout, eventBuffer),
      opts.timeout,
      opts.waitEvent
    );
    const evtOut = formatOutput(evt, opts.format, opts.pretty, `Event: ${evt.event}`);
    process.stdout.write(evtOut + '\n');
  }

  connInfo.close();
}

async function runScript(opts) {
  const connInfo = connect(opts);
  const conn = new DapConnection(connInfo.input, connInfo.output);
  const eventBuffer = [];
  attachDiagnostics(conn, opts, eventBuffer);

  const content = fs.readFileSync(opts.script, 'utf8');
  const lines = content.split(/\r?\n/);
  const printLine = (text) => process.stdout.write(text + '\n');

  for (const line of lines) {
    const shouldContinue = await handleCommandLine(conn, opts, line, printLine, eventBuffer);
    if (!shouldContinue) {
      break;
    }
  }

  connInfo.close();
}

function startRepl(opts) {
  const connInfo = connect(opts);
  const conn = new DapConnection(connInfo.input, connInfo.output);
  const eventBuffer = [];

  const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout,
    prompt: 'dap> '
  });

  let printChain = Promise.resolve();
  function printLine(text) {
    printChain = printChain.then(() => {
      readline.clearLine(process.stdout, 0);
      readline.cursorTo(process.stdout, 0);
      process.stdout.write(text + '\n');
      rl.prompt(true);
    });
  }

  attachDiagnostics(conn, opts, eventBuffer, printLine);

  let pendingJson = null;
  function onIncompleteJson(cmd, partial) {
    pendingJson = {cmd, buf: partial};
    rl.setPrompt('...> ');
    rl.prompt();
  }

  rl.prompt();

  rl.on('line', async (line) => {
    if (pendingJson) {
      pendingJson.buf += '\n' + line;
      try {
        const args = JSON.parse(pendingJson.buf);
        const cmd = pendingJson.cmd;
        pendingJson = null;
        rl.setPrompt('dap> ');
        const response = await withTimeout(conn.sendRequest(cmd, args), opts.timeout, cmd);
        const output = formatOutput(response, opts.format, opts.pretty, `Response: ${cmd}`);
        printLine(output);
      } catch (err) {
        if (/Unexpected end of JSON input/i.test(err.message || '')) {
          rl.prompt();
          return;
        }
        console.error(`Invalid JSON args: ${err.message}`);
        console.error('Tip: use @file or --script for large payloads.');
        pendingJson = null;
        rl.setPrompt('dap> ');
      }
      rl.prompt();
      return;
    }

    const shouldContinue = await handleCommandLine(conn, opts, line, printLine, eventBuffer, onIncompleteJson);
    if (!shouldContinue) {
      rl.close();
      return;
    }
    rl.prompt();
  });

  rl.on('close', () => {
    connInfo.close();
  });
}

async function main() {
  const opts = parseArgs(process.argv.slice(2));
  if (opts.help || (!opts.repl && !opts.command && !opts.script)) {
    usage();
    process.exitCode = opts.help ? 0 : 1;
    return;
  }

  try {
    if (opts.repl) {
      startRepl(opts);
    } else if (opts.script) {
      await runScript(opts);
    } else {
      await runSingle(opts);
    }
  } catch (err) {
    console.error(err.message);
    process.exitCode = 1;
  }
}

main();

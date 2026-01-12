const {EventEmitter} = require('events');

class DapConnection extends EventEmitter {
  constructor(input, output) {
    super();
    this.input = input;
    this.output = output;
    this.buffer = Buffer.alloc(0);
    this.pending = new Map();
    this.seq = 1;
    this.closed = false;

    this._onData = this._onData.bind(this);
    input.on('data', this._onData);
    input.on('close', () => this._close(new Error('Input closed')));
    input.on('error', (err) => this._close(err));
    output.on('error', (err) => this._close(err));
  }

  nextSeq() {
    return this.seq++;
  }

  send(message) {
    if (this.closed) return;
    const json = JSON.stringify(message);
    const payload = Buffer.from(json, 'utf8');
    const header = Buffer.from(`Content-Length: ${payload.length}\r\n\r\n`, 'utf8');
    this.output.write(Buffer.concat([header, payload]));
  }

  sendRequest(command, args) {
    const seq = this.nextSeq();
    const msg = { seq, type: 'request', command };
    if (args !== undefined) msg.arguments = args;

    return new Promise((resolve, reject) => {
      this.pending.set(seq, { resolve, reject, command });
      this.send(msg);
    });
  }

  reply(request, success, body, message) {
    const response = {
      seq: this.nextSeq(),
      type: 'response',
      request_seq: request.seq,
      success: !!success,
      command: request.command
    };
    if (body !== undefined) response.body = body;
    if (!success && message) response.message = message;
    this.send(response);
  }

  _onData(data) {
    this.buffer = Buffer.concat([this.buffer, data]);
    while (true) {
      const headerEnd = this.buffer.indexOf('\r\n\r\n');
      if (headerEnd === -1) return;
      const header = this.buffer.slice(0, headerEnd).toString('utf8');
      const match = header.match(/Content-Length: (\d+)/i);
      if (!match) {
        this._close(new Error('Missing Content-Length header'));
        return;
      }
      const length = parseInt(match[1], 10);
      const total = headerEnd + 4 + length;
      if (this.buffer.length < total) return;
      const body = this.buffer.slice(headerEnd + 4, total).toString('utf8');
      this.buffer = this.buffer.slice(total);

      let message;
      try {
        message = JSON.parse(body);
      } catch (e) {
        this.emit('error', e);
        continue;
      }
      this._dispatch(message);
    }
  }

  _dispatch(message) {
    if (message.type === 'response') {
      const pending = this.pending.get(message.request_seq);
      if (pending) {
        this.pending.delete(message.request_seq);
        if (message.success) {
          pending.resolve(message);
        } else {
          const err = new Error(message.message || 'Request failed');
          err.response = message;
          pending.reject(err);
        }
      } else {
        this.emit('response', message);
      }
      return;
    }

    if (message.type === 'event') {
      this.emit('event', message);
      return;
    }

    if (message.type === 'request') {
      this.emit('request', message);
      return;
    }

    this.emit('message', message);
  }

  _close(err) {
    if (this.closed) return;
    this.closed = true;
    this.emit('close', err);
    for (const [, pending] of this.pending.entries()) {
      pending.reject(err);
    }
    this.pending.clear();
  }
}

module.exports = {
  DapConnection
};

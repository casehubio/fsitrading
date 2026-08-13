interface PriceTick {
  instrument: string;
  price: number;
  volume: number;
  timestamp: string;
  anomaly: boolean;
}

interface RegimeAssessment {
  instrument: string;
  regime: string;
  confidence: number;
  rationale: string;
}

interface OHLCV {
  instrument: string;
  close: number;
  windowStart: string;
  windowEnd: string;
}

interface WireEvent {
  op: string;
  topic?: string;
  payload?: unknown;
  seq?: number;
  id?: string;
}

const INSTRUMENTS = ['AAPL', 'MSFT', 'GOOGL', 'AMZN', 'NVDA'];
const MAX_SPARKLINE = 20;

export class FsiMarketPanel extends HTMLElement {
  private ws: WebSocket | null = null;
  private prices: Map<string, number> = new Map();
  private regimes: Map<string, string> = new Map();
  private closes: Map<string, number[]> = new Map();
  private requestId = 0;

  connectedCallback(): void {
    this.innerHTML = this.renderShell();
    this.connect();
  }

  disconnectedCallback(): void {
    this.ws?.close(1000, 'panel removed');
    this.ws = null;
  }

  private renderShell(): string {
    const rows = INSTRUMENTS.map(sym => `
      <tr id="row-${sym}">
        <td class="sym">${sym}</td>
        <td class="price" id="price-${sym}">—</td>
        <td class="regime" id="regime-${sym}">—</td>
        <td class="sparkline" id="spark-${sym}"></td>
      </tr>
    `).join('');

    return `
      <style>
        :host { display: block; font-family: system-ui, sans-serif; }
        .fsi-panel { padding: 1rem; }
        .fsi-panel h3 { margin: 0 0 0.75rem; font-size: 1rem; }
        .status { font-size: 0.75rem; color: #888; margin-bottom: 0.5rem; }
        .status.connected { color: #2a2; }
        table { width: 100%; border-collapse: collapse; font-size: 0.875rem; }
        th, td { padding: 0.375rem 0.5rem; text-align: left; }
        th { border-bottom: 2px solid #333; font-weight: 600; }
        td { border-bottom: 1px solid #eee; }
        .price { font-variant-numeric: tabular-nums; font-weight: 500; }
        .regime { text-transform: uppercase; font-size: 0.75rem; font-weight: 600; letter-spacing: 0.05em; }
        .regime-trending { color: #16a34a; }
        .regime-volatile { color: #dc2626; }
        .regime-range_bound { color: #ca8a04; }
        .regime-mean_reverting { color: #2563eb; }
        .sparkline svg { height: 24px; width: 100%; }
        .sparkline polyline { fill: none; stroke: #6366f1; stroke-width: 1.5; }
        .anomaly { background: #fef2f2; }
      </style>
      <div class="fsi-panel">
        <h3>Market Pulse</h3>
        <div class="status" id="ws-status">connecting…</div>
        <table>
          <thead><tr><th>Symbol</th><th>Price</th><th>Regime</th><th>Trend</th></tr></thead>
          <tbody>${rows}</tbody>
        </table>
      </div>
    `;
  }

  private connect(): void {
    const proto = location.protocol === 'https:' ? 'wss:' : 'ws:';
    const url = `${proto}//${location.host}/ws/push`;
    this.ws = new WebSocket(url);

    this.ws.onopen = () => {
      this.setStatus('connected', true);
      const id = String(++this.requestId);
      this.ws!.send(JSON.stringify({
        op: 'listen',
        id,
        topics: ['market:ticks:*', 'market:bars:*', 'market:regime:*'],
      }));
    };

    this.ws.onmessage = (e: MessageEvent) => {
      this.handleMessage(e.data as string);
    };

    this.ws.onclose = () => {
      this.setStatus('disconnected', false);
      setTimeout(() => this.connect(), 3000);
    };
  }

  private handleMessage(data: string): void {
    let parsed: unknown;
    try { parsed = JSON.parse(data); } catch { return; }

    const messages: WireEvent[] = Array.isArray(parsed)
      ? parsed as WireEvent[]
      : [parsed as WireEvent];

    for (const msg of messages) {
      if (msg.op === 'event' && msg.topic && msg.payload) {
        this.handleEvent(msg.topic, msg.payload);
      }
    }
  }

  private handleEvent(topic: string, payload: unknown): void {
    if (topic.startsWith('market:ticks:')) {
      const tick = payload as PriceTick;
      this.prices.set(tick.instrument, tick.price);
      this.updatePrice(tick.instrument, tick.price, tick.anomaly);
    } else if (topic.startsWith('market:bars:')) {
      const bar = payload as OHLCV;
      const history = this.closes.get(bar.instrument) ?? [];
      history.push(bar.close);
      if (history.length > MAX_SPARKLINE) history.shift();
      this.closes.set(bar.instrument, history);
      this.updateSparkline(bar.instrument, history);
    } else if (topic.startsWith('market:regime:')) {
      const regime = payload as RegimeAssessment;
      this.regimes.set(regime.instrument, regime.regime);
      this.updateRegime(regime.instrument, regime.regime);
    }
  }

  private updatePrice(instrument: string, price: number, anomaly: boolean): void {
    const el = this.querySelector(`#price-${instrument}`);
    if (el) {
      el.textContent = price.toFixed(2);
      const row = this.querySelector(`#row-${instrument}`);
      row?.classList.toggle('anomaly', anomaly);
    }
  }

  private updateRegime(instrument: string, regime: string): void {
    const el = this.querySelector(`#regime-${instrument}`);
    if (el) {
      el.textContent = regime.replace('_', ' ');
      el.className = `regime regime-${regime.toLowerCase()}`;
    }
  }

  private updateSparkline(instrument: string, values: number[]): void {
    const el = this.querySelector(`#spark-${instrument}`);
    if (!el || values.length < 2) return;

    const min = Math.min(...values);
    const max = Math.max(...values);
    const range = max - min || 1;
    const w = 120;
    const h = 24;

    const points = values.map((v, i) => {
      const x = (i / (values.length - 1)) * w;
      const y = h - ((v - min) / range) * (h - 4) - 2;
      return `${x.toFixed(1)},${y.toFixed(1)}`;
    }).join(' ');

    el.innerHTML = `<svg viewBox="0 0 ${w} ${h}" preserveAspectRatio="none"><polyline points="${points}"/></svg>`;
  }

  private setStatus(text: string, connected: boolean): void {
    const el = this.querySelector('#ws-status');
    if (el) {
      el.textContent = text;
      el.classList.toggle('connected', connected);
    }
  }
}

customElements.define('fsi-market-panel', FsiMarketPanel);

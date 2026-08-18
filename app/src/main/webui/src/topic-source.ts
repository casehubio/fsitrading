import type { DataSource, DataSink } from "@casehubio/pages-data";

interface TopicEvent {
  op: string;
  topic?: string;
  payload?: unknown;
  seq?: number;
  id?: string;
}

interface TopicSourceOptions {
  readonly accumulate?: boolean;
  readonly keyField?: string;
}

export function topicSource(
  topics: string[],
  options?: TopicSourceOptions,
): DataSource {
  let ws: WebSocket | null = null;
  let sink: DataSink | null = null;
  let requestId = 0;
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  let reconnectAttempt = 0;
  const rows = new Map<string, Record<string, unknown>>();

  function connect(): void {
    const proto = location.protocol === "https:" ? "wss:" : "ws:";
    const url = `${proto}//${location.host}/ws/push`;
    ws = new WebSocket(url);

    ws.onopen = () => {
      reconnectAttempt = 0;
      const id = String(++requestId);
      ws!.send(JSON.stringify({ op: "listen", id, topics }));
    };

    ws.onmessage = (e: MessageEvent) => {
      handleMessages(e.data as string);
    };

    ws.onclose = () => {
      if (sink) {
        const delay = Math.min(1000 * 2 ** reconnectAttempt, 30000);
        reconnectAttempt++;
        reconnectTimer = setTimeout(() => connect(), delay);
      }
    };
  }

  function handleMessages(data: string): void {
    let parsed: unknown;
    try {
      parsed = JSON.parse(data);
    } catch {
      return;
    }

    const messages: TopicEvent[] = Array.isArray(parsed)
      ? (parsed as TopicEvent[])
      : [parsed as TopicEvent];

    for (const msg of messages) {
      if (msg.op === "event" && msg.topic && msg.payload && sink) {
        const row = msg.payload as Record<string, unknown>;
        const key = options?.keyField
          ? String(row[options.keyField] ?? msg.topic)
          : msg.topic;

        if (options?.accumulate !== false) {
          rows.set(key, row);
          sink.apply({
            type: "snapshot",
            columns: Object.keys(row).map((name) => ({ name })),
            rows: Array.from(rows.values()),
          });
        } else {
          sink.apply({
            type: "snapshot",
            columns: Object.keys(row).map((name) => ({ name })),
            rows: [row],
          });
        }
      }
    }
  }

  return {
    connect(s: DataSink): void {
      sink = s;
      connect();
    },

    disconnect(): void {
      sink = null;
      rows.clear();
      if (reconnectTimer) {
        clearTimeout(reconnectTimer);
        reconnectTimer = null;
      }
      if (ws) {
        ws.onclose = null;
        ws.close(1000, "disconnected");
        ws = null;
      }
    },
  };
}

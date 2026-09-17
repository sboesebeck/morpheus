# Morpheus

**A TUI-first toolbox and live monitor for [Morphium](https://github.com/sboesebeck/morphium) messaging.**

Morphium is a feature-rich abstraction layer for MongoDB — declarative caching, object mapping, and a sophisticated message-queuing system. Morpheus is the operator's cockpit for that message bus: a terminal UI (and a scriptable CLI) to watch messages flow in real time, inspect topics and nodes, and check the health of every connected Morphium instance.

```
┌─ Morpheus ──────────────────────────────────────────────────────────┐
│  Connections            Views                                        │
│  ▶ messagebus_prod      ▶ messages   live message monitor            │
│    genios_staging         topics     per-topic aggregates            │
│    local                  nodes      sender → answerer pairs          │
│                           status     per-node health (top-like)      │
│                           graph      message-flow graph              │
│                           latency    live latency graph / load test  │
└──────────────────────────────────────────────────────────────────────┘
```

## Highlights

- **TUI-first** — run `morpheus` with no arguments and you get an interactive launcher: pick a connection, pick a view, go. Built on [Lanterna](https://github.com/mabe02/lanterna), no X required.
- **Live views** that share one rendering core:
  - **messages** — top-like live monitor with a configurable rolling message history (default 1,000): sender/host, topic, size, processing state, exclusivity, answer + round-trip time, V5/V6 protocol tags, timeout highlighting, paging, and a detail snapshot that survives database deletion until eviction.
  - **topics** — per-topic aggregates: message/answer counts, average RTT, timeouts.
  - **nodes** — sender → answerer pair distribution (counts + avg RTT), keyed by host or sender; surfaces load imbalance.
  - **status** — a *top*-style per-node health monitor: heap usage, cache hit ratio, driver connections, errors, messages sent, threads, RTT — with a per-node **drill-down** to the full status dump (JVM, cache, driver, messaging, replica-set, config).
  - **graph** — animated message-flow graph: nodes on a ring, messages as topic-coloured shots over a Braille canvas, request→answer played sequentially, timeouts in red; optional anti-aliased raster rendering on Kitty/Ghostty/WezTerm (`g`).
  - **latency** — live latency graph and load test: Braille RTT time series or histogram, per-responder percentile table (min/avg/p50/p90/p99, jitter, outbound/return split), interval/fixed-rate/ramp send modes with adjustable payload and sender threads, plus the standalone `pong` echo responder to point it at.
- **CLI = shortcuts for the TUI** — every live view has a same-named command that opens the TUI straight on that view: `morpheus messages -c prod`, `morpheus topics`, `morpheus nodes`, `morpheus status`, `morpheus latency`.
- **Scriptable commands** for everything that doesn't belong in an interactive UI: one-shot status `ping` (with filters), `latency --headless` (JSON/CSV load-test reports), `pong` (latency echo responder), `send`, raw change-stream `watch`, and `config`.
- **Connection management** with SOCKS/SSH-tunnel support, multiple named connections, a default connection, and themes — all editable in the TUI or via `config`.

## Requirements

- Java 21
- Maven 3.x
- Access to a Morphium-enabled MongoDB instance (MongoDB 5.0+, Morphium 6.x)

## Quick start

```bash
# Build (produces a runnable jar; ./run.sh compiles on demand too)
mvn clean package

# Add a connection (interactive) and make it the default
./run.sh config connection add prod
./run.sh config connection default prod

# Launch the TUI — pick a connection and a view
./run.sh

# …or jump straight to a view from the CLI
./run.sh messages -c prod        # live message monitor
./run.sh status -c prod          # per-node health monitor
```

`run.sh` is a thin wrapper that recompiles changed sources and runs
`de.caluga.morpheus.Morpheus`. A first run creates the config file in your home
directory; `./run.sh --help` lists everything.

## The TUI

Running `morpheus` with no command opens the **launcher**: a connections column
and a views column (Tab switches columns, Enter opens the selected view; the
connection is built asynchronously behind a spinner). Connection CRUD lives here
too, and `Space` marks the selected connection as the **default** (shown with a
trailing `*`, used when you don't pass `-c`).

Common keys inside a view: `Esc` back, `q` quit. The **status** view adds
`↑/↓` to select a node, `+`/`-` to change the ping timeout (presets
2/5/10/15/30 s), `r` to re-ping, and `Enter` to open the selected node's detail.
The **messages** view uses `+`/`-` to resize its rolling history immediately
(presets from 100 to 10,000, default 1,000). Navigating into older messages
automatically pauses the visible snapshot; `p` resumes live mode at the newest
message while the bounded buffer continues collecting in the background.

## The CLI

| Command | What it does |
|---|---|
| `messages` | Open the TUI on the live message monitor |
| `topics` | Open the TUI on the per-topic view |
| `nodes` | Open the TUI on the sender→answerer view |
| `status` | Open the TUI on the per-node health monitor |
| `graph` | Open the TUI on the animated message-flow graph |
| `latency` | Open the TUI on the live latency graph / load test (`--headless` for scripting: `--json/--csv`, `--max-loss`) |
| `pong` | Latency echo responder — answers pings on the latency topic until Ctrl-C |
| `ping` | One-shot status ping of all nodes (table; `--filter-host/-sender`, `--level`, `--keys`) |
| `send` | Send a message to a topic |
| `watch` | Raw change-stream debug viewer |
| `config` | Manage connections, default connection, proxy, themes |

The view commands take the global options `-c/--connection`, `--messaging`,
`--theme`, `-v/--verbose`. Every command has `--help`.

## Examples

A quick look at each command. Hosts, node names, and topics below are
placeholders — swap in your own.

### `messages`

```
$ ./run.sh messages -c prod
┌ messages ───────────────────────────────────────────────── prod ─┐
│ up 02:14:07   142 msg/s   12,480 msgs   9,911 answers   3 timeout │
│  #  Time  Sender     Host        Topic           Size Proc An RTT │
│  1   0s   worker-3   host-3.lan  order.created    412 done  Y  8ms│
│  2   1s   worker-1   host-1.lan  invoice.render   188 done  Y 14ms│
│  3   2s   scheduler  host-2.lan  mail.send         96 done  N   – │
└─────────────────────────────────────────────────────────────────┘
```

### `topics`

```
$ ./run.sh topics -c prod
┌ topics ────────────────────────────────────┐
│ Topic            Nachr.  Antw.  Ø-RTT  Timeout│
│ order.created      842    840    9ms      2   │
│ invoice.render     311    311   14ms      0   │
│ mail.send          120    118   22ms      1   │
└────────────────────────────────────────────┘
```

### `nodes`

```
$ ./run.sh nodes -c prod
┌ nodes ────────────────────────────────────────┐
│ Sender → Antwortender      Anzahl   Ø-RTT      │
│ worker-1 → scheduler          412    9ms       │
│ worker-3 → scheduler          398   11ms       │
│ worker-2 → db-writer           77   18ms       │
└─────────────────────────────────────────────────┘
```

### `status`

```
$ ./run.sh status -c prod
┌ status ─ ping timeout 5s ──────────────────────────────────────────────┐
│ Sender@Host          RTT  Mem(u/max)   Hit%  Conn(u/p)  Err Msg-sent Thr│
│ worker-1@host-1.lan   6ms 512M/2048M  98.2%    2/10       0   18,420  24│
│ worker-2@host-2.lan   9ms 480M/2048M  97.8%    1/10       0   17,004  22│
│ worker-3@host-3.lan    –      –          –       –        –        –   –│
└──────────────────────────────────────────────────────────────────────┘
```

### `graph`

```
                         Message Flow
                ╭───────────────────────────────╮
                │        ·hermes·                │
                │      ╱        ·•╲               │   •  = a message in flight
                │  ·worker3·      ·worker1·       │       (coloured by topic)
                │     │     ◦sched◦    │          │   ◦  = a busy hub, pulled
                │  ·worker2·      ·worker4·       │       toward the centre
                │         ╲      ╱                │
                │          ·db-writer·            │
                ╰───────────────────────────────╯
   Topics:  ● order.created   ● invoice.render   ● mail.send   ● (timeout)
   [p] pause   [h] hosts   [g] grafik   [esc] zurück   [q] quit
```

### `latency`

```
$ ./run.sh latency -c prod
┌ latency ─ payload:10k threads:4 mode:rate 100/s ──────────────────────┐
│ RTT(ms)                                                                │
│ 40 ┤            ╭╮                                                    │
│ 20 ┤╭╮╭─╮╭──────╯╰╮╭───────╮       ×                                  │
│  0 ┼╯╰╯ ╰╯        ╰╯       ╰──────────────────                        │
│      Responder      cnt loss%  avg  p50  p90  p99  jit                │
│      pong-1          842   0.2   14   12   22   38    3               │
└────────────────────────────────────────────────────────────────────────┘
```

### `pong`

```
$ ./run.sh pong --name pong-1
pong 'pong-1' listening on topic morpheus_latency — Ctrl-C to stop
answered: 1204  (+241 in 5s, 48.2/s)
answered: 1449  (+245 in 5s, 49.0/s)
```

### `ping`

```
$ ./run.sh ping --level PING
Sender     | Host        | After ms
worker-1   | host-1.lan  | 6ms
worker-2   | host-2.lan  | 9ms

$ ./run.sh ping --json --level PING --wait 5
{"sentAt":1758100000000,"waitSec":5,"level":"PING","expectAnswers":10000,"answerCount":2,
 "answers":[{"sender":"worker-1","host":"host-1.lan","rttMs":6,"payload":null},
            {"sender":"worker-2","host":"host-2.lan","rttMs":9,"payload":null}]}
```

`--json` is meant for automated evaluation, e.g. feeding a monitoring
backend: exit code `2` if zero answers came back, `0` otherwise.

### `send`

```
$ ./run.sh send --topic order.created --msg "hello" --num-answers 1 --wait 5
sending message....(waiting 5s for answers)
got messages:1
Sender     | Host
worker-1   | host-1.lan
```

### `watch`

```
$ ./run.sh watch -c prod
Incoming event...INSERT
     ns : msgdb.msg
     doc: {sender: worker-1, name: order.created, ...}
Incoming event...UPDATE
     ns : msgdb.msg
     doc: {sender: worker-1, name: order.created, processed_by: [scheduler], ...}
```

### `config`

```
$ ./run.sh config connection list
Available Connections:

  staging
    Host(s): host-1.lan:27017
    Database: msgdb
    Messaging: single
    SSL: no

  prod
    Host(s): host-2.lan:27017,host-3.lan:27017
    Database: msgdb
    Messaging: multi
    SSL: yes
```

## Configuration

Connections, the default connection, SOCKS proxy settings, and themes are stored
in a properties file and managed either interactively in the launcher or via the
`config` command. See **[CONFIG_MANAGEMENT.md](CONFIG_MANAGEMENT.md)**.

## Documentation

- **[docs/views.md](docs/views.md)** — every view explained, column by column
- **[USAGE.md](USAGE.md)** — usage guide with examples
- **[CONFIG_MANAGEMENT.md](CONFIG_MANAGEMENT.md)** — connections, proxy, themes
- **[CHEATSHEET.md](CHEATSHEET.md)** — quick command reference

## Architecture

- **`core/`** — framework-free data layer (no UI dependency): message tracking,
  aggregation, status pinging. The same core feeds both the TUI screens and the
  scriptable CLI, so there is a single source of truth for what the data means.
- **`tui/`** — a thin Lanterna screen stack (launcher + view screens) with an
  explicit screen/result model.
- **`cli/`** — picocli command tree; live-view commands are thin openers onto the
  TUI, scriptable commands run and exit.

## Status & roadmap

Morpheus is under active redesign toward a TUI-first cockpit. Done: the launcher,
the messages/topics/nodes/status views, the unified CLI, and the **graph view**
(animated message flow over a Braille canvas, coloured by topic, with optional
Kitty-graphics raster rendering on supported terminals).

## License

See the repository for license details.

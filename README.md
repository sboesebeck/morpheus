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
│                           graph      message-flow graph (coming)      │
└──────────────────────────────────────────────────────────────────────┘
```

## Highlights

- **TUI-first** — run `morpheus` with no arguments and you get an interactive launcher: pick a connection, pick a view, go. Built on [Lanterna](https://github.com/mabe02/lanterna), no X required.
- **Live views** that share one rendering core:
  - **messages** — top-like live monitor: every message with sender/host, topic, size, processing state, exclusivity, answer + round-trip time, V5/V6 protocol tags, timeout highlighting.
  - **topics** — per-topic aggregates: message/answer counts, average RTT, timeouts.
  - **nodes** — sender → answerer pair distribution (counts + avg RTT), keyed by host or sender; surfaces load imbalance.
  - **status** — a *top*-style per-node health monitor: heap usage, cache hit ratio, driver connections, errors, messages sent, threads, RTT — with a per-node **drill-down** to the full status dump (JVM, cache, driver, messaging, replica-set, config).
- **CLI = shortcuts for the TUI** — every live view has a same-named command that opens the TUI straight on that view: `morpheus messages -c prod`, `morpheus topics`, `morpheus nodes`, `morpheus status`.
- **Scriptable commands** for everything that doesn't belong in an interactive UI: one-shot status `ping` (with Graphite export and filters), `send`, raw change-stream `watch`, and `config`.
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
too.

Common keys inside a view: `Esc` back, `q` quit. The **status** view adds
`↑/↓` to select a node, `+`/`-` to change the ping timeout (presets
2/5/10/15/30 s), `r` to re-ping, and `Enter` to open the selected node's detail.

## The CLI

| Command | What it does |
|---|---|
| `messages` | Open the TUI on the live message monitor |
| `topics` | Open the TUI on the per-topic view |
| `nodes` | Open the TUI on the sender→answerer view |
| `status` | Open the TUI on the per-node health monitor |
| `ping` | One-shot status ping of all nodes (table; `--graphite host[:port]`, `--filter-host/-sender`, `--level`, `--keys`) |
| `send` | Send a message to a topic |
| `watch` | Raw change-stream debug viewer |
| `config` | Manage connections, default connection, proxy, themes |

The view commands take the global options `-c/--connection`, `--messaging`,
`--theme`, `-v/--verbose`. Every command has `--help`.

## Configuration

Connections, the default connection, SOCKS proxy settings, and themes are stored
in a properties file and managed either interactively in the launcher or via the
`config` command. See **[CONFIG_MANAGEMENT.md](CONFIG_MANAGEMENT.md)**.

## Documentation

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
the messages/topics/nodes/status views, and the unified CLI. Next: a **graph
view** visualising message flow between nodes (animated over a Braille canvas,
coloured by topic).

## License

See the repository for license details.

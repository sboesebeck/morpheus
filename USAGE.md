# Morpheus Usage Guide

## Quick Start

### 1. Build the Project

```bash
mvn clean package
```

This creates `target/morpheus-1.0-SNAPSHOT-jar-with-dependencies.jar`

### 2. First Run

On first run, Morpheus creates a default configuration file:

```bash
./run.sh --help
```

This will:
- Create `~/.config/morpheus.properties` with default settings
- Show the Morpheus banner
- List all available subcommands

## Basic Usage

### Command Syntax

```bash
./run.sh [global-options] <subcommand> [subcommand-options]
```

Global options must appear **before** the subcommand.

### Available Subcommands

- **`status`** - Get status from all connected Morphium nodes
- **`send`** - Send messages through Morphium messaging
- **`monitor`** - Real-time message monitoring
- **`watch`** - Watch the MongoDB change stream
- **`config`** - Interactive configuration management (connections, themes, proxy)

Every subcommand supports `--help` for detailed inline help:
```bash
./run.sh status --help
./run.sh send --help
./run.sh config --help
```

### Examples

**Show all subcommands:**
```bash
./run.sh --help
```

**Connectivity ping (replaces the old `hello` command):**
```bash
./run.sh -c myconn status --level PING
```

**Monitor messages in real-time:**
```bash
./run.sh monitor
```

**Get status with verbose output:**
```bash
./run.sh --verbose status
```

## Configuration

### Easy Setup with `config` Command

The easiest way to configure Morpheus is using the interactive `config` command:

```bash
# Add a new connection
./run.sh config connection add production

# Set as default so -c can be omitted
./run.sh config connection default production

# Configure SOCKS proxy (for SSH tunnels)
./run.sh config proxy config production

# List all connections
./run.sh config connection list

# Validate configuration
./run.sh config validate
```

See [CONFIG_MANAGEMENT.md](CONFIG_MANAGEMENT.md) for complete configuration management guide.

### Configuration File Location

`~/.config/morpheus.properties`

### Manual Connection Setup

You can also manually edit the configuration file to add your MongoDB connection:

```properties
# Default connection
morphium.default_connection.hosts.0=localhost:27017
morphium.default_connection.database=test
morphium.default_connection.authDb=admin
morphium.default_connection.login=youruser
morphium.default_connection.password=yourpassword

# Messaging settings
morphium.default_connection.messaging.implementation=single
morphium.default_connection.messaging.processMultiple=true
morphium.default_connection.messaging.multithreadded=true
morphium.default_connection.messaging.windowSize=10
morphium.default_connection.messaging.pause=100
morphium.default_connection.messaging.queueName=msg

# SOCKS Proxy settings (optional, for SSH tunnels)
morphium.default_connection.proxy.enabled=false
morphium.default_connection.proxy.host=127.0.0.1
morphium.default_connection.proxy.port=5555
```

### Multiple Connections

You can define multiple connections:

```properties
# Production connection
morphium.production.hosts.0=prod-mongo:27017
morphium.production.database=prod_db
morphium.production.authDb=admin
morphium.production.login=produser
morphium.production.password=prodpass

# Development connection
morphium.development.hosts.0=localhost:27017
morphium.development.database=dev_db
morphium.development.authDb=admin
morphium.development.login=devuser
morphium.development.password=devpass
```

**Use a specific connection:**
```bash
./run.sh -c production status
```

**List available connections:**
```bash
./run.sh config connection list
```

**Set a default connection (omit `-c` afterwards):**
```bash
./run.sh config connection default production
./run.sh status
```

### Themes

Define custom color themes:

```properties
# Custom theme
theme.dark.c1=[fg196]
theme.dark.c2=[fg33]
theme.dark.c3=[fg226]
theme.dark.header1=[bld][fg46]
theme.dark.error=[rd]
theme.dark.warning=[y]
theme.dark.good=[gr]
theme.dark.gradient1=blue
theme.dark.gradient2=cyan
theme.dark.gradient3=purple
```

**Use a theme:**
```bash
./run.sh --theme dark status
```

**List available themes:**
```bash
./run.sh config theme list
```

## Command-Line Options

### Global Options (before subcommand)

- `-c <name>` - Select connection by name
- `--theme <name>` - Select theme
- `--messaging single|multi` - Select messaging implementation
- `-v` / `--verbose` - Show detailed startup information

### Examples

**Verbose mode:**
```bash
./run.sh --verbose status --level PING
```

**Custom theme and connection:**
```bash
./run.sh --theme dark -c production monitor
```

**Multi-collection messaging:**
```bash
./run.sh --messaging multi status
```

## Messaging

### Messaging Implementations

Morpheus supports two messaging implementations:

1. **SingleCollectionMessaging** (default)
   - All messages in one collection
   - Simpler setup
   - Good for most use cases

2. **MultiCollectionMessaging**
   - Separate collection per message type
   - Better for high-volume systems
   - More complex setup

**Configure default:**
```properties
morphium.default_connection.messaging.implementation=single
```

**Override via command-line:**
```bash
./run.sh --messaging multi monitor
```

## Advanced Usage

### Status Command

The `status` command has many options:

```bash
./run.sh status \
  --wait 30 \
  --expect-answers 50 \
  --filter-host 'prod.*' \
  --level ALL
```

**Options:**
- `-w` / `--wait <seconds>` - Wait time for responses (default: 30)
- `--expect-answers <num>` - Expected number of responses
- `--filter-host <pattern>` - Filter by hostname regex
- `--filter-sender <pattern>` - Filter by sender regex
- `--filter-path <pattern>` - Filter status paths regex
- `--exclude-path <pattern>` - Exclude status paths by regex
- `--level PING|MESSAGING_ONLY|MORPHIUM_ONLY|ALL` - Status detail level
- `--keys a,b` - Comma-separated list of keys to show
- `--graphite host[:port]` - Export metrics to Graphite

**Examples:**

```bash
# Quick ping check
./run.sh status --level PING --wait 5

# Detailed status from specific hosts
./run.sh --verbose status \
  --filter-host 'app-server-[0-9]+' \
  --level ALL

# Export to Graphite
./run.sh status --graphite localhost:2003
```

### Send Command

Send messages via Morphium messaging:

```bash
./run.sh send --topic myTopic --msg "hello world"
```

**Options:**
- `-t` / `--topic <name>` - Message topic
- `--msg <text>` - Message text
- `--value <text>` - Message value
- `--ttl <secs>` - Message time-to-live in seconds (default: wait time)
- `-n` / `--num-answers <N>` - Number of expected answers
- `-w` / `--wait <seconds>` - Wait for answers
- `--no-wait` - Do not wait for answers

### Message Monitoring

Monitor messages in real-time:

```bash
./run.sh monitor
```

**Options:**
- `--timeout <ms>` - Threshold in ms after which an unanswered request is marked as timed out (default: 2000)

This displays:
- Message counter
- Sender ID
- Recipient
- Message topic/name
- Whether it's an answer

### Watch Change Stream

Watch the MongoDB change stream in real-time:

```bash
./run.sh watch
```

### Latency / Pong

`latency` measures round-trip time over the message bus by sending pings and
matching answers (`inAnswerTo`); `pong` is a standalone echo responder to
point it at. Run with no options for the TUI (live graph/histogram, `s` to
start/stop — see `docs/views.md`), or `--headless` to run for a fixed
duration and print a summary (plus a `--timeout`-length tail wait after
`--duration` elapses, so every last-second ping is either answered or
counted as a loss before the summary is printed).

**Start a responder:**
```bash
./run.sh -c myconn pong --name pong-1
```

**Three target kinds:**
- **pong topic** (default) — a `pong` process listening on `--topic`
  (default: `morpheus_latency`). Point `latency` at the same `--topic`.
- **existing app listener topic** — any topic your own application already
  answers on; no `pong` process needed, just `--topic yourTopic`.
- **status listener** (`--status`) — pings the built-in Morphium status-info
  listener that every connected node already answers, so you can measure
  latency against real app nodes without a dedicated responder.

**Options:**
- `--topic <name>` - Ping topic (default: `morpheus_latency`)
- `--status` - Ping the Morphium status-info listener instead (real app nodes)
- `--interval <ms>` - Ping interval in ms, default send mode (default: 1000)
- `--rate <n>` - Fixed send rate in msgs/s (enables load mode)
- `--ramp <start:step:stepSecs>` - Ramp mode, e.g. `50:50:10` (start at 50/s,
  +50/s every 10s; aborts automatically past ~50% loss)
- `--threads <n>` - Sender threads (default: 1) — raise this if the achieved
  rate falls well short of `--rate`/`--ramp` on one thread
- `--payload <bytes>` - Ping padding bytes (default: 0)
- `--timeout <ms>` - Ping timeout ms (default: 5000)
- `--headless` - Run without the TUI, print a summary at the end
- `--duration <secs>` - Headless run duration in seconds (default: 60)
- `--json` / `--csv` - Machine-readable summary instead of the themed table
- `--graphite <host[:port]>` / `--prefix <name>` - Push per-responder
  metrics (`avg`, `p50`, `p90`, `p99`, `loss`) to Graphite
- `--max-loss <pct>` - Exit non-zero when total loss % exceeds this (for CI)

`pong` options: `--topic <name>` (must match `latency --topic`), `--name
<name>` (responder label, default: hostname-pid) — this name appears as the
responder key in `latency` output instead of the internal sender id.

**The `out≈`/`ret≈` split:** when the responder embeds its receive
timestamp, the round trip is split into an outbound (sender → responder) and
return (responder → sender) leg. These are approximate (`≈`) because they
depend on the two hosts' wall clocks agreeing — any clock skew between
sender and responder shows up directly in the split, including as a negative
`ret≈`. Trust the total RTT; treat `out≈`/`ret≈` as directional only, and
`?` means no receive timestamp was available (e.g. `--status` mode). The TUI
also shows `?` in place of a negative `out≈`/`ret≈` value (clock skew);
`--json`/`--csv` output prints the raw (possibly negative) value instead.

**Headless examples:**
```bash
# JSON summary, 15s run against a pong responder
./run.sh -c myconn latency --headless --interval 500 --duration 15 --json

# Load test at 100 msg/s with 4 sender threads, CSV output
./run.sh -c myconn latency --headless --rate 100 --threads 4 --duration 30 --csv

# Push percentiles to Graphite
./run.sh -c myconn latency --headless --interval 1000 --duration 60 --graphite localhost:2003

# CI-friendly: fail (exit 2) if loss exceeds 5%
./run.sh -c myconn latency --headless --rate 50 --duration 30 --json --max-loss 5

# Latency against real app nodes (no dedicated pong needed)
./run.sh -c myconn latency --headless --status --interval 1000 --duration 10 --json
```

### Running Directly with Java

```bash
java -jar target/morpheus-1.0-SNAPSHOT-jar-with-dependencies.jar <subcommand> [options]
```

Or with the helper script (includes SOCKS proxy support):

```bash
./run.sh [global-options] <subcommand> [options]
```

## Exit Codes

- `0` - Success
- `1` - Operational error (e.g. connection failed)
- `2` - Usage error (e.g. unknown option — a did-you-mean suggestion is shown)

## Troubleshooting

### Connection Issues

**Enable verbose mode to see connection details:**
```bash
./run.sh --verbose status --level PING
```

**Check configuration:**
```bash
cat ~/.config/morpheus.properties
```

**Test connection:**
```bash
./run.sh -c myconn status --level PING
```

### No Configuration File

If the configuration file doesn't exist, run any command and Morpheus will create it:

```bash
./run.sh --help
```

Then add a connection:
```bash
./run.sh config connection add myconn
```

### Theme Not Working

List available themes:
```bash
./run.sh config theme list
```

Make sure the theme exists in your configuration file.

### Messaging Errors

Check messaging configuration with verbose status:
```bash
./run.sh --verbose status --level MESSAGING_ONLY
```

Look for:
- Messaging implementation type
- Connection status
- Queue name

## Tips

1. **Use verbose mode** when troubleshooting:
   ```bash
   ./run.sh --verbose <subcommand>
   ```

2. **Set a default connection** so you don't need `-c` every time:
   ```bash
   ./run.sh config connection default myconn
   ```

3. **Create connection aliases** for frequently used configurations

4. **Use themes** to differentiate between environments:
   - `--theme production` for prod (red theme)
   - `--theme development` for dev (green theme)

5. **Export Graphite metrics** for monitoring:
   ```bash
   ./run.sh status --graphite metrics.example.com:2003
   ```

## Next Steps

- Set up your MongoDB connection in `~/.config/morpheus.properties`
- Configure messaging settings for your environment
- Create custom themes for different use cases
- Explore the `status` command for monitoring
- Use `monitor` for real-time message tracking

For more details, see [CLAUDE.md](CLAUDE.md) for architecture and development information.

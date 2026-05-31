# Morpheus Command Cheat Sheet

## Basic Commands

```bash
# Show all subcommands and global options
./run.sh --help

# Connectivity ping (replaces hello)
./run.sh status --level PING

# Configuration management
./run.sh config --help

# Monitor messages in real-time
./run.sh monitor

# Get status from all nodes
./run.sh status

# Send a message
./run.sh send --topic myTopic --msg "hello"

# Watch MongoDB change stream
./run.sh watch
```

## Configuration Management

```bash
# Add connection interactively
./run.sh config connection add myconn

# Set default connection (omit -c afterwards)
./run.sh config connection default myconn

# List connections
./run.sh config connection list

# Show connection details
./run.sh config connection show myconn

# Edit connection
./run.sh config connection edit myconn

# Remove connection
./run.sh config connection remove myconn

# Configure SOCKS proxy
./run.sh config proxy config myconn

# Enable/disable proxy
./run.sh config proxy enable myconn
./run.sh config proxy disable myconn

# List available themes
./run.sh config theme list

# Show all config
./run.sh config show

# Validate configuration
./run.sh config validate
```

## Command-Line Options

Global options go **before** the subcommand:

```bash
# Verbose output
./run.sh --verbose <subcommand>

# Use specific theme
./run.sh --theme <name> <subcommand>

# Use specific connection
./run.sh -c <name> <subcommand>

# Select messaging implementation
./run.sh --messaging single <subcommand>
./run.sh --messaging multi <subcommand>

# List available themes
./run.sh config theme list

# List available connections
./run.sh config connection list
```

## Status Examples

```bash
# Quick ping (5 seconds)
./run.sh status --level PING --wait 5

# Full status with details
./run.sh --verbose status --level ALL --wait 30

# Filter by host pattern
./run.sh status --filter-host 'app-server.*' --level ALL

# Export to Graphite
./run.sh status --graphite localhost:2003

# Specific keys only
./run.sh --verbose status --keys cpu,memory,connections

# Exclude certain paths
./run.sh status --exclude-path '/internal/.*'
```

## Send Examples

```bash
# Send a simple message
./run.sh send --topic myTopic --msg "hello world"

# Send with value and TTL
./run.sh send --topic myTopic --value "42" --ttl 60

# Send and wait for N answers
./run.sh send --topic myTopic --msg "ping" --num-answers 3 --wait 10

# Send without waiting
./run.sh send --topic myTopic --msg "fire and forget" --no-wait
```

## Configuration

### Config File Location
```
~/.config/morpheus.properties
```

### Add Connection
```properties
morphium.myconnection.hosts.0=mongodb.example.com:27017
morphium.myconnection.database=mydb
morphium.myconnection.authDb=admin
morphium.myconnection.login=user
morphium.myconnection.password=pass
morphium.myconnection.messaging.implementation=single
```

### Add Theme
```properties
theme.mytheme.c1=[fg196]
theme.mytheme.c2=[fg33]
theme.mytheme.error=[rd]
theme.mytheme.warning=[y]
theme.mytheme.good=[gr]
theme.mytheme.gradient1=blue
```

## Messaging Configuration

```properties
# Single collection (default)
morphium.default_connection.messaging.implementation=single

# Multi collection (separate per topic)
morphium.default_connection.messaging.implementation=multi

# Other messaging settings
morphium.default_connection.messaging.processMultiple=true
morphium.default_connection.messaging.multithreadded=true
morphium.default_connection.messaging.windowSize=10
morphium.default_connection.messaging.pause=100
morphium.default_connection.messaging.queueName=msg
```

## Useful Combinations

```bash
# Production monitoring with custom theme
./run.sh --theme prod_red -c production monitor

# Debug with full verbosity
./run.sh --verbose status --level ALL --wait 60

# Quick health check
./run.sh status --level PING --wait 5

# Development setup with verbose status
./run.sh --verbose --theme dev_green -c dev status --level ALL
```

## Build & Run

```bash
# Clean build
mvn clean package

# Run with helper script
./run.sh [global-options] <subcommand> [options]

# Run directly with Java
java -jar target/morpheus-1.0-SNAPSHOT-jar-with-dependencies.jar <subcommand> [options]

# Run with recompile
./run.sh --rerun <subcommand> [options]
```

## Troubleshooting

```bash
# Check config file
cat ~/.config/morpheus.properties

# Test connectivity with verbose
./run.sh --verbose status --level PING

# List available connections
./run.sh config connection list

# List available themes
./run.sh config theme list

# Check terminal size detection
./run.sh --verbose status --level PING | grep Terminal
```

## Exit Codes

- `0` - Success
- `1` - Operational error
- `2` - Usage error (unknown option → did-you-mean suggestion shown)

## Color Codes

Available in output strings:

- `[r]` - Reset
- `[rd]` - Red
- `[gr]` - Green
- `[y]` - Yellow
- `[b]` - Blue
- `[c]` - Cyan
- `[m]` - Magenta
- `[bld]` - Bold
- `[ul]` - Underline
- `[ital]` - Italic
- `[fgNNN]` - Foreground color (0-255)
- `[bgNNN]` - Background color (0-255)

Theme-specific:
- `[c1]`, `[c2]`, `[c3]` - Theme colors
- `[header1]`, `[header2]` - Headers
- `[error]`, `[warning]`, `[good]` - Status colors

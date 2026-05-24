# Morpheus Command Cheat Sheet

## Basic Commands

```bash
# List all commands
./run.sh list

# Test setup (shows colors, config)
./run.sh hello

# Configuration management
./run.sh config help

# Monitor messages in real-time
./run.sh monitor

# Get status from all nodes
./run.sh get_status

# Send a message
./run.sh send
```

## Configuration Management

```bash
# Add connection interactively
./run.sh config connection add myconn

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

# Show all config
./run.sh config show

# Validate configuration
./run.sh config validate
```

## Command-Line Options

```bash
# Verbose output
./run.sh <command> --verbose

# Use specific theme
./run.sh <command> --theme=<name>

# Use specific connection
./run.sh <command> --morphiumcfg=<name>

# Select messaging implementation
./run.sh <command> --messaging=single
./run.sh <command> --messaging=multi

# List available themes
./run.sh list --theme=?

# List available connections
./run.sh list --morphiumcfg=?
```

## Get Status Examples

```bash
# Quick ping (5 seconds)
./run.sh get_status level=PING wait=5

# Full status with details
./run.sh get_status verbose=true level=ALL wait=30

# Filter by host pattern
./run.sh get_status filter_host=app-server.* verbose=true

# Export to Graphite
./run.sh get_status graphite=localhost:2003

# Specific keys only
./run.sh get_status keys=cpu,memory,connections verbose=true
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
./run.sh monitor --morphiumcfg=production --theme=prod_red

# Debug with full verbosity
./run.sh get_status --verbose level=ALL wait=60

# Quick health check
./run.sh get_status level=PING wait=5

# Development setup
./run.sh hello --morphiumcfg=dev --theme=dev_green --verbose
```

## Build & Run

```bash
# Clean build
mvn clean package

# Run with helper script
./run.sh <command> [options]

# Run directly with Java
java -jar target/morpheus-1.0-SNAPSHOT-jar-with-dependencies.jar <command> [options]

# Run with recompile
./run.sh --rerun <command> [options]
```

## Troubleshooting

```bash
# Check config file
cat ~/.config/morpheus.properties

# Test with verbose
./run.sh hello --verbose

# List available connections
./run.sh list --morphiumcfg=?

# List available themes
./run.sh list --theme=?

# Check terminal size detection
./run.sh hello --verbose | grep Terminal
```

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

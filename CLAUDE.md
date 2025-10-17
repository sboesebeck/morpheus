# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Morpheus is a command-line toolbox and messaging monitor for Morphium (a sophisticated MongoDB abstraction layer). It provides CLI commands for monitoring and interacting with Morphium's messaging system and MongoDB connections.

Main entry point: `de.caluga.morpheus.Morpheus`

## Building and Running

### Build
```bash
mvn compile
```

### Build JAR with dependencies
```bash
mvn package
```
This creates `target/morpheus-1.0-SNAPSHOT-jar-with-dependencies.jar`

### Run with the helper script
```bash
./run.sh [options] <commandName> [args...]
```
The `run.sh` script intelligently compiles the project only when needed and runs it with optimal performance.

**Smart Compilation:**
- Automatically detects changes to source files or `pom.xml`
- Only recompiles when necessary (checks file modification timestamps)
- Caches dependency classpath in `cp.csv` for fast startup
- Typical cached startup time: ~0.3 seconds

**Options:**
- `--recompile` - Force recompilation even if no changes detected
- `--rerun` - Skip all compilation checks (fastest, use when you know nothing changed)

**Examples:**
```bash
./run.sh list                    # Smart compilation (auto-detects changes)
./run.sh --recompile config      # Force rebuild
./run.sh --rerun monitor         # Skip compilation entirely
```

### Run directly with Maven
```bash
mvn exec:java -Dexec.mainClass="de.caluga.morpheus.Morpheus" -Dexec.args="<commandName>"
```

### Code Quality
```bash
mvn pmd:pmd pmd:cpd-check
```

### Testing
```bash
mvn test
```

## Architecture

Morpheus follows a clean, layered architecture with separated concerns:

### Core Components

**1. ConfigurationManager** (`de.caluga.morpheus.config.ConfigurationManager`)
- Centralized configuration management
- Loads from `~/.config/morpheus.properties`
- Handles command-line argument parsing
- Provides type-safe configuration access
- Manages themes and connection selection

**2. ThemeManager** (`de.caluga.morpheus.rendering.ThemeManager`)
- ANSI color code management
- Theme loading and rendering
- Gradient text effects
- Column formatting utilities
- All visual output handling

**3. TerminalUtils** (`de.caluga.morpheus.utils.TerminalUtils`)
- Terminal size detection
- Cursor control
- Screen manipulation

**4. MorphiumConnectionFactory** (`de.caluga.morpheus.connection.MorphiumConnectionFactory`)
- Morphium instance creation
- Messaging configuration and initialization
- Connection health checks
- Separates MongoDB logic from main application

**5. Morpheus** (`de.caluga.morpheus.Morpheus`)
- Main entry point
- Command dispatch
- Lifecycle management
- Provides backward-compatible API for commands

### Command Pattern
Commands implement the `ICommand` interface:

```java
public interface ICommand {
    void execute(Morpheus morpheus, Map<String,String> args) throws Exception;
}
```

All commands must define:
- `public static final String NAME` - command name for CLI invocation
- `public static final String DESCRIPTION` - help text

**Commands that require MongoDB connection:**
Commands that need Morphium/Messaging should implement `IRequiresMorphium` instead of just `ICommand`:

```java
public interface IRequiresMorphium extends ICommand {
    // Marker interface
}
```

This ensures Morphium is initialized before the command executes. Commands that only manage configuration or display information (like `config`, `list`) should implement just `ICommand` - they can run without MongoDB connectivity.

### Command Registration
New commands must be:
1. Created in `src/main/java/de/caluga/morpheus/commands/`
2. Implement `ICommand` interface (or `IRequiresMorphium` if MongoDB is needed)
3. Define `NAME` and `DESCRIPTION` static fields
4. Registered in `Morpheus` static initializer block

Example:
```java
commands.put(YourCommand.NAME, YourCommand.class);
```

### Configuration System
Configuration is stored in `~/.config/morpheus.properties` and includes:
- **Themes**: Color schemes for terminal output (e.g., `theme.default.*`)
- **Morphium Connections**: MongoDB connection configs with prefix `morphium.<name>.*`
- **Messaging Settings**: Queue configuration, sender ID, threading options

**Managing Connections:**
Use the `config` command to manage MongoDB connections interactively:
- `./run.sh config connection add <name>` - Add new connection with multi-host support
- `./run.sh config connection edit <name>` - Edit existing connection (all fields including messaging)
- `./run.sh config connection list` - List all configured connections
- `./run.sh config connection show <name>` - Show detailed connection info
- `./run.sh config connection remove <name>` - Remove a connection

**Multi-Host Support:**
Connections support MongoDB replica sets with multiple hosts:
- Format: `host1:port1,host2:port2,host3:port3`
- Example: `mongo1.example.com:27017,mongo2.example.com:27017`
- Stored as `hostSeed` property

Command-line arguments:
- `--theme=<name>` or `--theme=?` to list themes
- `--morphiumcfg=<name>` or `--morphiumcfg=?` to list/choose connections
- `--messaging=single|multi` to select messaging implementation (overrides config)
- Arguments format: `key=value`

### ANSI Color System
Morpheus has a sophisticated color rendering system:
- `pr(String)` - prints with ANSI code replacement (e.g., `[rd]` → red, `[gr]` → green)
- `pr(String, Gradient)` - prints with gradient coloring
- `pr(String, int)` - prints with theme-based gradient
- Supports 256-color palette via `[fg###]` and `[bg###]` tags
- Theme colors are mapped to shorthand codes in `registerAnsiCodes()`

### Core Components

**Morpheus.java** (line 28-619)
- Main application class
- Manages Morphium connection and Messaging instance
- Provides ANSI color utilities and terminal manipulation
- Command dispatch and lifecycle management
- Configuration loading from properties file

**Available Commands**:
- `hello` - HelloCommand: Basic test/example command
- `list` - ListCommands: Shows all available commands
- `get_status` - GetStatus: Queries status from all connected Morphium nodes with extensive filtering and Graphite export
- `monitor` - MessageMonitor: Real-time messaging monitor using MongoDB change streams
- `send` - SendMessageCommand: Sends messages through Morphium messaging system

### Morphium Integration
The application initializes:
1. MorphiumConfig from properties (connection details, auth)
2. Morphium instance with PooledDriver
3. Messaging layer with configurable threading, queue name, and processing options
4. Connection health checks before command execution

Messaging configuration keys (per connection):
- `messaging.implementation` - messaging implementation type: `single` (SingleCollectionMessaging) or `multi` (MultiCollectionMessaging), default: `single`
- `messaging.processMultiple` - process multiple messages, default: `true`
- `messaging.multiThreadded` - enable multithreading, default: `true`
- `messaging.pause` - polling interval (ms), default: `100`
- `messaging.windowSize` - batch size, default: `10`
- `messaging.queueName` - queue collection name, default: `msg`
- `messaging.senderId` - unique sender ID, default: auto-generated UUID

### Key Implementation Details

**GetStatus Command** (src/main/java/de/caluga/morpheus/commands/GetStatus.java):
- Sends status ping to all connected nodes via messaging
- Supports filtering by host, sender, path patterns
- Can export metrics to Graphite (Plaintext protocol)
- Levels: PING (basic), MESSAGING_ONLY, MORPHIUM_ONLY, ALL
- Collects and displays hierarchical status data with pattern filtering

**MessageMonitor Command** (src/main/java/de/caluga/morpheus/commands/MessageMonitor.java):
- TOP-like real-time message monitoring with in-place updates
- Uses MongoDB Change Streams to watch Msg collection
- **V5/V6 Compatibility**: Reads both `name` (V5) and `topic` (V6) fields from documents
- Fixed screen buffer showing last N **request messages** (answers update requests in-place)
- Messages update in place when:
  - Answer arrives (RTT displayed, timeout cleared)
  - ProcessedBy is added
- Live statistics: uptime, message rate, answers, updates, timeouts, buffer usage
- **Topic RTT Statistics**: Shows average response time per topic (top 5 slowest)
- **SLA Monitoring**: Highlights unanswered requests after threshold in RED
- Display Philosophy:
  - **Only shows request messages**, not answers (saves buffer space)
  - Answers update the original request in-place with RTT
  - Focus on what matters: request status and response time
  - More requests visible = better monitoring
- Features:
  - Throttled redraws (500ms) to prevent flickering
  - **Red highlighting** for requests without answer after timeout (default: 2000ms)
  - Yellow highlighting for updated messages (processedBy added)
  - Green "YES" indicator when request is answered
  - Shows RTT (Round Trip Time) for answered requests
  - Tracks and displays average RTT per message topic
  - Timeout counter in statistics line (red)
  - Alternating row colors for readability
  - Handles insert/update/replace change stream events
- Configuration:
  - `--timeout=<ms>` - Set timeout threshold (default: 2000ms)
  - `--verbose` - Show debug information and RTT details
- **Dynamic Terminal Resize**: Automatically adapts to terminal size changes
  - Registers SIGWINCH signal handler
  - Recalculates column widths on resize
  - Adjusts message buffer size based on terminal height
- Similar UX to `top` or `vmstat` but for Morphium messages

### Dependencies
- Java 21
- **Morphium 6.0.0** (MongoDB abstraction layer) - upgraded from 5.1.25
- Spring Core 6.1.5
- MongoDB Driver 4.7.1
- Logback 1.4.0
- JUnit 5.9.0 (testing)

### Morphium V6 Migration Notes
The project has been successfully migrated from Morphium 5.1.25 to 6.0.0. Key changes:

1. **Messaging API Changes**:
   - `Messaging` class replaced with `MorphiumMessaging` interface
   - Two implementations available:
     - `SingleCollectionMessaging` - single collection for all messages (default)
     - `MultiCollectionMessaging` - separate collections per message type
   - **New initialization pattern**: Configure via `MorphiumConfig.messagingSettings()` and create with `morphium.createMessaging()`
   - Old: `new Messaging(morphium, pause, processMultiple, multithreadded, windowSize)`
   - New: Configure settings on config, then `morphium.createMessaging()`

2. **MorphiumConfig Changes**:
   - `IndexCheck` enum removed - automatic index checking no longer configurable
   - `CappedCheck` enum removed - automatic capped collection checking no longer configurable
   - Add `messagingSettings()` for configuring messaging before Morphium creation

3. **Msg API Changes**:
   - `getName()` method renamed to `getTopic()`
   - Use `msg.getTopic()` instead of `msg.getName()`

4. **Messaging Implementation Selection**:
   - Configure in properties: `morphium.<connection>.messaging.implementation=single|multi`
   - Override via command-line: `--messaging=single|multi`
   - Application detects and uses the configured implementation automatically

## TODOs

### ~~Morphium Version Upgrade~~ ✅ COMPLETED
- [x] Upgrade from Morphium 5.1.25 to V6.0.0
  - Updated pom.xml dependency version
  - Identified and fixed breaking changes
  - Updated MorphiumConfig initialization (removed IndexCheck/CappedCheck)
  - Updated Messaging to MorphiumMessaging/SingleCollectionMessaging
  - Fixed Msg.getName() → Msg.getTopic()
  - Tested compilation and tests successfully

### ~~Code Structure Overhaul~~ ✅ COMPLETED
- [x] Refactor project structure for better organization
  - ✅ Created `ThemeManager` for ANSI/color utilities
  - ✅ Created `ConfigurationManager` for configuration management
  - ✅ Created `TerminalUtils` for terminal operations
  - ✅ Created `MorphiumConnectionFactory` for Morphium/Messaging initialization
  - ✅ Refactored `Morpheus.java` to use clean architecture
  - ✅ Added `--verbose` flag for detailed output
  - ✅ Improved startup flow and error handling
  - ✅ Maintained backward compatibility with existing commands

### Interactive Views
- [ ] Implement interactive TOP-like views for monitoring
  - Create interactive dashboard command similar to Unix `top`
  - Real-time refresh of messaging statistics (messages/sec, queue depth, active listeners)
  - Show active connections, nodes, and their status
  - Support keyboard controls (sort by different columns, filter, pause/resume)
  - Display CPU/memory metrics if available from status data
  - Add color-coded alerts for thresholds (queue depth, response times)
  - Consider using libraries like Lanterna or JLine for terminal UI

### Interactive Message Sender
- [ ] Create interactive form-based message sending interface
  - Build TUI (Text User Interface) form for composing messages
  - Fields: recipient selection (with autocomplete from known senders)
  - Message name/type dropdown or autocomplete
  - Message body editor (multi-line text input)
  - Support for message priority, TTL settings
  - Preview message before sending
  - Show send confirmation and track response
  - History of sent messages with ability to resend

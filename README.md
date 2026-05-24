# Morpheus

**Morphium Toolbox and Messaging Monitor**

## What is it about?

The Project [morphium](https://github.com/sboesebeck/morphium) is a very sophisticated and feature-rich abstraction layer for MongoDB. It offers a lot of features, like declarative caching, automatic Object-Mapping and an integrated highly sophisticated Message Queuing System.

Morpheus fills in the gap by providing command-line tools for:
- 📊 Real-time message monitoring
- 🔍 Status checking across distributed nodes
- 💬 Message sending and testing
- 🎨 Customizable themes and output
- ⚙️ Multiple connection management

## Quick Start

```bash
# Build the project
mvn clean package

# First run creates config file
./run.sh list

# Add your MongoDB connection interactively
./run.sh config connection add myconn

# Test your setup
./run.sh hello --morphiumcfg=myconn --verbose

# Monitor messages in real-time
./run.sh monitor --morphiumcfg=myconn
```

## Features

- ✅ **Interactive Configuration**: Easy-to-use `config` command for managing connections and settings
- ✅ **SOCKS Proxy Support**: Connect through SSH tunnels and SOCKS proxies
- ✅ **Multiple Messaging Implementations**: Choose between SingleCollection or MultiCollection messaging
- ✅ **Real-time Monitoring**: Watch messages as they flow through your system
- ✅ **Distributed Status**: Get status from all connected Morphium nodes
- ✅ **Flexible Configuration**: Multiple connections, themes, and settings
- ✅ **Clean Architecture**: Refactored with ConfigurationManager, ThemeManager, and separated concerns
- ✅ **Verbose Mode**: Detailed output for debugging and monitoring

## Documentation

- **[USAGE.md](USAGE.md)** - Complete usage guide with examples
- **[CONFIG_MANAGEMENT.md](CONFIG_MANAGEMENT.md)** - Configuration management guide (connections, proxy, themes)
- **[CHEATSHEET.md](CHEATSHEET.md)** - Quick reference for common commands
- **[CLAUDE.md](CLAUDE.md)** - Architecture and development guide

## Requirements

- Java 21
- Maven 3.x
- MongoDB 5.0+ (for Morphium V6)
- Access to a Morphium-enabled MongoDB instance

## Architecture

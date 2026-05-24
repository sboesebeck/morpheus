# Configuration Management Guide

## Overview

Morpheus provides an interactive configuration management tool that makes it easy to manage connections, themes, and proxy settings without manually editing configuration files.

## The `config` Command

```bash
./run.sh config [subcommand] [action] [name]
```

### Quick Help

```bash
./run.sh config help
```

## Managing Connections

### Add a New Connection

```bash
./run.sh config connection add production
```

This launches an interactive wizard that prompts for:
- MongoDB Host (default: localhost)
- MongoDB Port (default: 27017)
- Database Name
- Authentication Database (default: admin)
- Username
- Password (hidden input)
- Messaging Implementation (single/multi, default: single)
- Queue Name (default: msg)

### List All Connections

```bash
./run.sh config connection list
```

Shows all configured connections with their host and database information.

### Show Connection Details

```bash
./run.sh config connection show production
```

Displays all configuration properties for a specific connection (passwords are hidden).

### Edit a Connection

```bash
./run.sh config connection edit production
```

Interactive editing - press ENTER to keep current values.

### Remove a Connection

```bash
./run.sh config connection remove production
```

Prompts for confirmation before removing.

## SOCKS Proxy Management

Morpheus supports SOCKS proxy for secure MongoDB connections through SSH tunnels or other proxy services.

### Configure Proxy for a Connection

```bash
./run.sh config proxy config default_connection
```

Interactive prompts:
- SOCKS Proxy Host (default: 127.0.0.1)
- SOCKS Proxy Port (default: 5555)
- Enable proxy? (yes/no, default: yes)

### Enable Proxy

```bash
./run.sh config proxy enable production
```

### Disable Proxy

```bash
./run.sh config proxy disable production
```

### Manual Configuration

You can also manually edit `~/.config/morpheus.properties`:

```properties
# Enable SOCKS proxy for a connection
morphium.production.proxy.enabled=true
morphium.production.proxy.host=127.0.0.1
morphium.production.proxy.port=5555
```

### SSH Tunnel Example

```bash
# Create SSH tunnel (in separate terminal)
ssh -D 5555 -f -C -q -N user@bastion-host

# Configure Morpheus to use the proxy
./run.sh config proxy config production
# Host: 127.0.0.1
# Port: 5555
# Enable: yes

# Connect through the tunnel
./run.sh get_status --morphiumcfg=production
```

## Theme Management

### List Available Themes

```bash
./run.sh config theme list
```

### Creating Custom Themes

Currently, themes must be added manually to `~/.config/morpheus.properties`:

```properties
# Custom theme example
theme.mytheme.c1=[fg196]
theme.mytheme.c2=[fg33]
theme.mytheme.c3=[fg226]
theme.mytheme.header1=[bld][fg46]
theme.mytheme.header2=[ital]
theme.mytheme.error=[rd]
theme.mytheme.warning=[y]
theme.mytheme.good=[gr]
theme.mytheme.gradient1=blue
theme.mytheme.gradient2=cyan
theme.mytheme.gradient3=purple
```

Then use it:
```bash
./run.sh list --theme=mytheme
```

## General Configuration

### Show All Configuration

```bash
./run.sh config show
```

Displays all configuration properties (passwords hidden).

### Validate Configuration

```bash
./run.sh config validate
```

Checks:
- Connection configurations are valid
- Required properties exist
- Shows warnings for missing or invalid settings

## Configuration File

Location: `~/.config/morpheus.properties`

### Structure

```properties
# Theme settings
theme.default.c1=[rd]
theme.default.c2=[bg1][bld][fg230]
theme.default.good=[gr]
theme.default.error=[rd]
theme.default.warning=[y]
theme.default.gradient1=grey

# Connection settings
morphium.production.hosts.0=mongodb.example.com:27017
morphium.production.database=prod_db
morphium.production.authDb=admin
morphium.production.login=produser
morphium.production.password=prodpass

# Messaging settings
morphium.production.messaging.implementation=single
morphium.production.messaging.processMultiple=true
morphium.production.messaging.multithreadded=true
morphium.production.messaging.windowSize=10
morphium.production.messaging.pause=100
morphium.production.messaging.queueName=msg
morphium.production.messaging.senderId=unique-id-here

# Proxy settings
morphium.production.proxy.enabled=true
morphium.production.proxy.host=127.0.0.1
morphium.production.proxy.port=5555
```

## Examples

### Setup a New Production Connection

```bash
# Add connection
./run.sh config connection add production

# Configure proxy (if needed)
./run.sh config proxy config production

# Validate
./run.sh config validate

# Test connection
./run.sh hello --morphiumcfg=production --verbose
```

### Multiple Environments

```bash
# Development
./run.sh config connection add dev

# Staging
./run.sh config connection add staging

# Production with proxy
./run.sh config connection add prod
./run.sh config proxy enable prod

# Use them
./run.sh monitor --morphiumcfg=dev
./run.sh get_status --morphiumcfg=staging
./run.sh get_status --morphiumcfg=prod
```

### Backup and Restore

```bash
# Backup
cp ~/.config/morpheus.properties ~/morpheus-backup.properties

# Restore
cp ~/morpheus-backup.properties ~/.config/morpheus.properties

# Validate after restore
./run.sh config validate
```

## Security Considerations

1. **Passwords**: Stored in plain text in configuration file
   - Ensure proper file permissions: `chmod 600 ~/.config/morpheus.properties`
   - Consider using environment variables for sensitive values

2. **SOCKS Proxy**:
   - Proxy settings apply at JVM level
   - All MongoDB connections use the configured proxy
   - Ensure SSH tunnel is active before connecting

3. **Configuration File**:
   - Located in user's home directory
   - Not shared across users
   - Backup regularly

## Troubleshooting

### Connection Issues with Proxy

```bash
# Check proxy settings
./run.sh config connection show production | grep proxy

# Disable proxy temporarily
./run.sh config proxy disable production

# Test without proxy
./run.sh hello --morphiumcfg=production --verbose

# Re-enable if needed
./run.sh config proxy enable production
```

### Validate After Changes

```bash
./run.sh config validate
```

### View Full Configuration

```bash
./run.sh config show
```

Or directly:
```bash
cat ~/.config/morpheus.properties
```

## Advanced Tips

1. **Use Descriptive Names**: `production`, `staging`, `local` instead of `conn1`, `conn2`

2. **Document Custom Settings**: Add comments in the properties file

3. **Version Control**: Keep a template configuration in version control (without passwords)

4. **Multiple Proxies**: Different connections can have different proxy settings

5. **Messaging Tuning**: Adjust `windowSize` and `pause` based on load

## Next Steps

- Set up your first connection with `./run.sh config connection add myconn`
- Configure SOCKS proxy if needed with `./run.sh config proxy config myconn`
- Validate with `./run.sh config validate`
- Test with `./run.sh hello --morphiumcfg=myconn --verbose`

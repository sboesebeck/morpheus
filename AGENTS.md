# Repository Guidelines

## Project Structure & Module Organization
Morpheus is a Java 21 CLI packaged with Maven. Core sources live in `src/main/java/de/caluga/morpheus`, commands sit in `src/main/java/de/caluga/morpheus/commands`, and shared resources (e.g. `logback.xml`) reside in `src/main/resources`. Keep build metadata in `pom.xml`, compiled output in `target/`, and helper scripts such as `run.sh` at the repository root.

## Build, Test, and Development Commands
- `mvn compile` — resolves dependencies and compiles sources to `target/classes`.
- `mvn test` — executes the JUnit 5 test suite; ensure it passes before publishing changes.
- `mvn package` — produces a jar-with-dependencies using the assembly plugin.
- `./run.sh [--rerun] <command>` — launches `de.caluga.morpheus.Morpheus` with the assembled classpath; use `--rerun` to skip dependency scanning on subsequent runs.

## Coding Style & Naming Conventions
Stick to Java defaults: 4-space indentation, `UpperCamelCase` types, `lowerCamelCase` members, `UPPER_SNAKE_CASE` constants. Keep shared helpers in `de.caluga.morpheus`, add CLI actions in `de.caluga.morpheus.commands` with a `Command` suffix, and run `mvn pmd:pmd cpd-check` before submitting.

## Testing Guidelines
JUnit 5 ships via `junit-jupiter-params`. Place tests in `src/test/java`, mirroring the main package layout (e.g. `de/caluga/morpheus/commands/HelloCommandTest`). Name classes `*Test`, lean on parameterized cases for varied inputs, and assert both success and failure paths for each command.

## Commit & Pull Request Guidelines
Use short, lowercase, imperative commit summaries (e.g. `add message monitor`, `improve status command`) and group related edits. Before opening a pull request, run `mvn test` and lint checks, document the intent, list validation steps, and attach screenshots or terminal excerpts when CLI output changes. Call out configuration updates so reviewers can reproduce your setup.

## Configuration Tips
Runtime configuration defaults to `~/.config/morpheus.properties`. Override connections or themes with flags like `--morphiumcfg=staging` or `--theme=cyan`. If your proxy differs from the defaults in `run.sh` (`127.0.0.1:5555`), document the change in your pull request.

## Roadmap & TODOs
- TODO: Upgrade Morphium dependency to `6.0.2` and reconcile any API updates across `de.caluga.morpheus` commands.
- TODO: Restructure project layout to clarify command modules, shared utilities, and UI assets; consider separating CLI and service logic.
- TODO: Introduce interactive monitoring views akin to Unix `top` for live Morphium messaging metrics.
- TODO: Add an interactive form-based command for composing and sending messages with validation before dispatch.

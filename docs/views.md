# Morpheus Views

Running `morpheus` with no command opens the **launcher**: pick a connection
(left) and a view (right), `Tab` switches columns, `Enter` opens the view. Inside
any view, `Esc` goes back and `q` quits.

Every view is fed by the framework-free `core/` layer, so the CLI and the TUI
agree on what each number means.

### Launcher — connections

The connections column also manages connections: `a` add, `e` edit, `d` delete,
`t` test (sends one status ping). One connection can be the **default** (used
when you don't pass `-c`): it is shown with a trailing `*`, the cursor starts on
it, and `Space` toggles the default on the selected connection.

---

## `messages` — live message monitor

A `top`-like live feed of every message on the bus, newest first. The line above
the table shows uptime, throughput, and running totals
(messages / answers / updates / timeouts / buffer fill).

By default, the view keeps the newest **1,000 messages** in a rolling in-memory
history. Use `+/-` inside the Messages view to choose and persist a preset between
100 and 10,000 messages. The running buffer is resized immediately; shrinking it
evicts the oldest snapshots first. A snapshot remains available after its MongoDB
document has been deleted, including up to 2,048 characters of message/value/map
payload captured on insert.

| Column | Meaning |
|---|---|
| `#` | row number (newest is 1) |
| `Time` | age of the message in seconds |
| `Sender` | id of the originating node; the V5/V6 protocol is colour-tagged |
| `Host` | host of the sender |
| `Topic` | message name / topic |
| `Size` | approximate message size |
| `Proc` | processing / lock / answer state (e.g. `N proc`, or the lock holder) |
| `Ex` | `X` when the message is **exclusive** (processed by exactly one node) |
| `An` | `Y` when the message has been **answered** |
| `AnswerBy` | id of the answering node (protocol-tagged) |
| `AnsHost` | host of the answering node |
| `RTT` | request → answer round-trip time, in ms |

Rows that **timed out** (unanswered past the threshold) are shown in **red**;
the rest alternate shading for readability. The selected row is green. Selection
is anchored to the message id, so incoming messages do not move the cursor away
from an older message being inspected. Moving down or paging automatically pauses
the visible snapshot, while new messages continue entering the bounded rolling
buffer in the background. The footer shows how many arrived during the pause.

**Keys:** `↑/↓` select a message, `PgUp/PgDn` move by one visible page, `Enter`
opens the message detail snapshot, `+/-` resize the rolling history, `Esc` goes
back, `q` quits. `p` toggles pause/live and `End` immediately resumes live mode at
the newest message. The existing filter keys remain available as shown in the footer.

---

## `topics` — per-topic aggregates

One row per topic, with running counts — the bird's-eye view of which topics are
busy and healthy.

| Column | Meaning |
|---|---|
| `Topic` | the topic / message name |
| `Nachr.` | messages seen on this topic |
| `Antw.` | answers seen |
| `Ø-RTT` | average round-trip time (ms) |
| `Timeout` | requests that crossed the timeout threshold unanswered |

A **late answer takes its earlier timeout back**, so the `Timeout` count reflects
genuinely unanswered requests rather than slow ones.

**Keys:** `Esc` back, `q` quit.

---

## `nodes` — sender → answerer pairs

The distribution of *who answers whom*. Each row is a directed pair, which makes
load imbalance (one node answering most requests) jump out.

| Column | Meaning |
|---|---|
| `Sender → Antwortender` | the directed sender → answerer pair |
| `Anzahl` | number of correlated request/answer exchanges |
| `Ø-RTT` | average RTT for that pair (ms) |

**Keys:** `v` toggles keying by **host** vs **sender id**; `Esc` back, `q` quit.

---

## `status` — per-node health (top-like)

A live `top`-style table of every node that answered the status ping. The header
shows the current ping timeout.

| Column | Meaning |
|---|---|
| `Sender@Host` | node identity |
| `RTT` | ping round-trip (ms) |
| `Mem(u/max)` | JVM heap used / max |
| `Hit%` | Morphium cache hit ratio |
| `Conn(u/p)` | driver connections in use / in pool |
| `Err` | driver error count |
| `Msg-sent` | messages sent by the node |
| `Thr` | active threads |

A `–` means the node did not report that metric.

**Keys:** `↑/↓` select a node, `Enter` opens the **detail** drill-down (the full
status dump — JVM, cache, driver, messaging, replica-set, config — scroll with
`↑/↓`), `+`/`-` change the ping timeout (presets 2 / 5 / 10 / 15 / 30 s), `r`
re-ping, `Esc` back, `q` quit.

---

## `graph` — message-flow graph

The nodes sit on a ring; every message is a short, **topic-coloured "shot"**
travelling between them, drawn on a Braille sub-pixel canvas so it animates
smoothly inside a normal terminal.

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

What you are looking at:

- **Nodes on a ring** — discovered live from traffic. Unset sender ids (long
  UUIDs) are shortened to `head…tail`. Nodes that *send* a lot drift toward the
  centre over time (activity-weighted), so the busiest hubs end up visually
  central.
- **Shots** — one message in flight, coloured by topic. The **`Topics:`** legend
  at the bottom maps colours to topics as they appear.
- **Request → answer** — when an answer is observed, the request and its answer
  animate **sequentially** (out, then back), so you read a real exchange instead
  of only the reply.
- **Broadcasts** fan out to every listener of the topic.
- **Red shots** — a request that **timed out** (no answer within the threshold).

**Keys:**

- `p` — pause / resume the animation
- `h` — toggle a second line per node showing its **hostname**
- `g` — **grafik** (opt-in): on **Kitty / Ghostty / WezTerm**, render the graph
  as a real anti-aliased raster image via the Kitty graphics protocol. Braille
  stays the default and the universal fallback; on terminals without graphics
  support, `g` shows a short hint and the view stays on Braille.
- `Esc` back, `q` quit

> The Kitty-graphics mode is eye-candy, not a different data source — it draws the
> exact same scene (same ring, same shots, same colours) as a smooth image where
> the terminal supports it.

---

## `latency` — RTT graph / load test

Live round-trip-time view for the message bus: a Braille time series (or
histogram, toggle with `h`) of every answered ping, plus a per-responder
percentile table (`cnt`, `loss%`, `avg`, `p50/p90/p99`, jitter and the
approximate `out≈`/`ret≈` split). Point it at a `pong` responder, an
application topic, or the built-in status listener (`--status`). Red `×`
marks are timeouts; white dotted vertical lines mark ramp steps.

The bottom line always shows the **current** payload/thread settings, e.g.
`p/P payload:10k  t/T threads:4`.

**Keys:**

- `s` — start / stop the probe
- `m` — cycle send mode: interval → fixed-rate → ramp
- `x` — toggle exclusive pings: measures the request/reply profile including
  the responder-side lock/claim traffic instead of the broadcast path
  (no-op in `--status` mode; `excl` shows in the header when active)
- `+` / `-` — adjust the current mode's knob (interval ms / rate msgs/s / ramp step)
- `p` / `P` — smaller / bigger ping payload (0, 1k, 10k, 100k)
- `t` / `T` — fewer / more sender threads (1, 2, 4, 8)
- `h` — toggle histogram vs. time series
- `e` — export the raw sample series as CSV into the working directory
  (`latency-samples-<timestamp>.csv`, one row per answered ping) — the file
  name is confirmed in the bottom line
- `Esc` back, `q` quit

Changing mode, exclusive, payload or threads restarts a running probe;
collected stats are kept.

## `uptime` — Uptime/failover monitor over pong responders

Timeline, availability statistics, and event log tracking node state changes,
replica-set primary failovers, and send errors. Reuses latency-probe pings to
detect outages; `w` writes three CSVs (events, nodes, samples).

| Column (Table) | Meaning |
|---|---|
| `Node` | pong responder name |
| `Status` | UP or DOWN |
| `seit` | time since current state |
| `avail%` | availability percentage since first ping |
| `Ausfälle` | number of outages recorded |
| `längster` | duration of longest outage |
| `⌀Ausfall` | average outage duration |
| `lastRTT` | last round-trip time to responder (ms) |

**Keys:** `s` start/stop probe, `+`/`-` adjust ping interval, `n` cycle miss
threshold (1/2/3/5/10), `r` toggle replica-set watcher, `w` write CSVs, `↑/↓`
scroll events, `c` reset, `Esc` back, `q` quit.

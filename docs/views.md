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

| Column | Meaning |
|---|---|
| `#` | row number (newest is 1) |
| `Time` | message timestamp (`HH:MM:SS`) |
| `Sender` | id of the originating node; the V5/V6 protocol is colour-tagged |
| `Host` | host of the sender |
| `Topic` | message name / topic |
| `Size` | serialized message size |
| `Proc` | processing / lock / answer state (e.g. `N proc`, or the lock holder) |
| `Ex` | `X` when the message is **exclusive** (processed by exactly one node) |
| `An` | `Y` when the message has been **answered** |
| `AnswerBy` | id of the answering node (protocol-tagged) |
| `AnsHost` | host of the answering node |
| `RTT` | request → answer round-trip time, in ms |

Rows that **timed out** (unanswered past the threshold) are shown in **red**;
the rest alternate shading for readability.

**Keys:** `Esc` back, `q` quit.

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

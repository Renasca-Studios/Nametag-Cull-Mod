All four require permission level 2 (gamemaster), the same level as `/gamemode`.

- **`/culltag enable`** turns culling on and writes `enabled=true` to the config file. Hidden
  nametags start disappearing on the next sweep, within `check_interval_ticks`.
- **`/culltag disable`** turns culling off, writes `enabled=false`, and restores every
  nametag straight away. It does not just clear what the server is tracking: it sends a
  restore for every viewer and target pair, so a client left holding an override by an older
  jar is corrected too. The message reports how many of each kind of override came off.
- **`/culltag reload`** re-reads `config/culltag.properties` and reports the settings it ended
  up with. If the reload turns `enabled` off, it restores everything exactly as `disable`
  does.
- **`/culltag stats`** reports sweep and raycast counts, the last and average sweep time in
  milliseconds, and how many nametags are currently hidden by each mechanism. It is sent only
  to whoever ran it.

Reading `/culltag stats`:

- **Sweeps** and **raycasts** both climb while the mod is working. Sweeps that climb with
  raycasts stuck at zero means every pair is out of range or in a different dimension.
- **Average sweep** is over the last 100 sweeps. On a normal server it stays well under a
  millisecond; a sweep is only run every `check_interval_ticks`, so the tick budget it eats is
  that number divided by the interval.
- **Hidden by line of sight** and **hidden by crouch** are live counts of overrides currently
  applied, summed across every viewer. A pair blocked in both directions counts twice, once
  per viewer.

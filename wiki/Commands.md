All four require permission level 2 (gamemaster), the same level as `/gamemode`.

- **`/culltag enable`** turns culling on and writes `enabled=true` to the config file. Hidden
  nametags start disappearing on the next sweep, within `check_interval_ticks`.
- **`/culltag disable`** turns culling off, writes `enabled=false`, and restores every nametag
  straight away. It does not just clear what the server is tracking: it restores everything in
  its own records and then blasts every player pair regardless, so a client left holding an
  override by an older jar is corrected too. The message reports how many packets that took.
- **`/culltag reload`** re-reads `config/culltag.properties` and reports the settings it ended
  up with. If the reload turns `enabled` off, it restores everything exactly as `disable` does.
- **`/culltag stats`** reports sweep and ray counts, the last and average sweep time in
  milliseconds, and how many nametags are currently hidden. It is sent only to whoever ran it.

Reading `/culltag stats`:

- **Sweeps** and **rays** both climb while the mod is working. Sweeps that climb with rays
  stuck at zero means nothing is in range, in the same dimension, or visible in the first
  place.
- **Average sweep** is over the last 100 sweeps. On a normal server it stays well under a
  millisecond; a sweep only runs every `check_interval_ticks`, so the tick budget it eats is
  that number divided by the interval.
- **Nametags hidden** is a live count of overrides currently applied, summed across every
  viewer. A pair blocked in both directions counts twice, once per viewer.

# Open Myau

![Preview](/images/image2.png)

[Myau Client](https://myau.sell.app/), for those who can’t afford it.

Based on **`Myau-260926`**, with added features and improvements not found in the original, focused on expanding and refining the original Myau client.

[![Discord](https://invidget.switchblade.xyz/yjSaTufmqU)](https://discord.gg/yjSaTufmqU)

## Changes

### Upstream sync — `250910` → `260926`

Ported the upstream feature deltas released between those builds.

**Rewritten modules**

* **KillAura** — millisecond-based auto-block timing, new `auto-block-no-slow`,
  `auto-block-hold`, `auto-block-delay` and `auto-block-hurt-time` options, snapshotted
  target selection with `DISTANCE` / `HEALTH` / `HURT_TIME` / `FOV` sorting, and a reworked
  rotation and release/swap teardown. Auto-block CPS clamping was removed upstream.
* **Scaffold** — placements are now re-verified with a confirming trace before being sent,
  rotations snap to the mouse-sensitivity grid, and the telly takeoff, tower motion and
  keep-y recovery paths were reworked.
* **BedNuker** — `ground-spoof` now uses logical OR instead of AND, the render colour
  channels were un-swapped, tool selection uses `findBestToolForBlock`, and packet
  buffering moved onto `DelayManager`.
* **AutoClicker** — the block-hit cycle is now driven by a hold duration, a release delay
  and a hurt-time gate instead of a fixed tick count.

**New modules**

* **Timer** — `VANILLA` and `HYPIXEL` modes.

**Updated modules**

* **AimAssist** — added `require-press` and `allow-mining`, prefers the KillAura target,
  and prioritises enemies.
* **Eagle** — added `offset` and `align`.
* **SpeedMine** — added `chance` and reworked around digging packets.
* **WTap** — added `chance`.
* **HitSelect** — added `shouldDelayAttack`, used by KillAura.
* **NoSlow** — integrates with KillAura's `auto-block-no-slow`.

### OpenMyau additions

* Added Click GUI (ported from Raven B3 by blowsy)
* Fixed bugs

> **Note:** two upstream settings were removed by this sync and will be dropped from
> existing configs — `auto-block-min-aps` / `auto-block-max-aps` (KillAura),
> `block-hit-ticks` (AutoClicker, replaced by `block-hit-hold` / `block-hit-delay` /
> `block-hit-hurt-time`), `jump-check` / `sneak-only` (Eagle), and
> `no-keep-y-on-jump-potion` (Scaffold). The KillAura `auto-block` mode list was also
> reordered and now defaults to `LEGIT`; saved configs are unaffected because modes are
> stored by name.

If you encounter an issue or have a feature suggestion, please [create an issue](https://github.com/60124808866/OpenMyau/issues).

## Building

```bash
./gradlew build
```

## Contributing

You can open an issue or submit a pull request to help improve Open Myau.

If you’re interested in co-developing or have questions, feel free to reach out:

* Discord: `60124808866_88040`

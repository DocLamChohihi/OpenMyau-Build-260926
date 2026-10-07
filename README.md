# Open Myau

[Myau Client](https://myau.sell.app/), for those who can’t afford it.

Based on **`Myau-260926`**, with added features and improvements not found in the original, focused on expanding and refining the original Myau client.

[![Discord](https://invidget.switchblade.xyz/yjSaTufmqU)](https://discord.gg/zv2AwJT93s)

## Changes

### Upstream sync `250910` → `260926`

Ported the feature deltas released upstream between these builds.

**KillAura**
```
[+] auto-block-no-slow, auto-block-hold, auto-block-delay, auto-block-hurt-time
[+] Sort modes: DISTANCE, HEALTH, HURT_TIME, FOV
[+] Millisecond-based auto-block timing
[.] auto-block now defaults to LEGIT, mode list reordered
[.] auto-block-range 6.0 → 4.0, swing-range 3.5 → 4.0
[.] auto-block-require-press now defaults to true
[.] Reworked rotation and release/swap teardown
[-] auto-block-min-aps, auto-block-max-aps
```

**Scaffold**
```
[+] Placement re-verification via confirming trace
[+] Rotation snapping to the mouse-sensitivity grid
[.] Reworked telly takeoff, tower motion and keep-y recovery
[.] Initial pitch now randomised between 65 and 85 degrees
[-] no-keep-y-on-jump-potion
```

**BedNuker**
```
[+] findBestToolForBlock for tool selection
[+] 500ms bed scan cooldown
[.] ground-spoof now uses OR instead of AND (bug fix)
[.] Render colour channels un-swapped (bug fix)
[.] Bed whitelist is now a single bed
[.] Packet buffering moved onto DelayManager
```

**AutoClicker**
```
[+] block-hit-hold, block-hit-delay, block-hit-hurt-time
[.] Block-hit cycle driven by hold/delay/hurt-time instead of a fixed tick count
[-] block-hit-ticks
[-] range, hit-box-horizontal, hit-box-vertical
```

**Timer** *(new module)*
```
[+] Timer with VANILLA and HYPIXEL modes
```

**Other modules**
```
[+] AimAssist: require-press, allow-mining
[+] Eagle: offset, align
[+] SpeedMine: chance
[+] WTap: chance
[+] HitSelect: shouldDelayAttack, used by KillAura
[.] AimAssist: prefers the KillAura target, prioritises enemies
[.] SpeedMine: delay range 0-4 → 0-5, reworked around digging packets
[.] NoSlow: integrates with KillAura's auto-block-no-slow
[-] Eagle: jump-check, sneak-only
```

### OpenMyau additions

```
[+] Click GUI (ported from Raven B3 by blowsy)
[+] Fixed bugs
```

> **Config note:** settings marked `[-]` are dropped from existing configs on first load.
> The KillAura `auto-block` mode list was also reordered — saved configs are unaffected,
> because modes are stored by name rather than by index.

If you encounter an issue or have a feature suggestion, please [create an issue](https://github.com/MekongRE/OpenMyau/issues).

## Building

```bash
./gradlew build
```

## Contributing

You can open an issue or submit a pull request to help improve Open Myau.

If you’re interested in co-developing or have questions, feel free to reach out:

* Discord: `DocLamChohihi`

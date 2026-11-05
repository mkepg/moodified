# Screenshots

The README's screenshot gallery is wired to files in this folder. Drop the PNGs in with the exact filenames below and they'll render.

**Target device:** any phone the app targets (Android 8.0+). Portrait, 1080×2400 or similar. Dark or light system theme is fine — Moodified renders the same either way.

| File | Frame to capture |
| --- | --- |
| `01-check-in.png` | Check-in home screen with at least one mood logged today, the recent-days row visible, and the "Today's care" card populated. |
| `02-quick-log.png` | The Quick-log bottom sheet on its second step, with an energy picked, a note typed, and the "When" row showing the entry time. |
| `03-insight-overview.png` | Insight screen on the Overview tab, showing Today's Mood inferred from behavior, its confidence, and at least one driver chip. |
| `04-insight-screen.png` | Insight screen on the Sleep tab, showing the "Last 7 nights" chart with one night logged by hand and the plain-language insight card below it. |
| `05-care.png` | Care screen with an active guidance card or a micro-intervention list. |
| `06-profile.png` | Profile screen showing the Tracking row + notifications row. |

**How to capture:** Android Studio's device toolbar → camera icon, or `adb exec-out screencap -p > 0X-name.png`. Trim to the app window (no status bar, no gesture bar). The current set is 1080×2124.

Once you drop the PNGs in, the README gallery links resolve automatically. No further wiring needed.

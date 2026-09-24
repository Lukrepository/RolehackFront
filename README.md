# RolehackFront

The touch interface for [Rolehack](https://github.com/Lukrepository/RolehackDroid), a NetHack 5.0 variant for Android. This is ForkFront, the Android front end by gurrhack as maintained by [JodiJodington](https://github.com/JodiJodington/ForkFront-Android), plus the Rolehack mobile interface.

- **`rolehack-ui`** — the Rolehack interface.
- **`master`** — upstream, unchanged.

It is an Android library, not an app. To play or build Rolehack, start at **[RolehackDroid](https://github.com/Lukrepository/RolehackDroid)**, which builds this library from a checkout beside it.

## The interface

It is laid out for two thumbs, and works held either way. Turn the phone mid-game and it rearranges without touching the game.

- **Landscape:** a bank of keys each side of the screen, and a deck under it.
- **Portrait:** the screen across the top, a row of keys under it, and the two banks side by side along the bottom. Each bank ends in a 3×3 pad: movement on the left, and on the right the actions you use most. The map gets far more height, which suits narrow levels like Sokoban.

- **The case.** The keys sit as keycaps in three wells: a bank on each side and a deck under the screen. The map is framed as a CRT. Only the screen passes taps to the map, so a tap that just misses a key never starts a travel command.
- **Caseless.** The map fills the whole screen. The wells go translucent but still catch near misses. Switch it with GAME → Case on/off, or Settings → Show the case.
- **Styles.** Terminal (dark), Terminal (light) and GameCube.
- **Fonts.** Screen: VT323, IBM Plex Mono, Share Tech Mono or Space Mono. Keys: IBM Plex Sans Condensed, Barlow Semi Condensed, Space Mono or the system font.
- **Status lines.** Full, compact (no attribute line) or hidden, which keeps only conditions such as Stoned, from GAME → Status lines or the settings. The map shows through them and answers a tap under them.
- **Where the settings are.** MENU → Settings → Mobile interface.

## Controls at a glance

Each key shows its tap on its face and its hold on its front edge. The small letter in the corner is the NetHack key it sends.

| Key | Tap | Hold |
|---|---|---|
| Numpad | Move | — |
| Numpad centre | Search a turn; Pick up when something is underfoot; your own square when asked for a direction | Context menu: attack, pick up, descend, look here, chat |
| REST | Rest the count shown | Choose the count. Swipe the key toward the screen's edge to bring in **Long rest**, the darker key (searches 100–400 turns, or any number), hidden there so it can't be tapped by accident |
| SACRIFICE | Offer | Pray |
| M1 · M2 · M3 | Run the macro, or set one up if the key is empty | Edit the macro |
| DROP | Drop one item | Fan: drop by type, from a menu, review first. Tap DROP again for everything |
| OFFENSE | Fight and Kick keys appear | All attack commands. **Flick** up to Fight, up-right to Kick |
| LOOK | Look here (`:`) | Far look (`;`) |
| Context key | Lights up when something applies: descend, ascend, sacrifice, loot, open or close a door. With two or more, tap to choose | — |
| INVENTORY | Inventory (`i`) | All gear commands |
| Wear · Put on · Wield / Take off · Remove · Swap | That command | Clear the key. On an empty key: pick what goes there |
| EAT QUAFF READ | Eat | Fan: eat, quaff, read. Tap again for everything |
| SEARCH | Search the count shown | Choose the count |
| INTERACT | Apply (`a`) | Fan: apply, sit, dip, engrave. Tap again for everything |
| MENU · WORLD · GAME · KEYS | Settings, the command drawers, the keyboard | — |

- **Counts.** Every count row ends in **×n**, which asks for any number up to NetHack's limit of 32767.
- **Closing things.** Any fan, radial or drawer closes with a tap anywhere else, or the phone's Back button.
- **Moving commands onto keys.** Hold an empty key (it reads *hold to fill*): its drawer opens ready, and the command you tap goes straight onto that key. A tap on an empty key does nothing, so clearing a key also switches it off. Or open a drawer and press **ASSIGN**, then tap a command; the keys it can go to light up, and you tap one. OFFENSE's drawer fills its pinned keys, INVENTORY's fills the equipment keys, and a fan key's drawer (DROP, EAT/QUAFF/READ, INTERACT) fills that key's fan.
- **Lamps under the screen.**
  - SEARCH: search mode is on (WORLD → Search mode).
  - ARMED: a command is waiting for a direction.
  - MORE: earlier messages scrolled away. Tap the message lines to read them.
- **Macros** use gurrhack's notation: `^D` is Ctrl-D, `M-x` is Meta-x, `\e` is Escape, `\n` is Enter, `\b` is backspace.

## For developers

The interface lives in `lib/src/com/tbd/forkfront/rolehack/`:

| File | What it does |
|---|---|
| `RhOverlay` | Layout and gestures |
| `RhFace` | The keycaps |
| `RhCase` | The frame |
| `RhScreen` | Messages and status |
| `RhTheme` | Styles and fonts |
| `RhCommands` | The command vocabulary |
| `RhPrefs` | Settings and pins |

It hooks into ForkFront in a few places: `NH_State` routes the Back key and sets where the map centres, and the status and "what's here" callbacks come from `winandroid.c` in RolehackDroid.

## Credits and licences

- **ForkFront:** gurrhack and JodiJodington. It has no licence file in its upstream repositories; its copyright remains with its authors.
- **The Rolehack interface:** Lucas Ruiz.
- **Fonts:** SIL Open Font License 1.1. The notices are in `lib/assets/fonts/OFL.txt`.

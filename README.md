# Sky Banker

A Hypixel SkyBlock-style bank plugin for Paper servers. Deposit and withdraw coins through a clean GUI, unlock higher-tier bank accounts, earn scheduled interest on your savings, store items in a private vault, and track it all with a full transaction history. Everything is customizable without touching a line of code.

![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.11%20--%2026.3-brightgreen)
![Paper](https://img.shields.io/badge/Paper-required-blue)
![Java](https://img.shields.io/badge/Java-21%2B-orange)
![License](https://img.shields.io/badge/license-All%20Rights%20Reserved-red)

> **Always download Sky Banker from Modrinth:** https://modrinth.com/project/GAP9zjCv
> It is the only official download. This GitHub page is for reading the source code.

---

## ✨ Features

- **Personal Bank Accounts** — a familiar deposit / withdraw / upgrade menu, styled after Hypixel SkyBlock's banker
- **7 Upgradeable Account Tiers** — from a free Starter Account up to a Palatial Account holding 60 billion coins, each with its own interest cap and balance limit
- **Tranche-Based Interest** — coins in the bank earn interest automatically every 31 hours (fully configurable), calculated the same way Hypixel does it, with a live "projected interest" preview in the info menu
- **Custom Amount Input** — click "Specific Amount" and type any figure on a sign, with shorthand support (`10k`, `1.5m`, `2b`)
- **Personal Vault** *(new in 1.3)* — a private item storage each player buys once. The price and size are set in `config.yml`, and items are saved when the vault is closed
- **Sound Settings** *(new in 1.3)* — every player can turn each bank sound on or off (interest, deposit, withdraw, upgrade, errors, menu clicks), or mute them all, and the choice is remembered
- **Settings Menu** — a profile page with your own player head (username, account status, tier, balance, interest info, and the date you first used the bank), plus the Sounds and Vault buttons. Server owners can switch it off completely in `config.yml`
- **Update Notice** *(new in 1.3)* — admins are told in chat when a newer version is available, with a clickable download link
- **Transaction History** — the last 20 deposits, withdrawals, and interest payouts are tracked per player, with names and timestamps
- **Leaderboard & Rank Lookup** — `/skybanker top` for the 10 richest accounts, `/skybanker user <player>` to check anyone's rank
- **PlaceholderAPI Support** — 9 placeholders for balance, purse, tier, interest timer, and more, ready to drop into scoreboards, tab lists, or holograms
- **Fully Config-Driven GUI** — every item's material, name, lore, and slot position is editable in `config.yml`, no recompiling needed
- **SQLite Storage** — lightweight, no external database server required, safe for concurrent use under load
- **Built-in Anti-Spam Protection** — click cooldowns and command rate-limiting to keep the menu smooth even under heavy use

---

## 📦 Requirements

| Dependency | Notes |
|---|---|
| [Paper](https://papermc.io/) (or a Paper fork) | Supports **1.21.11, 26.1, 26.2 and 26.3**. Plain Spigot is not supported |
| Java 21+ | Works on Java 21 and Java 25 servers |
| [Vault](https://www.spigotmc.org/resources/vault.34315/) | Required — provides the economy backend |
| [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) | Optional — enables the placeholder list below |

---

## 🚀 Installation

1. Download the latest jar from **[Modrinth](https://modrinth.com/project/GAP9zjCv)** (the official download)
2. Drop it into your server's `plugins/` folder
3. Make sure **Vault** (and an economy plugin, if Vault doesn't already have one) is also installed
4. Restart your server
5. Config files (`config.yml`, `messages.yml`) will generate automatically on first run — edit them, then run `/skybanker reload all`

### Updating from an older version

- Back up your plugin folder before updating.
- Your database keeps working and no data is lost. The new tables (sound settings and vault) are created automatically.
- Your existing `config.yml` and `messages.yml` are **not** overwritten, so the new 1.3 options will be missing from them. To get the Sound menu and the Personal Vault with their proper names and lore, do one of these:
  - Delete your old `config.yml` and `messages.yml` and let the plugin generate new ones (then redo your edits), or
  - Copy the new sections from the default files into your own: `vault:`, `update-checker:`, `gui.settings-menu.items.sounds`, `gui.settings-menu.items.vault`, `gui.sound-menu`, `gui.vault-menu`, `gui.vault-confirm-menu`, and the new vault and update messages in `messages.yml`.

---

## 🎮 Commands

| Command | Description | Permission |
|---|---|---|
| `/bank` | Opens your personal bank menu | `bank.use` |
| `/skybanker` or `/sb` | Shows the command list | `bank.use` |
| `/skybanker top` | Shows the top 10 richest bank accounts | `bank.use` |
| `/skybanker user [player]` | Shows your rank, or another player's | `bank.use` |
| `/skybanker placeholder` | Lists every available placeholder | `skybanker.admin` |
| `/skybanker reload <config.yml\|message.yml\|all>` | Reloads configuration files | `skybanker.admin` |

`/sb` is a full alias of `/skybanker` — both run identical code.

---

## 🔑 Permissions

| Permission | Default | Description |
|---|---|---|
| `bank.use` | `true` | Access to `/bank`, `/skybanker`, and `/sb` |
| `skybanker.admin` | `op` | Access to the `reload` and `placeholder` subcommands, and the in-game update notice |

---

## 🏷️ Placeholders

Requires PlaceholderAPI. Test any of these with `/papi parse <player> <placeholder>`.

| Placeholder | Returns |
|---|---|
| `%skybanker_balance%` | Bank balance |
| `%skybanker_purse%` | Vault purse/wallet balance |
| `%skybanker_limit%` | Current tier's max bank balance |
| `%skybanker_level%` | Tier number (1–7) |
| `%skybanker_account%` | Tier display name (e.g. "Gold Account") |
| `%skybanker_interest_timer%` | Time left until next interest payout |
| `%skybanker_next_upgrade_cost%` | Cost of the next tier (or `MAX`) |
| `%skybanker_space_left%` | Coins of room left before hitting the limit |
| `%skybanker_progress%` | Percentage of bank limit filled |

---

## 🗄️ Personal Vault

The vault is a private chest for each player. Open the bank, click **Settings**, then click the vault button to the right of your player head.

- A player pays **once** with coins from their purse, after a confirmation screen.
- After buying, it opens like a normal chest (3 rows by default), and the items are saved when the vault is closed.
- Players who already bought it keep it. Changing the price later does not charge them again.

Set the price and size in `config.yml`:

```yaml
vault:
  enabled: true
  price: 5000000.0   # coins, paid once
  rows: 3            # 1 - 6 rows (3 rows = 27 slots)
```

To turn the vault off completely, set `vault.enabled: false`. After any change, run `/skybanker reload config.yml`.

---

## 🔊 Sound Settings

Open the bank, click **Settings**, then click the **Sounds** button to the left of your player head.

- **Left-click** a sound to turn it on or off.
- **Right-click** a sound to hear it first.
- The **All Sounds** switch at the top mutes or restores everything.

Every player's choices are saved, so they are still there the next time they join.

---

## ⚙️ Configuration

Everything about the plugin's look and feel lives in **`config.yml`** — no code changes required:

- Menu titles, sizes, and item slots
- Every item's material, display name, and lore (with placeholders like `%balance%`, `%limit%`, `%account%`)
- All 7 account tiers — cost, balance cap, interest cap, and their full lore text
- The Personal Vault — price, size, and its menus
- The Sound menu — slots, items, and ON/OFF text
- The sign editor's preset text for custom amount entry
- Interest interval, transaction history length, and withdraw quick-button percentage
- The Settings menu: items, lore, date format, and an on/off switch

### Turning the Settings menu off

```yaml
gui:
  settings-menu:
    enabled: false
```

Then run `/skybanker reload config.yml`. The Settings button disappears from the bank menu and the menu can't be opened.

All player-facing messages (success/error text, the upgrade banner, etc.) live in **`messages.yml`** and support `&`-based color codes and `\n` for multi-line messages.

Reload either file in-game without restarting:
```
/skybanker reload config.yml
/skybanker reload message.yml
/skybanker reload all
```

---

## 🔔 Update Notice & Privacy

Sky Banker checks whether a newer version exists every time the server starts, and then once every 24 hours. If there is one, the console prints a warning and admins (permission `skybanker.admin`) see a short chat message with a clickable download link. Normal players never see it.

**This is the only network request the plugin makes.** It contacts Modrinth, sends only the plugin name and version, and sends no player or server data. It runs in the background and never slows the server. If there is no internet, the check is skipped.

To turn it off or hide the chat notice:

```yaml
update-checker:
  enabled: true         # false = never check
  notify-admins: true   # false = console message only
```

---

## 🔍 Checking the Code

The source code is public so you can see that the plugin is safe. You are welcome to read it and to build it on your own computer to check that the release jar matches the code:

```bash
git clone https://github.com/sko-ohy/SkyBanker.git
cd SkyBanker
./gradlew clean build
```

The compiled jar will be in `build/libs/`.

Building is allowed **for checking only**. Under the license below, you may not share, upload, sell, or publish a jar you built yourself, and you may not copy or modify the code without written permission.

---

## 📚 Built With

- [Paper API](https://papermc.io/) — server API, including the built-in sign editor used for custom amount input
- [Vault](https://github.com/MilkBowl/VaultAPI) — economy integration
- [SQLite JDBC](https://github.com/xerial/sqlite-jdbc) — data storage
- [PlaceholderAPI](https://github.com/PlaceholderAPI/PlaceholderAPI) — optional placeholder support

---

## 🤖 About the Code

This plugin is not fully AI-generated. I edited most of the lore and some of the code myself, and I used AI to help with the rest. I always test the plugin before uploading it.

---

## 📄 License

Copyright (c) 2026 Skoohy. All rights reserved.

This source code is public for viewing and security checking only. You may not copy, modify, redistribute, or sell it without written permission. See the [LICENSE](LICENSE) file.

---

## 🙋 Support

Found a bug or have a suggestion? Join my Discord server: **https://discord.gg/hGZvUmHs2t**, or open an [issue](../../issues). Feedback is always welcome.

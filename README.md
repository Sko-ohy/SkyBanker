# Sky Banker

A Hypixel SkyBlock-style bank plugin for Paper servers. Deposit and withdraw coins through a clean GUI, unlock higher-tier bank accounts, earn scheduled interest on your savings, and track it all with a full transaction history. Everything is customizable without touching a line of code.

![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.11%20--%2026.3-brightgreen)
![Paper](https://img.shields.io/badge/Paper-required-blue)
![Java](https://img.shields.io/badge/Java-21%2B-orange)
![License](https://img.shields.io/badge/license-All%20Rights%20Reserved-red)

---

## ✨ Features

- **Personal Bank Accounts** — a familiar deposit / withdraw / upgrade menu, styled after Hypixel SkyBlock's banker
- **7 Upgradeable Account Tiers** — from a free Starter Account up to a Palatial Account holding 60 billion coins, each with its own interest cap and balance limit
- **Tranche-Based Interest** — coins in the bank earn interest automatically every 31 hours (fully configurable), calculated the same way Hypixel does it, with a live "projected interest" preview in the info menu
- **Custom Amount Input** — click "Specific Amount" and type any figure on a sign, with shorthand support (`10k`, `1.5m`, `2b`)
- **Settings Menu** *(new)* — a profile page with your own player head, showing your username, account status, tier, balance, interest info and the date you first used the bank. Server owners can switch it off completely in `config.yml`
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

1. Download the latest jar from the [Releases](../../releases) page
2. Drop it into your server's `plugins/` folder
3. Make sure **Vault** (and an economy plugin, if Vault doesn't already have one) is also installed
4. Restart your server
5. Config files (`config.yml`, `messages.yml`) will generate automatically on first run — edit them, then run `/skybanker reload all`

### Updating from an older version

- Your database keeps working, no data is lost.
- Your existing `config.yml` and `messages.yml` are **not** overwritten. If a new option is missing from your old config, the plugin uses its built-in default, so everything still works. To edit the new options, copy them from the default config or let the plugin generate a fresh file.
- Back up your plugin folder before updating.

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
| `skybanker.admin` | `op` | Access to the `reload` and `placeholder` subcommands |

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

## ⚙️ Configuration

Everything about the plugin's look and feel lives in **`config.yml`** — no code changes required:

- Menu titles, sizes, and item slots
- Every item's material, display name, and lore (with placeholders like `%balance%`, `%limit%`, `%account%`)
- All 7 account tiers — cost, balance cap, interest cap, and their full lore text
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

## 🔍 Checking the Code

The source code is public so you can see that the plugin is safe. You are welcome to read it and to build it on your own computer to check that the release jar matches the code:

```bash
git clone https://github.com/yourusername/Sky-Banker.git
cd Sky-Banker
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

Older versions of Sky Banker used [SignGUI](https://github.com/Rapha149/SignGUI) by Rapha149 for the sign input. Version 1.2. no longer uses it.

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

# Sky Banker

A Hypixel SkyBlock-style bank plugin for Paper servers. Deposit and withdraw coins through a clean GUI, unlock higher-tier bank accounts, earn scheduled interest on your savings, and track it all with a full transaction history — all fully customizable without touching a line of code.

![Minecraft Version](https://img.shields.io/badge/Minecraft-1.20%2B-brightgreen)
![Paper](https://img.shields.io/badge/Paper-26.2-blue)
![License](https://img.shields.io/badge/license-MIT-green)

---

## ✨ Features

- **Personal Bank Accounts** — a familiar deposit / withdraw / upgrade menu, styled after Hypixel SkyBlock's banker
- **7 Upgradeable Account Tiers** — from a free Starter Account up to a Palatial Account holding 60 billion coins, each with its own interest cap and balance limit
- **Tranche-Based Interest** — coins in the bank earn interest automatically every 31 hours (fully configurable), calculated the same way Hypixel does it, with a live "projected interest" preview in the info menu
- **Custom Amount Input** — click "Specific Amount" and type any figure directly with shorthand support (`10k`, `1.5m`, `2b`)
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
| [Paper](https://papermc.io/) 1.20+ | Built and tested on Paper 26.2 |
| Java 21+ | |
| [Vault](https://www.spigotmc.org/resources/vault.34315/) | Required — provides the economy backend |
| [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) | Optional — enables the placeholder list below |

---

## 🚀 Installation

1. Download the latest `Sky_Banker.jar` from the [Releases](../../releases) page
2. Drop it into your server's `plugins/` folder
3. Make sure **Vault** (and an economy plugin, if Vault doesn't already have one) is also installed
4. Restart your server
5. Config files (`config.yml`, `messages.yml`) will generate automatically on first run — edit them, then run `/skybanker reload all`

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
| `skybanker.admin` | `op` | Access to the `reload` subcommand |

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

All player-facing messages (success/error text, the upgrade banner, etc.) live in **`messages.yml`** and support `&`-based color codes and `\n` for multi-line messages.

Reload either file in-game without restarting:
```
/skybanker reload config.yml
/skybanker reload message.yml
/skybanker reload all
```

---

## 🛠️ Building from Source

```bash
git clone https://github.com/yourusername/Sky-Banker.git
cd Sky-Banker
./gradlew clean build
```
The compiled jar will be in `build/libs/`.

---

## 📚 Built With

- [Paper API](https://papermc.io/)
- [Vault](https://github.com/MilkBowl/VaultAPI) — economy integration
- [SignGUI](https://github.com/Rapha149/SignGUI) by Rapha149 — custom amount input
- [SQLite JDBC](https://github.com/xerial/sqlite-jdbc) — data storage
- [PlaceholderAPI](https://github.com/PlaceholderAPI/PlaceholderAPI) — optional placeholder support

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).

---

## 🙋 Support

Found a bug or have a suggestion? Open an [issue](../../issues) — feedback is always welcome.

# M9 Bayonet

**CS2 weapons in Minecraft.** Rename a crossbow and it becomes the CS2 AWP. Rename an iron sword and it becomes a CS2 knife. Real skins, real animations, real sounds.

M9 Bayonet is a client-side Fabric mod for Minecraft **26.3** (branch `main`) and **26.2** (branch `26.2`). Current version: **1.0.3**. Made for the HugoSMP.net Halloween event.

## Get it

1. Download the latest release from the [Releases page](https://github.com/andoraspalace/m9-bayonet/releases). Every release has two jars:
   - `bayonet-26.3.jar` for Minecraft 26.3
   - `bayonet-26.2.jar` for Minecraft 26.2
2. Install **Fabric Loader** and **Fabric API**.
3. Put the jar in your `mods` folder. That's it.

Mod Menu is optional. Cloth Config is already bundled.

## Skins

The anvil name picks the skin. Nothing else does. If you forget the names, type `/bayonet skins` in game for a clickable list.

| Weapon | Item | Skins |
|---|---|---|
| AWP | Crossbow | Dragon Lore, Asiimov |
| M9 Bayonet | Iron sword | Default, Emerald, Ruby |
| Butterfly Knife | Iron sword | Default, Sapphire, Ruby, Gamma Doppler |

Want StatTrak on your AWP? Add "StatTrak" to the name. It counts your kills.

## What you get

- First-person animations: draw, inspect, attacks, backstabs, plus the AWP reload and shot
- CS2 sounds with CS2 distance falloff. Other players with the mod hear them too.
- CS2 first-person arms with Sport Gloves | Vice (on by default)
- Wear based on durability, from Factory New down to Battle-Scarred
- A StatTrak kill counter on AWPs
- An AWP in an item frame turns into a big wall display

## Controls

- `G`: inspect
- `R`: reload the AWP

Commands: `/bayonet skins`, `/bayonet arms`, `/bayonet glint`.

In Mod Menu you can change the keybinds and toggle the CS2 arms and "Hide enchantment glint".

## Good to know

- It's client-side. Players without the mod just see normal crossbows and swords.
- It doesn't work with **Punchy** (the attack animation mod). Pick one.
- If a server gives an item its own custom model, the server's look wins.

## Coming next

**1.0.4** is all about third person: how the weapons sit in your hand and how the animations look from the outside.

## A bit of history

This started as my old Minecraft 1.16 mod that brought Counter-Strike assets into Minecraft. Now it's on modern Minecraft, and it uses CS2 models instead of CS:GO ones. An AI (Claude Opus 5.5) did the migration and the code, so expect bugs and weird behavior!

## Building it yourself

This repo has **no Counter-Strike assets**. They belong to Valve, so they can't be shared here. If you build without them, the jar only contains code. Not much to look at.

To build the full mod you need the CS2 game files:

1. Run `tools/vpk_chunks.py` to find the files the mod needs.
2. Run `tools/build_assets.py` to turn a Source 2 Viewer export into the mod's models, sounds and textures.
3. Run `./gradlew build`.

Use branch `main` for 26.3 and branch `26.2` for 26.2.

If you're not sure how to do that... just give up!

## License

The code is licensed under **GPL-3.0-or-later**. The license covers the code only.

You are free to modify this mod, add new features, and republish your own versions. Just make sure to **respect the license!**

## Disclaimer

The Counter-Strike 2 assets are owned by Valve, and this repository does not include them. This project is not affiliated with or endorsed by Valve.

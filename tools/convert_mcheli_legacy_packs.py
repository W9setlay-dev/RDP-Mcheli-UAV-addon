#!/usr/bin/env python3
"""Convert the RDP instance's legacy MCHeli TXT packs to McHeliCE YAML layout."""

from __future__ import annotations

import argparse
import csv
import json
import os
from pathlib import Path
import re
import shutil
import sys
import tempfile


PACKS = {
    "dronepack": {
        "planes": ("orlan10", "orlan30", "shark2", "supercum", "zala421"),
        "weapons": ("fab500umpk", "gbu39", "kab500marker"),
        "hud": (),
        "helicopters": (),
        "sound_names": {
            "shahed": "legacy_dronepack_shahed",
            "rocket_snd": "legacy_dronepack_rocket_snd",
        },
        "weapon_renames": {},
    },
    "fpvdrones": {
        "planes": (),
        "weapons": ("fpvbomb", "fpvbombaa", "fpvbombat"),
        "hud": ("fpv_hud", "fpv_hudair"),
        "helicopters": ("fpvof34", "fpvskyrusoriz"),
        "sound_names": {
            "fpvdrone": "legacy_fpvdrones_fpvdrone",
            "fpvbomb": "legacy_fpvdrones_fpvdrone",
        },
        "weapon_renames": {"fpvbomb": "legacy_fpvdrones_fpvbomb"},
    },
}

VEHICLE_EXTRA_WEAPONS = {
    "zala421": ("zala421_selfdestruct", "0.00, -0.2, 0.4"),
}
ONE_WAY_UAVS = {"zala421"}

VEHICLE_KEYS = {
    "DisplayName", "MaxHp", "Speed", "Sound", "CameraPosition",
    "ThrottleUpDown", "MaxFuel", "FuelConsumption", "Stealth",
    "RotorSpeed", "HUD", "ParticlesScale", "ThirdPersonDist",
    "AddSeat", "HideEntity", "Category", "AddRecipe", "AddWeapon",
    "AddPartRotor", "AddBlade", "BoundingBox", "EnableVtol",
    "VtolYaw", "VtolPitch", "AddTexture", "AddRotor", "MobilityYaw",
    "MobilityPitch", "MobilityRoll", "AddShapelessRecipe",
}
WEAPON_KEYS = {
    "DisplayName", "Type", "Power", "Acceleration", "Explosion",
    "ExplosionBlock", "ExplosionInWater", "Flaming", "Sight", "Delay",
    "Sound", "DisableSmoke", "ModelBullet", "Gravity", "ReloadTime",
    "Round", "DamageFactor", "MaxAmmo", "SuppliedNum", "Item", "LockTime",
    "ModeNum", "ProximityFuseDist", "RidableOnly",
}


def scalar(value: str) -> str:
    return json.dumps(value, ensure_ascii=False)


def number(value: str) -> str:
    value = value.strip()
    if not re.fullmatch(r"[-+]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][-+]?\d+)?", value):
        raise ValueError("not a numeric value: " + value)
    return value


def boolean(value: str) -> str:
    normalized = value.strip().lower()
    if normalized not in ("true", "false"):
        raise ValueError("not a boolean value: " + value)
    return normalized


def vector(value: str, count: int = 3) -> list[str]:
    parts = [part.strip() for part in value.split(",")]
    if len(parts) != count:
        raise ValueError("expected {} vector components: {}".format(count, value))
    return [number(part) for part in parts]


def parse_legacy(path: Path, allowed: set[str]) -> dict[str, list[str]]:
    parsed: dict[str, list[str]] = {}
    for line_number, line in enumerate(path.read_text(encoding="utf-8-sig").splitlines(), 1):
        line = line.strip()
        if not line or line.startswith((";", "#")) or "=" not in line:
            continue
        key, value = (part.strip() for part in line.split("=", 1))
        if key not in allowed:
            raise ValueError("{}:{}: unsupported legacy key {!r}".format(path, line_number, key))
        parsed.setdefault(key, []).append(value)
    return parsed


def first(values: dict[str, list[str]], key: str, default: str | None = None) -> str | None:
    entries = values.get(key)
    return entries[0] if entries else default


def csv_values(value: str) -> list[str]:
    row = next(csv.reader([value], skipinitialspace=True))
    while row and not row[-1].strip():
        row.pop()
    return [part.strip().strip('"') for part in row]


def emit_vehicle(path: Path,
                 weapon_renames: dict[str, str],
                 sound_names: dict[str, str]) -> tuple[str, list[str]]:
    values = parse_legacy(path, VEHICLE_KEYS)
    out: list[str] = []
    notes: list[str] = []

    display_name = first(values, "DisplayName", path.stem)
    out.extend(("DisplayName: " + scalar(display_name),
                "Category: " + scalar(first(values, "Category", "M.A"))))
    for legacy, modern in (("MaxHp", "MaxHP"), ("MaxFuel", "MaxFuel"),
                           ("FuelConsumption", "FuelConsumption"), ("Stealth", "Stealth")):
        value = first(values, legacy)
        if value is not None:
            out.append(modern + ": " + number(value))

    camera_lines = []
    third_person = first(values, "ThirdPersonDist")
    if third_person is not None:
        camera_lines.append("  ThirdPersonDist: " + number(third_person))
    camera = first(values, "CameraPosition")
    if camera is not None:
        camera_lines.extend(("  Pos:",
                             "  - Pos: [" + ", ".join(vector(camera)) + "]"))
    if camera_lines:
        out.append("Camera:")
        out.extend(camera_lines)

    sound = first(values, "Sound")
    if sound is not None:
        sound = sound_names.get(sound, sound)
        out.extend(("Sound:", "  MoveSound: " + scalar("mcheli:" + sound)))

    speed = first(values, "Speed")
    if speed is not None:
        out.extend(("PhysicalProperties:",
                    "  Speed: " + number(speed)))
    mobility = [(key, first(values, key)) for key in
                ("MobilityYaw", "MobilityPitch", "MobilityRoll")]
    mobility = [(key, value) for key, value in mobility if value is not None]
    if mobility:
        out.append("Mobility:")
        for key, value in mobility:
            out.append("  " + key.removeprefix("Mobility") + ": " + number(value))

    render_values: list[tuple[str, str]] = []
    hide_entity = first(values, "HideEntity")
    if hide_entity is not None:
        render_values.append(("HideRiders", boolean(hide_entity)))
    particle_scale = first(values, "ParticlesScale")
    if particle_scale is not None:
        render_values.append(("ParticleScale", number(particle_scale)))
    if values.get("AddTexture"):
        render_values.append(("Textures", ""))

    throttle = first(values, "ThrottleUpDown")
    if throttle is not None:
        out.extend(("AircraftFeatures:",
                    "  ThrottleUpDown: " + number(throttle)))
    if path.stem in ONE_WAY_UAVS:
        out.extend(("Uav:",
                    "  IsUav: true",
                    "  IsSmallUav: true"))
    rotor_speed = first(values, "RotorSpeed")
    if rotor_speed is not None:
        out.append("RotorSpeed: " + number(rotor_speed))
    vtol = first(values, "EnableVtol")
    if vtol is not None:
        out.extend(("PlaneFeatures:",
                    "  EnableVtol: " + boolean(vtol)))
    if render_values:
        out.append("Render:")
        for key, value in render_values:
            if key == "Textures":
                out.append("  Textures:")
                out.extend("  - " + scalar(name.strip())
                           for name in values.get("AddTexture", []))
            else:
                out.append("  {}: {}".format(key, value))

    weapons = []
    for entry in values.get("AddWeapon", []):
        fields = csv_values(entry)
        if len(fields) not in (4, 6):
            raise ValueError("{}: unsupported AddWeapon entry {!r}".format(path, entry))
        weapon_name = weapon_renames.get(fields[0], fields[0])
        weapon = ["- Type: " + scalar(weapon_name),
                  "  Position: [" + ", ".join(number(v) for v in fields[1:4]) + "]"]
        if len(fields) == 6:
            weapon.extend(("  Yaw: " + number(fields[4]),
                           "  Pitch: " + number(fields[5])))
        weapons.extend(weapon)
    extra_weapon = VEHICLE_EXTRA_WEAPONS.get(path.stem)
    if extra_weapon:
        weapon_name, position = extra_weapon
        weapons.extend(("- Type: " + scalar(weapon_name),
                        "  Position: [" + ", ".join(number(v) for v in vector(position)) + "]"))
    if weapons:
        out.append("Weapons:")
        out.extend("  " + line for line in weapons)

    hud = first(values, "HUD")
    seats = []
    for seat in values.get("AddSeat", []):
        seats.extend(("- Position: [" + ", ".join(vector(seat)) + "]",))
        if hud:
            seats.append("  Hud: " + scalar(hud))
    if seats:
        out.append("Seats:")
        out.extend("  " + line for line in seats)

    boxes = []
    for entry in values.get("BoundingBox", []):
        fields = csv_values(entry)
        if len(fields) != 5:
            raise ValueError("{}: unsupported BoundingBox {!r}".format(path, entry))
        x, y, z, width, height = (number(part) for part in fields)
        boxes.extend(("- Type: DEFAULT",
                      "  Position: [{}, {}, {}]".format(x, y, z),
                      "  Size: [{}, {}, {}]".format(width, height, width)))
    if boxes:
        out.append("BoundingBoxes:")
        out.extend("  " + line for line in boxes)

    rotors = []
    blades = values.get("AddBlade", [])
    part_rotors = values.get("AddPartRotor", [])
    if len(part_rotors) != len(blades):
        raise ValueError("{}: AddPartRotor/AddBlade counts do not match".format(path))
    for rotor, blade in zip(part_rotors, blades):
        rotor_fields = csv_values(rotor)
        blade_fields = csv_values(blade)
        if len(rotor_fields) != 6 or len(blade_fields) != 8:
            raise ValueError("{}: unsupported plane rotor declaration".format(path))
        rotors.extend(("- Position: [" + ", ".join(number(v) for v in rotor_fields[:3]) + "]",
                       "  Rotation: [" + ", ".join(number(v) for v in rotor_fields[3:]) + "]",
                       "  Blades:",
                       "  - Position: [" + ", ".join(number(v) for v in blade_fields[2:5]) + "]",
                       "    Rotation: [" + ", ".join(number(v) for v in blade_fields[5:8]) + "]",
                       "    BladeNum: " + str(int(number(blade_fields[0]))),
                       "    BladeRot: " + str(int(number(blade_fields[1])))))
    heli_rotors = []
    for entry in values.get("AddRotor", []):
        fields = csv_values(entry)
        if len(fields) != 8:
            raise ValueError("{}: unsupported helicopter AddRotor {!r}".format(path, entry))
        heli_rotors.extend(("- BladeCount: " + str(int(number(fields[0]))),
                            "  BladeRot: " + str(int(number(fields[1]))),
                            "  Position: [" + ", ".join(number(v) for v in fields[2:5]) + "]",
                            "  Rotation: [" + ", ".join(number(v) for v in fields[5:8]) + "]"))
    if rotors or heli_rotors:
        out.append("Components:")
        if rotors:
            out.append("  PlaneRotor:")
            out.extend("  " + line for line in rotors)
        if heli_rotors:
            out.append("  HeliRotor:")
            out.extend("  " + line for line in heli_rotors)

    if values.get("AddRecipe"):
        shaped = values.get("AddRecipe", [])
    else:
        shaped = []
    shapeless = values.get("AddShapelessRecipe", [])
    if shaped and shapeless:
        raise ValueError("{}: shaped and shapeless legacy recipes cannot share one "
                         "McHeliCE Recepie block".format(path))
    recipes = shaped or shapeless
    if recipes:
        out.extend(("Recepie:",
                    "  isShaped: " + ("true" if shaped else "false"),
                    "  Pattern:"))
        for recipe in recipes:
            out.extend(("  - >-", "    " + recipe))
    if values.get("VtolYaw") or values.get("VtolPitch"):
        notes.append("Legacy VtolYaw/VtolPitch were not emitted because McHeliCE's "
                     "documented YAML EnableVtol map currently has a parser bug.")
    if not out:
        raise ValueError("empty vehicle definition: " + str(path))
    return "\n".join(out) + "\n", notes


def emit_weapon(path: Path, available_bullet_models: set[str],
                sound_names: dict[str, str]) -> tuple[str, list[str]]:
    values = parse_legacy(path, WEAPON_KEYS)
    notes: list[str] = []
    out = ["DisplayName: " + scalar(first(values, "DisplayName", path.stem)),
           "Type: " + scalar(first(values, "Type", "rocket").lower())]
    if first(values, "Power") is not None:
        out.append("BaseDamage: " + number(first(values, "Power")))
    ballistics = []
    for legacy, modern in (("Acceleration", "Acceleration"),
                           ("Gravity", "Gravity")):
        value = first(values, legacy)
        if value is not None:
            ballistics.append((modern, number(value)))
    if ballistics:
        out.append("Ballistics:")
        out.extend("  {}: {}".format(key, value) for key, value in ballistics)
    explosion = []
    for legacy, modern in (("Explosion", "Power"),
                           ("ExplosionBlock", "DestructionPower"),
                           ("ExplosionInWater", "PowerUnderwater"),
                           ("Flaming", "Flaming"),
                           ("ProximityFuseDist", "ProximityFuseDist")):
        value = first(values, legacy)
        if value is not None:
            normalized = boolean(value) if modern == "Flaming" else number(value)
            explosion.append((modern, normalized))
    if explosion:
        out.append("Explosion:")
        out.extend("  {}: {}".format(key, value) for key, value in explosion)
    sight = first(values, "Sight")
    if sight is not None:
        out.extend(("Camera:", "  Sight: " + scalar(sight.lower())))
    lock_time = first(values, "LockTime")
    if lock_time is not None:
        out.extend(("Missile:", "  LockOn:", "    Time: " + number(lock_time)))
    sound = first(values, "Sound")
    if sound is not None:
        sound = sound_names.get(sound, sound)
        out.extend(("Sound:", "  Locations:",
                    "    Fire: " + scalar("mcheli:" + sound)))
    ammo = []
    for legacy, modern in (("ReloadTime", "ReloadTime"), ("Round", "MagSize"),
                           ("MaxAmmo", "MaxAmmo"), ("SuppliedNum", "ResupplyCount")):
        value = first(values, legacy)
        if value is not None:
            ammo.append((modern, str(int(number(value)))))
    items = []
    for entry in values.get("Item", []):
        fields = csv_values(entry)
        if len(fields) != 2:
            raise ValueError("{}: unsupported Item declaration {!r}".format(path, entry))
        item_name = fields[1]
        if ":" not in item_name:
            item_name = "minecraft:" + item_name
        items.extend(("- Count: " + str(int(number(fields[0]))),
                      "  Name: " + scalar(item_name),
                      "  Meta: 0"))
    if ammo or items:
        out.append("Ammo:")
        out.extend("  {}: {}".format(key, value) for key, value in ammo)
        if items:
            out.append("  AmmoItems:")
            out.extend("  " + line for line in items)
    delay = first(values, "Delay")
    if delay is not None:
        out.append("FireCooldown: " + str(int(number(delay))))
    mode = first(values, "ModeNum")
    if mode is not None:
        out.append("Mode: " + str(int(number(mode))))
    if first(values, "RidableOnly") is not None:
        out.append("RidableOnly: " + boolean(first(values, "RidableOnly")))
    damage_factors = []
    for entry in values.get("DamageFactor", []):
        fields = entry.replace(",", " ").split()
        if len(fields) != 2:
            raise ValueError("{}: unsupported DamageFactor {!r}".format(path, entry))
        alias = "Player" if fields[0].lower() == "player" else fields[0].lower()
        damage_factors.append((alias, number(fields[1])))
    if damage_factors:
        out.append("DamageFactor:")
        out.extend("  {}: {}".format(key, value) for key, value in damage_factors)
    model = first(values, "ModelBullet")
    disable_smoke = first(values, "DisableSmoke")
    if model and model not in available_bullet_models:
        notes.append("Referenced bullet model {!r} does not exist in the instance assets; "
                     "the unavailable ModelBullet reference was omitted.".format(model))
        model = None
    if model or disable_smoke is not None:
        out.append("Render:")
        if model:
            out.append("  BulletModel: " + scalar(model))
        if disable_smoke is not None:
            out.extend(("  Smoke:",
                        "    DisableSmoke: " + boolean(disable_smoke)))
    return "\n".join(out) + "\n", notes


def emit_zala_self_destruct_weapon() -> str:
    return """DisplayName: Zala-421 Self-Destruct Payload
CanLoadChunks: true
Type: bomb
BaseDamage: 300
Ballistics:
  Acceleration: 0.1
  Gravity: -0.03
  GravityInWater: -0.03
  VelocityInWater: 0.999
Sound:
  Locations:
    Fire: mcheli:bomb_snd
Explosion:
  Power: 7
  DestructionPower: 5
  ExplosionAirburst: 10
  Flaming: true
DamageFactor:
  plane: 100.0
  heli: 100.0
  vehicle: 100.0
  Player: 100.0
Ammo:
  MagSize: 1
  MaxAmmo: 1
  ResupplyCount: 1
  AmmoItems:
  - Count: 1
    Name: hbm:warhead_generic_small
    Meta: 0
FireCooldown: 1
SelfDestruct: true
"""


def emit_hud(path: Path) -> str:
    commands: list[tuple[int, str]] = []
    for line_number, raw in enumerate(path.read_text(encoding="utf-8-sig").splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith((";", "#")):
            continue
        if line.lower() == "endif":
            commands.append((line_number, "EndIf="))
            continue
        if "=" not in line:
            raise ValueError("{}:{}: unsupported HUD statement {!r}".format(
                path, line_number, line))
        key, value = (part.strip() for part in line.split("=", 1))
        commands.append((line_number, key + "=" + value))

    output: list[str] = []
    indent = 0
    for line_number, command in commands:
        key, value = command.split("=", 1)
        key = key.strip()
        value = value.strip()
        prefix = " " * indent + "- "
        if key.lower() == "endif":
            indent -= 4
            if indent < 0:
                raise ValueError("{}:{}: unmatched EndIf".format(path, line_number))
            continue
        if key.lower() == "if":
            output.append(prefix + "If: " + scalar(value))
            output.append(" " * (indent + 2) + "Do:")
            indent += 4
            continue
        if key.lower() == "call":
            output.append(prefix + "Call: " + scalar(value))
        elif key.lower() == "color":
            output.append(prefix + "Color: " + scalar(value))
        elif key.lower() in ("drawstring", "drawcenteredstring"):
            fields = csv_values(value)
            if len(fields) < 3:
                raise ValueError("{}:{}: invalid DrawString".format(path, line_number))
            x, y = fields[0], fields[1]
            fmt = fields[2]
            variables = [item.strip() for item in fields[3:] if item.strip()]
            output.extend((prefix + "DrawString:",
                           " " * (indent + 2) + "Text:",
                           " " * (indent + 4) + "Fmt: " + scalar(fmt),
                           " " * (indent + 4) + "Vars: [" + ", ".join(
                               scalar(var) for var in variables) + "]",
                           " " * (indent + 2) + "Position: [" + scalar(x) + ", "
                           + scalar(y) + "]",
                           " " * (indent + 2) + "Center: "
                           + ("true" if key.lower() == "drawcenteredstring" else "false")))
        elif key.lower() == "drawtexture":
            fields = csv_values(value)
            if len(fields) != 10:
                raise ValueError("{}:{}: invalid DrawTexture".format(path, line_number))
            name, x, y, width, height, u, v, uv_width, uv_height, rotation = fields
            output.extend((prefix + "DrawTexture:",
                           " " * (indent + 2) + "Texture: " + scalar(name),
                           " " * (indent + 2) + "Position: [" + scalar(x) + ", " + scalar(y) + "]",
                           " " * (indent + 2) + "Size: [" + scalar(width) + ", " + scalar(height) + "]",
                           " " * (indent + 2) + "UVPos: [" + scalar(u) + ", " + scalar(v) + "]",
                           " " * (indent + 2) + "UVSize: [" + scalar(uv_width) + ", "
                           + scalar(uv_height) + "]",
                           " " * (indent + 2) + "Rotation: " + scalar(rotation)))
        elif key.lower() == "drawline":
            fields = csv_values(value)
            if len(fields) != 4:
                raise ValueError("{}:{}: invalid DrawLine".format(path, line_number))
            output.extend((prefix + "DrawLine:",
                           " " * (indent + 2) + "Position:",
                           " " * (indent + 2) + "- [" + scalar(fields[0]) + ", "
                           + scalar(fields[1]) + "]",
                           " " * (indent + 2) + "- [" + scalar(fields[2]) + ", "
                           + scalar(fields[3]) + "]"))
        elif key.lower() == "drawrect":
            fields = csv_values(value)
            if len(fields) != 4:
                raise ValueError("{}:{}: invalid DrawRect".format(path, line_number))
            output.extend((prefix + "DrawRect:",
                           " " * (indent + 2) + "Position: [" + scalar(fields[0]) + ", "
                           + scalar(fields[1]) + "]",
                           " " * (indent + 2) + "Size: [" + scalar(fields[2]) + ", "
                           + scalar(fields[3]) + "]"))
        else:
            raise ValueError("{}:{}: unsupported HUD command {!r}".format(
                path, line_number, key))
    if indent != 0:
        raise ValueError("{}: unclosed HUD conditional".format(path))
    return "\n".join(output) + "\n"


def copy_assets(source: Path, output: Path, sound_names: dict[str, str]) -> None:
    assets = output / "assets" / "mcheli"
    for old, new in (("models", "models"), ("textures", "textures")):
        path = source / old
        if path.exists():
            shutil.copytree(path, assets / new, dirs_exist_ok=True)
    sounds = assets / "sounds"
    for folder in ("sounds", "sound"):
        path = source / folder
        if path.exists():
            for file in path.rglob("*"):
                if file.is_file():
                    sound_name = sound_names.get(file.stem, "legacy_{}_{}".format(
                        source.name, file.stem))
                    target = sounds / (sound_name + file.suffix.lower())
                    target.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copy2(file, target)


def convert_pack(source_root: Path, pack: str, output: Path,
                 asset_root: Path | None = None) -> list[str]:
    source = source_root / pack
    if not source.is_dir():
        raise ValueError("missing legacy pack: " + str(source))
    output.mkdir(parents=True, exist_ok=False)
    sound_names = PACKS[pack]["sound_names"]
    copy_assets(source, output, sound_names)
    configs = output / "assets" / "mcheli"
    notes: list[str] = []
    available_bullet_models = set()
    model_roots = {source_root}
    if asset_root is not None:
        model_roots.add(asset_root)
    for model_root in model_roots:
        for pack_root in model_root.iterdir():
            model_dir = pack_root / "models" / "bullets"
            if model_dir.is_dir():
                available_bullet_models.update(model.stem for model in model_dir.glob("*.mqo"))
            modern_model_dir = pack_root / "assets" / "mcheli" / "models" / "bullets"
            if modern_model_dir.is_dir():
                available_bullet_models.update(model.stem for model in modern_model_dir.glob("*.mqo"))
    weapon_renames = PACKS[pack]["weapon_renames"]
    for category in ("planes", "helicopters", "weapons", "hud"):
        for name in PACKS[pack][category]:
            path = source / category / (name + ".txt")
            if not path.is_file():
                raise ValueError("missing expected legacy definition: " + str(path))
            if category in ("planes", "helicopters"):
                content, file_notes = emit_vehicle(path, weapon_renames, sound_names)
                notes.extend("{}: {}".format(name, note) for note in file_notes)
            elif category == "weapons":
                content, file_notes = emit_weapon(path, available_bullet_models, sound_names)
                notes.extend("{}: {}".format(name, note) for note in file_notes)
            else:
                content = emit_hud(path)
            output_name = weapon_renames.get(name, name) if category == "weapons" else name
            if category == "weapons" and output_name != name:
                notes.append("{} was renamed to {} to avoid a duplicate weapon ID "
                             "already present in another active pack.".format(name, output_name))
            target = configs / category / (output_name + ".yml")
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(content, encoding="utf-8", newline="\n")
    for name in PACKS[pack]["planes"]:
        if name in VEHICLE_EXTRA_WEAPONS:
            target = configs / "weapons" / (VEHICLE_EXTRA_WEAPONS[name][0] + ".yml")
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(emit_zala_self_destruct_weapon(),
                              encoding="utf-8", newline="\n")

    events: dict[str, dict] = {}
    for sound in (configs / "sounds").rglob("*.ogg") if (configs / "sounds").exists() else []:
        sound_name = sound.relative_to(configs / "sounds").with_suffix("").as_posix()
        events[sound_name] = {
            "category": "master",
            "sounds": ["mcheli:" + sound_name],
        }
    if events:
        (configs / "sounds.json").write_text(
            json.dumps(events, indent=2) + "\n", encoding="utf-8")
    (output / "pack.mcmeta").write_text(json.dumps({
        "pack": {
            "pack_format": 3,
            "description": "McHeliCE 1.12.2 conversion of legacy {} content".format(pack),
        }
    }, indent=2) + "\n", encoding="utf-8")
    if notes:
        (output / "CONVERSION_NOTES.txt").write_text(
            "Conversion limitations; original files are preserved in the external backup.\n\n"
            + "\n".join("- " + note for note in notes) + "\n", encoding="utf-8")
    return notes


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True,
                        help="Directory containing the legacy dronepack and fpvdrones folders")
    parser.add_argument("--target", type=Path,
                        help="Active mcheli_addons directory; defaults to --source")
    parser.add_argument("--backup", type=Path, required=True,
                        help="Backup directory outside mcheli_addons; must not exist")
    parser.add_argument("--apply", action="store_true",
                        help="Atomically replace both legacy packs after staging")
    args = parser.parse_args()
    source_root = args.source.resolve()
    target_root = args.target.resolve() if args.target else source_root
    backup_root = args.backup.resolve()
    if not source_root.is_dir() or any(not (source_root / pack).is_dir() for pack in PACKS):
        parser.error("--source must contain both legacy pack folders")
    if not target_root.is_dir() or target_root.name.lower() != "mcheli_addons":
        parser.error("--target must be an existing mcheli_addons folder")
    try:
        backup_root.relative_to(target_root)
    except ValueError:
        pass
    else:
        parser.error("--backup must be outside mcheli_addons")
    if backup_root.exists():
        parser.error("--backup must be a new sibling/ancestor path outside mcheli_addons")

    stage_root = Path(tempfile.mkdtemp(prefix="mcheli-ce-conversion-",
                                       dir=str(target_root.parent)))
    staged: dict[str, Path] = {}
    try:
        for pack in PACKS:
            target = stage_root / pack
            notes = convert_pack(source_root, pack, target, target_root)
            staged[pack] = target
            print("{}: staged {} YAML definitions; {} note(s)".format(
                pack, len(list(target.rglob("*.yml"))), len(notes)))
        if not args.apply:
            print("Dry run only; active instance was not changed.")
            return 0

        backup_root.mkdir(parents=True, exist_ok=False)
        moved_to_backup: list[str] = []
        installed: list[str] = []
        try:
            for pack in PACKS:
                os.replace(str(target_root / pack), str(backup_root / pack))
                moved_to_backup.append(pack)
            for pack in PACKS:
                os.replace(str(staged[pack]), str(target_root / pack))
                installed.append(pack)
        except OSError:
            for pack in reversed(installed):
                os.replace(str(target_root / pack), str(staged[pack]))
            for pack in reversed(moved_to_backup):
                os.replace(str(backup_root / pack), str(target_root / pack))
            raise
        print("Converted packs installed. Original folders moved to {}".format(backup_root))
        return 0
    finally:
        if stage_root.exists():
            shutil.rmtree(stage_root)


if __name__ == "__main__":
    sys.exit(main())

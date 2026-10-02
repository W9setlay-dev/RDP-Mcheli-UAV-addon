# Legacy McHeli pack conversion

`tools/convert_mcheli_legacy_packs.py` stages and converts the two legacy packs
found in the active RDP Prism instance: `dronepack` and `fpvdrones`. It writes
McHeliCE 1.12.2 content under `assets/mcheli/`, adds `pack.mcmeta` and sound
events, and copies the original models, textures, and sounds. The default mode
is a dry run. `--apply` first moves the original pack folders to a new backup
directory outside `mcheli_addons`, then installs the staged content.
When the source backup and active instance are separate, pass the backup root
with `--source` and the active addon folder with `--target`.

The converter covers the five `dronepack` planes, two `fpvdrones` helicopters,
six legacy weapons, one Zala self-destruct payload weapon, and two HUDs. It maps
available aircraft, rotor, weapon, ammo, sound, texture, and HUD fields to the
McHeliCE YAML schema. `zala421` is explicitly emitted as a small native UAV,
mounted with `zala421_selfdestruct`, and assigned the RDP `ONE_WAY` profile.
The other legacy aircraft retain their source-defined mission capabilities;
their RDP profiles are not guessed.
Legacy comma-delimited `AddRecipe` payloads are kept intact as one
`Recepie.Pattern` entry using an unquoted YAML block scalar. The McHeliCE wiki
documents `Pattern` as `List<String>`; its recipe registration parser requires
double quotes around each of the three shaped grid rows, so those inner quotes
are recipe syntax and are intentionally preserved. The YAML block style removes
unnecessary YAML-level quoting and backslash escapes without changing the
string passed to the recipe parser.

The active instance already defines an `fpvbomb` weapon in the modern `fpvdrone`
pack. To avoid the cross-pack identifier collision, the legacy definition is
renamed to `legacy_fpvdrones_fpvbomb`, and its aircraft mounts are updated.
Bundled audio files and their sound-event names are also prefixed with their
legacy pack name so shared `mcheli` resource IDs do not replace active sounds
such as `shahed`, `fpvdrone`, or `rocket_snd`.

Known source/schema gaps are written to each converted pack's
`CONVERSION_NOTES.txt` and remain recoverable from the external backup:

- The Zala legacy `VtolYaw`/`VtolPitch` values cannot be applied through the
  currently documented YAML `EnableVtol` map; the supported boolean enable
  flag is retained.
- The legacy FPV weapon `ModelBullet=fpvbomb` asset is absent from the active
  addon assets and is omitted rather than pointing YAML at a missing model.

The YAML shape was checked against the McHeliCE upstream parser at commit
[`cbaff7f`](https://github.com/Warfactory-Official/McHeliCE/tree/cbaff7f30e549ff75e485388c24c74a4316c2228)
and against the installed McHeliCE 1.5.1 parser bytecode. In particular,
`WeaponParser` consumes `SelfDestruct`, `BaseDamage`, `Ballistics`,
`Explosion`, `Ammo`, and `FireCooldown`, while `YamlParser` consumes
`Recepie.Pattern`, `Weapons`, and `Uav.IsUav`/`IsSmallUav`. The upstream
[`plastic_bomb.yml`](https://github.com/Warfactory-Official/McHeliCE/blob/cbaff7f30e549ff75e485388c24c74a4316c2228/src/main/resources/assets/mcheli/weapons/plastic_bomb.yml)
provides the native `Type: bomb` / `SelfDestruct: true` format.
The published [Aircraft configuration (YML) wiki page](https://github.com/Warfactory-Official/McHeliCE/wiki/Aircraft-configuration-%28YML%29)
at wiki revision `544183d` confirms `Recepie.Pattern` is a `List<String>`;
[`MCH_ItemRecipe.addShapedRecipe`](https://github.com/Warfactory-Official/McHeliCE/blob/cbaff7f30e549ff75e485388c24c74a4316c2228/src/main/java/com/norwood/mcheli/MCH_ItemRecipe.java#L143-L166)
at the upstream source revision confirms the row delimiters must remain in the
string content.

The vehicle names `fpvof34` and `fpvskyrusoriz` are not added to RDP vehicle
profile aliases automatically. The active `fpvdrone` profile's mission and
payload policy is not sufficient evidence that those legacy helicopters should
share it. Similarly, the instance's `rdp-uav-hud.cfg` currently enables FLIR;
this user setting is preserved rather than overwritten during pack conversion.

Validate the staged definitions with the McHeliCE-bundled SnakeYAML parser and
resolve model, texture, and sound references before using a converted pack.
YAML syntax validation alone does not prove successful entity registration,
weapon execution, HUD rendering, or in-game flight.

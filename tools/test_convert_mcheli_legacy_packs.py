import tempfile
import unittest
from pathlib import Path

import convert_mcheli_legacy_packs as converter


class LegacyPackConversionTests(unittest.TestCase):
    def write_legacy(self, contents: str, name: str = "definition") -> Path:
        temp_dir = tempfile.TemporaryDirectory()
        self.addCleanup(temp_dir.cleanup)
        path = Path(temp_dir.name) / (name + ".txt")
        path.write_text(contents, encoding="utf-8")
        return path

    def test_vehicle_weapon_mount_is_renamed_and_recipe_is_preserved(self) -> None:
        path = self.write_legacy(
            "DisplayName = Test plane\n"
            "AddWeapon = fpvbomb, 0, 1, 2\n"
            "AddRecipe = \"SSS\", I, iron_ingot\n"
        )

        yaml, notes = converter.emit_vehicle(
            path,
            {"fpvbomb": "legacy_fpvdrones_fpvbomb"},
            {"shahed": "legacy_dronepack_shahed"})

        self.assertIn('Type: "legacy_fpvdrones_fpvbomb"', yaml)
        self.assertIn("Recepie:\n  isShaped: true\n  Pattern:", yaml)
        self.assertIn('  - >-\n    "SSS", I, iron_ingot', yaml)
        self.assertEqual([], notes)

    def test_zala_vehicle_gets_self_destruct_weapon_and_native_uav_flags(self) -> None:
        path = self.write_legacy(
            "DisplayName = zala421\n"
            "AddWeapon = fab500umpk, 0, -0.2, 0.4\n"
            ,
            name="zala421",
        )

        yaml, _ = converter.emit_vehicle(path, {}, {})
        weapon = converter.emit_zala_self_destruct_weapon()

        self.assertIn("Uav:\n  IsUav: true\n  IsSmallUav: true", yaml)
        self.assertIn('Type: "zala421_selfdestruct"', yaml)
        self.assertIn("SelfDestruct: true", weapon)
        self.assertIn("Type: bomb", weapon)

    def test_legacy_recipe_pattern_uses_unquoted_yaml_block_scalar(self) -> None:
        path = self.write_legacy(
            'AddRecipe = " I ",  " I ",  " F ",  I,iron_ingot, F,furnace\n'
        )

        yaml, _ = converter.emit_vehicle(path, {}, {})

        self.assertIn(
            '  - >-\n    " I ",  " I ",  " F ",  I,iron_ingot, F,furnace',
            yaml,
        )
        self.assertNotIn("  - '", yaml)
        self.assertNotIn(r'\"', yaml)

    def test_weapon_omits_missing_bullet_model_and_reports_it(self) -> None:
        path = self.write_legacy(
            "DisplayName = Test bomb\n"
            "Type = Rocket\n"
            "Power = 50\n"
            "ModelBullet = missing_model\n"
        )

        yaml, notes = converter.emit_weapon(path, set(), {})

        self.assertIn('Type: "rocket"', yaml)
        self.assertNotIn("BulletModel:", yaml)
        self.assertTrue(any("missing_model" in note for note in notes))

    def test_local_weapon_sound_is_namespaced_to_avoid_active_pack_collisions(self) -> None:
        path = self.write_legacy(
            "DisplayName = Test bomb\n"
            "Type = Rocket\n"
            "Sound = fpvbomb\n"
        )

        yaml, _ = converter.emit_weapon(
            path, set(), {"fpvbomb": "legacy_fpvdrones_fpvdrone"})

        self.assertIn('Fire: "mcheli:legacy_fpvdrones_fpvdrone"', yaml)

    def test_hud_conditionals_become_nested_commands_and_keep_color_expression(self) -> None:
        path = self.write_legacy(
            "Color = reloading==0? #c0c0c0: #ff0000\n"
            "If = reloading==1\n"
            "DrawRect = 1, 2, 3, 4\n"
            "Endif\n"
            "DrawCenteredString = 0, 5, \"%s\", WPN_NAME\n"
        )

        yaml = converter.emit_hud(path)

        self.assertIn('"reloading==0? #c0c0c0: #ff0000"', yaml)
        self.assertIn('Do:\n    - DrawRect:', yaml)
        self.assertIn('DrawString:', yaml)
        self.assertIn('Center: true', yaml)
        self.assertNotIn("EndIf", yaml)


if __name__ == "__main__":
    unittest.main()

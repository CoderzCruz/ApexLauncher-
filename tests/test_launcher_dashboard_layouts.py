"""Static checks for the launcher's responsive dashboard resources.

Run with: python3 -m unittest discover -s tests
These checks complement (but do not replace) Android build and device tests.
"""

import unittest
from collections import Counter
from pathlib import Path
import xml.etree.ElementTree as ET


RES = Path(__file__).resolve().parents[1] / "app_pojavlauncher/src/main/res"
ANDROID = "{http://schemas.android.com/apk/res/android}"
CONFIGURATIONS = {
    "portrait": ("layout",),
    "compact_landscape": ("layout-land", "layout"),
    "wide_landscape": ("layout-w720dp-land", "layout-land", "layout"),
}


def layout_file(name, directories):
    for directory in directories:
        path = RES / directory / f"{name}.xml"
        if path.exists():
            return path
    raise AssertionError(f"No layout for {name} in {directories}")


def inflated_elements(name, directories):
    """Expand includes using the resources that Android selects for a configuration."""
    root = ET.parse(layout_file(name, directories)).getroot()
    for element in root.iter():
        if element.tag == "include":
            reference = element.attrib["layout"]
            assert reference.startswith("@layout/")
            yield from inflated_elements(reference[len("@layout/"):], directories)
        else:
            yield element


def element_ids(elements):
    result = []
    for element in elements:
        value = element.get(ANDROID + "id", "")
        if value.startswith(("@+id/", "@id/")):
            result.append(value.split("/", 1)[1])
    return Counter(result)


class LauncherDashboardLayoutTests(unittest.TestCase):
    def test_activity_bindings_exist_once_in_every_configuration(self):
        required = {
            "container_fragment", "setting_button", "account_spinner",
            "account_add_button", "progress_layout",
        }
        rail = {
            "launcher_navigation", "launcher_nav_home", "launcher_nav_create",
            "launcher_nav_modpacks", "launcher_nav_controls", "launcher_nav_files",
            "launcher_rail_actions",
        }
        for configuration, directories in CONFIGURATIONS.items():
            with self.subTest(configuration=configuration):
                elements = list(inflated_elements("activity_pojav_launcher", directories))
                ids = element_ids(elements)
                for name in required | (rail if configuration != "portrait" else set()):
                    self.assertEqual(ids[name], 1, name)
                expected_types = {
                    "container_fragment": "androidx.fragment.app.FragmentContainerView",
                    "setting_button": "ImageButton",
                    "account_spinner": "com.kdt.mcgui.mcAccountSpinner",
                    "progress_layout": "com.kdt.mcgui.ProgressLayout",
                }
                for name, expected_type in expected_types.items():
                    element = next(item for item in elements
                                   if item.get(ANDROID + "id", "").split("/")[-1] == name)
                    self.assertEqual(element.tag, expected_type, name)
                if configuration == "portrait":
                    self.assertEqual(ids["launcher_navigation"], 0)

    def test_dashboard_bindings_exist_once_in_every_configuration(self):
        required = {
            "mc_version_spinner", "edit_profile_button", "play_button",
            "create_profile_button", "explore_modpacks_button", "custom_control_button",
            "open_files_button", "quick_settings_button", "share_logs_button",
            "install_jar_button", "news_button", "discord_button", "gamepad_mapping_button",
            "launcher_tasks_card", "launcher_task_status",
        }
        for configuration, directories in CONFIGURATIONS.items():
            with self.subTest(configuration=configuration):
                elements = list(inflated_elements("fragment_launcher", directories))
                ids = element_ids(elements)
                for name in required:
                    self.assertEqual(ids[name], 1, name)
                spinner = next(item for item in elements
                               if item.get(ANDROID + "id") == "@+id/mc_version_spinner")
                self.assertEqual(spinner.tag, "com.kdt.mcgui.mcVersionSpinner")
                self.assertEqual(ids["launcher_profile_dir_value"], int(configuration == "wide_landscape"))
                self.assertEqual(ids["launcher_profile_runtime_value"], int(configuration == "wide_landscape"))

    def test_compact_landscape_prioritizes_profile_and_play(self):
        directories = CONFIGURATIONS["compact_landscape"]
        fragment = ET.parse(layout_file("fragment_launcher", directories)).getroot()
        includes = [node.attrib["layout"] for node in fragment.iter("include")]
        self.assertLess(includes.index("@layout/view_launcher_profile"),
                        includes.index("@layout/view_launcher_actions"))

        profile = ET.parse(layout_file("view_launcher_profile", directories)).getroot()
        play = next(node for node in profile.iter() if node.get(ANDROID + "id") == "@+id/play_button")
        self.assertEqual(play.get(ANDROID + "layout_height"), "match_parent")
        row = next(node for node in profile.iter() if play in list(node))
        self.assertTrue(any(node.get(ANDROID + "id") == "@+id/mc_version_spinner"
                            for node in row.iter()))

    def test_wide_actions_share_the_available_width(self):
        actions = ET.parse(layout_file("view_launcher_actions", CONFIGURATIONS["wide_landscape"])).getroot()
        self.assertEqual(actions.tag, "LinearLayout")
        self.assertEqual(len(actions), 3)
        for action in actions:
            self.assertEqual(action.get(ANDROID + "layout_width"), "0dp")
            self.assertEqual(action.get(ANDROID + "layout_weight"), "1")


if __name__ == "__main__":
    unittest.main()

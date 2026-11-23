#!/usr/bin/env python3
"""Run every ReMatter data, asset, style, crystal-fuel, and Python tool check in one command."""
from __future__ import annotations

import argparse
from pathlib import Path
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]

GENERATOR_AND_STYLE_CHECKS: tuple[tuple[str, tuple[str, ...]], ...] = (
    ("material_data", ("tools/generate_material_data.py", "--check")),
    ("workshop_materials", ("tools/generate_workshop_materials.py", "--check")),
    ("visual_assets", ("tools/generate_visual_assets.py", "--check")),
    ("family_assets", ("tools/generate_family_assets.py", "--check")),
    ("bench_shapes", ("tools/generate_bench_shapes.py", "--check")),
    ("gui_assets", ("tools/generate_gui_assets.py", "--check")),
    ("module_assets", ("tools/generate_module_assets.py", "--check")),
    ("cell_transfer_assets", ("tools/generate_cell_transfer_assets.py", "--check")),
    ("cell_models", ("tools/generate_cell_models.py", "--check")),
    ("component_assets", ("tools/generate_component_assets.py", "--check")),
    ("illustrated_icons", ("tools/generate_illustrated_icons.py", "--check")),
    ("advanced_material_icons", ("tools/generate_advanced_material_icons.py", "--check")),
    ("metallurgy_assets", ("tools/generate_metallurgy_assets.py", "--check")),
    ("device_assets", ("tools/generate_device_assets.py", "--check")),
    ("sound_assets", ("tools/generate_sound_assets.py", "--check")),
    ("logo", ("tools/generate_logo.py", "--check")),
    ("research_data", ("tools/generate_research_data.py", "--check")),
    ("primal_assets", ("tools/generate_primal_assets.py", "--check")),
    ("energy_assets", ("tools/generate_energy_assets.py", "--check")),
    ("progression_assets", ("tools/generate_progression_assets.py", "--check")),
    ("player_guidance", ("tools/generate_player_guidance.py", "--check")),
    ("crystal_fuel_audit", ("tools/audit_crystal_fuel.py", "--check")),
    ("png_chunks", ("tools/check_assets.py",)),
    ("visual_style_selftest", ("tools/check_visual_style.py", "--selftest")),
    ("visual_style", ("tools/check_visual_style.py",)),
)

UNIT_TEST_SUITES: tuple[tuple[str, tuple[str, ...]], ...] = (
    ("tools_tests", ("-B", "-m", "unittest", "discover", "-s", "tools/tests", "-p", "test_*.py")),
    ("reforging_tests", ("-B", "-m", "unittest", "discover", "-s", "integrations/reforging/tests", "-p", "test_*.py")),
)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fast", action="store_true", help="Skip unittest discovery suites and run only --check/style validators.")
    args = parser.parse_args(argv)

    steps = list(GENERATOR_AND_STYLE_CHECKS)
    if not args.fast:
        steps.extend(UNIT_TEST_SUITES)

    started = time.monotonic()
    for index, (name, cmd_args) in enumerate(steps, start=1):
        cmd = [sys.executable, *cmd_args]
        step_start = time.monotonic()
        completed = subprocess.run(cmd, cwd=ROOT, capture_output=True, text=True)
        elapsed = time.monotonic() - step_start
        if completed.returncode != 0:
            print(f"[{index}/{len(steps)}] FAIL {name} ({elapsed:.2f}s)", file=sys.stderr)
            if completed.stdout.strip():
                print(completed.stdout.rstrip(), file=sys.stderr)
            if completed.stderr.strip():
                print(completed.stderr.rstrip(), file=sys.stderr)
            return completed.returncode
        print(f"[{index}/{len(steps)}] OK   {name} ({elapsed:.2f}s)")

    total = time.monotonic() - started
    print(f"All {len(steps)} checks passed in {total:.2f}s.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

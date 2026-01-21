#!/usr/bin/env python3

## Build helper for native presets (host-compatible by default) ##

from __future__ import annotations

import os
import platform
import shutil
import argparse
from dataclasses import dataclass
from pathlib import Path
from subprocess import run, Popen, CalledProcessError, DEVNULL
import sys


@dataclass
class DockcrossConfig:
    image: str  # e.g. "manylinux2014-x64"
    docker_args: list[str] | None = None  # e.g. ["--platform=linux/amd64"]


@dataclass
class Variant:
    preset: str  # e.g. "manylinux2014-x64-release"
    cross: DockcrossConfig | None = None


# --- CLI: allow selecting debug or release preset suffix ---
parser = argparse.ArgumentParser(description="Build presets across variants (host-compatible by default; supports cross via dockcross)")
parser.add_argument("--buildType", dest="buildType", choices=["release", "debug"], default="release",
                    help="Choose preset build type suffix to use (default: release)")
parser.add_argument("--preset", dest="preset", default=None,
                    help="If set, only run the variant whose preset equals this value")
parser.add_argument("--force", dest="force", action="store_true", default=False,
                    help="Force regeneration of the dockcross wrapper even if it already exists")
parser.add_argument("--dockcross-dir", dest="dockcross_dir",
                    default=os.environ.get("DOCKCROSS_DIR", ".dockcross-wrappers"),
                    help="Directory to store dockcross wrappers (default: .dockcross-wrappers)")
parser.add_argument("--use-host-mingw", dest="use_host_mingw", action="store_true", default=False,
                    help="Prefer host MinGW-w64 toolchain over dockcross for Windows builds")
parser.add_argument("--all-platforms", dest="all_platforms", action="store_true", default=False,
                    help="Build all presets, even if not supported on this host")
parser.add_argument("--dry-run", dest="dry_run", action="store_true", default=False,
                    help="Print commands that would be run without executing them")
parser.add_argument("--verbose", dest="verbose", action="store_true", default=False,
                    help="Enable verbose output")
args = parser.parse_args()

BUILD_TYPE = args.buildType
ONLY_PRESET = args.preset
FORCE = args.force
DOCKCROSS_DIR = Path(args.dockcross_dir)
USE_HOST_MINGW = args.use_host_mingw
ALL_PLATFORMS = args.all_platforms
DRY_RUN = args.dry_run
VERBOSE = args.verbose


def sh(cmd: list[str], **kw) -> None:
    """Run a command (blocking). In dry-run mode this only prints the command.

    This helper prints the command when VERBOSE is True.
    """
    if VERBOSE or DRY_RUN:
        print("[CMD]", " ".join(cmd))
    if DRY_RUN:
        return
    run(cmd, check=True, **kw)


def sh_stream(cmd: list[str], **kw) -> None:
    """Run a command streaming to the terminal. In dry-run mode this prints the command and doesn't run it."""
    if VERBOSE or DRY_RUN:
        print("[STREAM-CMD]", " ".join(cmd))
    if DRY_RUN:
        return
    with Popen(cmd, **kw) as p:
        rc = p.wait()
        if rc != 0:
            raise CalledProcessError(rc, cmd)


def ensure_docker_running() -> None:
    """Ensure Docker is installed and running before attempting dockcross builds."""
    if DRY_RUN:
        return
    try:
        run(["docker", "info"], check=True, stdout=DEVNULL, stderr=DEVNULL)
    except FileNotFoundError:
        print("❌ Docker not found. Install Docker Desktop or ensure `docker` is on PATH.")
        sys.exit(1)
    except CalledProcessError:
        print("❌ Docker is not running. Start Docker Desktop and retry.")
        sys.exit(1)


def detect_host_os() -> str:
    sysname = platform.system().lower()
    if sysname == "darwin":
        return "macos"
    if sysname.startswith("windows"):
        return "windows"
    return "linux"


def is_mingw_preset(preset: str) -> bool:
    return preset.startswith("mingw-w64-")


def is_manylinux_preset(preset: str) -> bool:
    return preset.startswith("manylinux")


def is_macos_preset(preset: str) -> bool:
    return preset.startswith("macos-")


def is_ios_preset(preset: str) -> bool:
    return preset.startswith("ios-")


def is_windows_preset(preset: str) -> bool:
    return preset.startswith("windows-")



def find_mingw_prefix(triplet: str) -> Path | None:
    env_prefix = os.environ.get("MINGW_PREFIX")
    if env_prefix:
        return Path(env_prefix)

    for exe in (f"{triplet}-gcc", f"{triplet}-g++", f"{triplet}-windres"):
        resolved = shutil.which(exe)
        if resolved:
            return Path(resolved).resolve().parent.parent
    return None


def host_compatible(variant: Variant, host_os: str) -> tuple[bool, str | None]:
    preset = variant.preset
    if is_macos_preset(preset) or is_ios_preset(preset):
        if host_os != "macos":
            return False, "requires macOS host"
    if is_windows_preset(preset):
        if host_os != "windows":
            return False, "requires Windows host"
    if is_mingw_preset(preset):
        if not USE_HOST_MINGW:
            return False, "host mingw not enabled"
        if not find_mingw_prefix("x86_64-w64-mingw32"):
            return False, "mingw-w64 toolchain not found"
    return True, None


variants: list[Variant] = [
    Variant(
        preset=f"mingw-w64-cross-macos-{BUILD_TYPE}"
    ),
    Variant(
        preset=f"macos-arm64-{BUILD_TYPE}"
    ),
    Variant(
        preset=f"macos-x86_64-{BUILD_TYPE}"
    ),

    Variant(
        preset=f"manylinux2014-x64-{BUILD_TYPE}",
        cross=DockcrossConfig(
            image="manylinux2014-x64",
            docker_args=["--platform=linux/amd64"]
        )
    ),
    Variant(
        preset=f"manylinux2014-aarch64-{BUILD_TYPE}",
        cross=DockcrossConfig(
            image="manylinux2014-aarch64",
            docker_args=["--platform=linux/amd64"]
        )
    ),
]

# Filter to host-compatible presets by default.
if not ALL_PLATFORMS:
    host_os = detect_host_os()
    print(f"Host OS: {host_os} (host-compatible presets enabled; use --all-platforms to override)")
    filtered: list[Variant] = []
    skipped: list[tuple[str, str]] = []
    for v in variants:
        ok, reason = host_compatible(v, host_os)
        if ok:
            filtered.append(v)
        else:
            skipped.append((v.preset, reason or "unsupported"))

    if skipped:
        print(f"Skipping {len(skipped)} preset(s) not supported on this host:")
        for preset, reason in skipped:
            print(f" - {preset} ({reason})")
    else:
        print("All presets are host-compatible on this machine.")

    variants = filtered

# If --preset was provided, filter the variants; exit with non-zero if nothing matches
if ONLY_PRESET:
    filtered = [v for v in variants if v.preset == ONLY_PRESET]
    if not filtered:
        print(f"No variants match the requested preset: {ONLY_PRESET}")
        print("Available presets:")
        for v in variants:
            print(" -", v.preset)
        sys.exit(2)
    variants = filtered

# Fail fast if any selected variant requires dockcross and Docker isn't available.
if any(v.cross is not None for v in variants):
    ensure_docker_running()

cwd = Path(__file__).resolve().parent
if not DOCKCROSS_DIR.is_absolute():
    DOCKCROSS_DIR = (cwd / DOCKCROSS_DIR)

legacy_dir = cwd / ".dockcross"
if legacy_dir.exists() and legacy_dir.is_dir() and legacy_dir != DOCKCROSS_DIR:
    if DOCKCROSS_DIR.exists():
        print(f"❌ Found legacy dockcross dir at {legacy_dir} and target dir at {DOCKCROSS_DIR}.")
        print("Remove or rename the legacy directory to avoid dockcross entrypoint errors.")
        sys.exit(1)
    if DRY_RUN:
        print(f"[DRY-RUN] Would rename legacy dockcross dir: {legacy_dir} -> {DOCKCROSS_DIR}")
    else:
        print(f"Renaming legacy dockcross dir: {legacy_dir} -> {DOCKCROSS_DIR}")
        legacy_dir.rename(DOCKCROSS_DIR)


def ensure_dockcross_wrapper(config: DockcrossConfig) -> Path:
    """Ensure a dockcross wrapper script exists for the image and return its path."""
    if not DOCKCROSS_DIR.exists() and not DRY_RUN:
        DOCKCROSS_DIR.mkdir(parents=True, exist_ok=True)
    wrapper = DOCKCROSS_DIR / f"dockcross-{config.image}"

    need_generate = FORCE or (not wrapper.exists())
    if not need_generate:
        if VERBOSE:
            print(f"Using existing dockcross wrapper: {wrapper}")
        return wrapper

    gen_cmd = ["docker", "run", "--rm"]
    if config.docker_args:
        gen_cmd += config.docker_args
    gen_cmd.append(f"dockcross/{config.image}")

    if DRY_RUN:
        print("[DRY-RUN] Would generate dockcross wrapper with:", " ".join(gen_cmd))
        return wrapper

    try:
        if VERBOSE:
            print("Generating dockcross wrapper via:", " ".join(gen_cmd))
        proc = run(gen_cmd, check=True, text=True, capture_output=True)
    except CalledProcessError as e:
        print("❌ Command failed:", e.cmd)
        print("Exit code:", e.returncode)
        print("---- STDOUT ----")
        print(e.stdout or "(empty)")
        print("---- STDERR ----")
        print(e.stderr or "(empty)")
        raise

    wrapper.write_text(proc.stdout)
    sh(["chmod", "+x", str(wrapper)])
    return wrapper

for v in variants:
    print("=>", v.preset)

    # Prepare defaults so static analysis doesn't warn about undefined names
    wrapper: Path | None = None
    baked_args: list[str] = []

    if v.cross is not None:
        # Determine wrapper path
        wrapper = ensure_dockcross_wrapper(v.cross)

    # Optional docker run args for the wrapper (e.g., --platform=…)
    if v.cross is not None and v.cross.docker_args:
        baked_args = ["--args", " ".join(v.cross.docker_args)]  # wrapper flag, not docker

    env = os.environ.copy()
    if is_mingw_preset(v.preset):
        mingw_prefix = find_mingw_prefix("x86_64-w64-mingw32")
        if mingw_prefix:
            env.setdefault("MINGW_PREFIX", str(mingw_prefix))

    try:
        # 2) Configure
        if v.cross is not None:
            # Use the generated dockcross wrapper when cross configuration exists
            cmd = [str(wrapper)] + baked_args + ["cmake", "--preset", v.preset]
            sh_stream(cmd, cwd=cwd, text=True, env=env)
        else:
            # Call host cmake directly
            sh_stream(["cmake", "--preset", v.preset], cwd=cwd, text=True, env=env)

        # 3) Build
        if v.cross is not None:
            cmd = [str(wrapper)] + baked_args + ["cmake", "--build", "--preset", v.preset]
            sh_stream(cmd, cwd=cwd, text=True, env=env)
        else:
            sh_stream(["cmake", "--build", "--preset", v.preset], cwd=cwd, text=True, env=env)
    except CalledProcessError as e:
        print("❌ Command failed:", e.cmd)
        print("Exit code:", e.returncode)
        print("---- STDOUT ----")
        # CalledProcessError may not have stdout attribute in all contexts, guard it
        try:
            print(e.stdout or "(empty)")
        except Exception:
            pass
        print("---- STDERR ----")
        try:
            print(e.stderr or "(empty)")
        except Exception:
            pass
        raise

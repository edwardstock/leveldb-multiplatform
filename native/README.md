# Native build notes

- Prebuilt JNI and Kotlin/Native libs are built via CMake presets.
- Use `./build_prebuilt.sh [release|debug] [preset]` to build all variants or a single preset.
- Dockcross wrappers are generated into `.dockcross-wrappers/` on first run (requires Docker).

## Host-compatible builds (default)

`make_libs.py` filters presets to those compatible with the current host by default.
It will print which presets are skipped and why. Use `--all-platforms` to disable filtering.

Pass `--use-host-mingw` to enable a local MinGW-w64 toolchain (set `MINGW_PREFIX` if it is not on PATH).

## Script help

See `./build_prebuilt.sh --help` for wrapper usage.
See `./make_libs.py --help` for all options and flags.

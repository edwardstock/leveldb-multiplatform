# Native build notes

- Prebuilt JNI and Kotlin/Native libs are built via CMake presets.
- Use `./build_prebuilt.sh [release|debug] [preset]` to build all variants or a single preset.
- Dockcross wrappers are generated into `.dockcross-wrappers/` on first run (requires Docker).

## Host-compatible builds (default)

What you can build depending on where you run the script (host-compatible mode, the default):

- On macOS: macOS + iOS; Linux (manylinux via dockcross) only when `--all-platforms` is set; MinGW builds are allowed if a host MinGW-w64 toolchain is
  installed.
- On Linux: Linux presets (manylinux) via dockcross; macOS/iOS are skipped; MinGW builds are allowed if a host MinGW-w64 toolchain is installed.
- On Windows: Windows and MinGW (host MinGW-w64 required); macOS/iOS/Linux are skipped. Kotlin/Native supports only MinGW (not MSVC).

`--all-platforms` brings dockcross Linux targets to non-Linux hosts. It does **not** bypass macOS/iOS host requirements.

Note on MinGW: the MinGW preset assumes a host MinGW-w64 toolchain. Install MinGW-w64 and/or set `MINGW_PREFIX` if the triplet is not on PATH.
`--use-host-mingw` controls whether the preset is considered; it defaults to true.

Key flags:

- `--buildType [release|debug]` (default: release)
- `--preset <cmake-preset>` to build only one preset
- `--all-platforms` to include dockcross cross targets
- `--use-host-mingw` (default: true) to use local MinGW-w64; set `MINGW_PREFIX` if not on PATH
- `--dockcross-dir` to control wrapper location (default: `.dockcross-wrappers`)
- `--force` to regenerate dockcross wrapper
- `--dry-run` / `--verbose` for diagnostics

## Script help

See `./build_prebuilt.sh --help` for wrapper usage.
See `./make_libs.py --help` for all options and flags.

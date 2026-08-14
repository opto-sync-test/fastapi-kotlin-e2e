from __future__ import annotations

import platform
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CORE = ROOT / "vendor/opto-sync-clients/syncer.c/core"
OUTPUT_DIR = ROOT / ".build"


def build() -> Path:
    OUTPUT_DIR.mkdir(exist_ok=True)
    system = platform.system()
    if system == "Darwin":
        output = OUTPUT_DIR / "libsyncer.dylib"
        linker = "-dynamiclib"
    elif system == "Linux":
        output = OUTPUT_DIR / "libsyncer.so"
        linker = "-shared"
    else:
        raise RuntimeError(f"unsupported platform: {system}")

    subprocess.run(
        [
            "cc",
            "-std=c11",
            "-O2",
            "-fPIC",
            linker,
            f"-I{CORE / 'include'}",
            str(CORE / "src/syncer.c"),
            str(CORE / "src/yyjson.c"),
            "-o",
            str(output),
        ],
        check=True,
    )
    return output


if __name__ == "__main__":
    print(build())

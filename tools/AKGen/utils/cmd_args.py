import argparse
from dataclasses import dataclass
from pathlib import Path

from utils.config import RootConfig, parse_config
from utils.file import *

@dataclass(frozen=True, slots=True)
class CliArgs:
    config: Path
    debug: bool
    verbose: bool

def parse_args(argv: list[str] | None = None) -> CliArgs:
    parser = argparse.ArgumentParser(
        prog="arkts-ffi-kt",
        description="Generate Kotlin wrappers from ArkTS declarations.",
    )


    parser.add_argument(
        "--config",
        type=Path,
        default=Path("test/config.json"),
        help="Path to config.json (default: config.json).",
    )
    parser.add_argument(
        "--debug",
        action="store_true",
        default=False,
        help="Enable debug output.",
    )

    parser.add_argument(
        "--verbose",
        action="store_true",
        default=False,
        help="Enable debug output.",
    )

    ns = parser.parse_args(argv)

    config: Path = ns.config.expanduser().resolve()
    
    if not config.exists() or not config.is_file():
        raise SystemExit(f"--config must be an existing file: {config}")

    return CliArgs(config=config, debug=ns.debug, verbose=ns.verbose)


def print_config(cfg: RootConfig) -> None:
    # print(f"kotlinOutput: {cfg.common.kotlin_output}")
    # print("imports:")
    for imp in cfg.common.imports:
        print(f"  - {imp}")

    print("packages:")
    for pkg in cfg.packages:
        print(f"  - {pkg.name}")
        for d in pkg.arkts_source_dir:
            print(f"      arktsSourceDir: {d}")

if __name__ == "__main__":
    args = parse_args()
    cfg = parse_config(args.config)
    print_config(cfg)
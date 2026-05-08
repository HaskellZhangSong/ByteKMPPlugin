from pathlib import Path

KOTLIN_FILE_EXTS = {".kt"}
ARKTS_FILE_EXTS = {".ets", ".ts"}


def get_files(root: str | Path, exts: set[str] | None = None) -> list[Path]:
    """Recursively collect ArkTS/TS files under `root`.

    - If `root` is a file: returns [root] if its suffix is in `exts`, else []
    - If `root` is a directory: returns all matching files under it (recursively)

    Returned paths are absolute (resolved), sorted, and unique.
    """

    exts = exts or ARKTS_FILE_EXTS

    root_path = Path(root).expanduser().resolve()

    if root_path.is_file():
        return [root_path] if root_path.suffix in exts else []

    if not root_path.is_dir():
        raise FileNotFoundError(f"Path not found or not a directory/file: {root_path}")

    files = [
        p.resolve()
        for p in root_path.rglob("*")
        if p.is_file() and p.suffix in exts
    ]

    return sorted(set(files))
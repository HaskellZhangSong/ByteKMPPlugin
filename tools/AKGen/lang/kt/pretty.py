from __future__ import annotations

from pathlib import Path
import sys

# Allow running as a script: `poetry run python lang/kt/pretty.py`
_REPO_ROOT = Path(__file__).resolve().parents[2]
if str(_REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(_REPO_ROOT))

from typing import Iterable

from lang.kt.kt_decls import (
    ClassDecl,
    Decl,
    Annotations,
    IntegerValue,
    KtValue,
    PropertyDecl,
    StringValue,
)
from lang.kt.kt_types import Type


def _indent(level: int) -> str:
    return "  " * level


def _fmt_decorators(decorators: list[Annotations] | None, pre: str) -> str:
    if not decorators:
        return ""

    def fmt_arg(a: KtValue) -> str:
        if isinstance(a, StringValue):
            return repr(a.string)
        if isinstance(a, IntegerValue):
            return str(a.number)
        return repr(a)

    lines: list[str] = []
    for d in decorators:
        if d.args is None:
            lines.append(f"{pre}@{d.name}")
        else:
            args_s = ", ".join(fmt_arg(a) for a in d.args)
            lines.append(f"{pre}@{d.name}({args_s})")
    return "\n".join(lines)


def pretty_decl(decl: Decl, *, indent: int = 0) -> str:
    """Pretty print a Kotlin AST Decl.

    Output is intended for debugging and tests.
    """

    pre = _indent(indent)

    if isinstance(decl, ClassDecl):
        deco_block = _fmt_decorators(decl.annotations, pre)
        head = f"{pre}class {decl.name}"

        if not decl.members:
            return f"{deco_block + '\n' if deco_block else ''}{head}".rstrip("\n")

        body = "\n".join(pretty_decl(m, indent=indent + 1) for m in decl.members)
        out = []
        if deco_block:
            out.append(deco_block)
        out.append(f"{head} {{")
        out.append(body)
        out.append(f"{pre}}}")
        return "\n".join(out)

    if isinstance(decl, PropertyDecl):
        deco_block = _fmt_decorators(decl.annotations, pre)
        mod = ' '.join(map(lambda x : x.name.lower(), decl.modifier))  # var/val
        t: Type = decl.type
        line = f"{pre}{mod} {decl.name}: {t}"
        return f"{deco_block + '\n' if deco_block else ''}{line}".rstrip("\n")

    # Fallback for unknown Decl variants.
    return f"{pre}{decl!r}"


def pretty_program(decls: Iterable[Decl]) -> str:
    return "\n".join(pretty_decl(d) for d in decls)


if __name__ == "__main__":
    from lang.kt.kt_parser import parse_kotlin_source

    kt_file = _REPO_ROOT / "test" / "cases" / "kt" / "class2.kt"

    code = kt_file.read_text(encoding="utf-8")
    ast = parse_kotlin_source(code)
    print(pretty_program(ast))

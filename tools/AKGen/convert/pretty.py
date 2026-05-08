from convert.types import *

def pretty_print_kt_node(node: KtNode) -> str:
    return _pretty_print_kt_node(node)


def multi_line_str_to_kt_node(s: str, init_indent: int = 0) -> KtNode:
    """Convert an indented multi-line string into a nested KtNode.

    Behavior:
    - Ignores leading/trailing blank lines.
    - Strips the common leading indentation from all non-empty lines.
    - Infers indentation levels from leading spaces:
        * Any indentation increase compared to the previous non-empty line counts
          as **exactly one** deeper nesting level (even if the indent jumps by 2x).
        * Dedent must match a previously seen indentation width at some earlier
          depth; otherwise it falls back to root.

    Args:
        s: Multi-line string.
        init_indent: Wrap the final result into this many outer list levels.
    """
    if init_indent < 0:
        raise ValueError(f"init_indent must be >= 0, got {init_indent!r}")

    # Normalize newlines and drop leading/trailing empty lines.
    lines = s.replace("\r\n", "\n").replace("\r", "\n").split("\n")
    while lines and lines[0].strip() == "":
        lines.pop(0)
    while lines and lines[-1].strip() == "":
        lines.pop()

    if not lines:
        wrapped: KtNode = []
        for _ in range(init_indent):
            wrapped = [wrapped]  # type: ignore[list-item]
        return wrapped

    non_empty = [ln for ln in lines if ln.strip() != ""]
    min_leading = min(len(ln) - len(ln.lstrip(" ")) for ln in non_empty)

    def normalized(line: str) -> str:
        if line.strip() == "":
            return ""
        return line[min_leading:]

    norm_lines = [normalized(ln) for ln in lines]

    root: list[KtNode] = []
    stack: list[list[KtNode]] = [root]

    # Exact leading-space counts seen at each depth.
    indent_levels: list[int] = [0]

    def ensure_level(level: int) -> None:
        while len(stack) > level + 1:
            stack.pop()
        while len(stack) < level + 1:
            new_list: list[KtNode] = []
            stack[-1].append(new_list)
            stack.append(new_list)

    last_depth = 0

    for ln in norm_lines:
        if ln.strip() == "":
            continue

        leading = len(ln) - len(ln.lstrip(" "))

        if leading > indent_levels[last_depth]:
            # Any increase counts as exactly one nesting level.
            depth = last_depth + 1
            if len(indent_levels) <= depth:
                indent_levels.append(leading)
            else:
                indent_levels[depth] = leading
        elif leading == indent_levels[last_depth]:
            depth = last_depth
        else:
            # Dedent: must exactly match a previously seen indent width;
            # otherwise treat as root-level.
            depth = 0
            for d in range(min(last_depth, len(indent_levels) - 1), -1, -1):
                if indent_levels[d] == leading:
                    depth = d
                    break

        ensure_level(depth)
        stack[-1].append(ln.lstrip(" "))
        last_depth = depth

    wrapped: KtNode = root
    for _ in range(init_indent):
        wrapped = [wrapped]  # type: ignore[list-item]
    return wrapped


def _pretty_print_kt_node(node: KtNode, indent: int = 0) -> str:
    if isinstance(node, str):
        return (" " * indent) + node

    # List[KtNode]
    lines: list[str] = []
    for child in node:
        if isinstance(child, list):
            lines.append(_pretty_print_kt_node(child, indent + 4))
        else:
            lines.append(_pretty_print_kt_node(child, indent))

    return "\n".join(lines)

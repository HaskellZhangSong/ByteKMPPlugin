from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Iterable
import logging

from tree_sitter import Language, Node, Parser, Tree
import tree_sitter_kotlin as kt

from lang.kt.kt_decls import (
    Annotations,
    ClassDecl,
    Decl,
    PropertyDecl,
    PropertyModifier,
    SourceFile,
    StringValue,
)
from lang.kt.type_parser import parse_kt_type

BASE_DIR = Path(__file__).parent.parent.parent


class KotlinParseError(ValueError):
    pass


def _text(source: bytes, node: Node) -> str:
    return source[node.start_byte : node.end_byte].decode("utf-8", errors="replace")


def _find_child(node: Node, t: str) -> Node | None:
    for c in node.children:
        if c.type == t:
            return c
    return None


def _find_children(node: Node, t: str) -> list[Node]:
    return [c for c in node.children if c.type == t]


def _iter_descendants(node: Node) -> Iterable[Node]:
    stack = list(reversed(node.children))
    while stack:
        n = stack.pop()
        yield n
        stack.extend(reversed(n.children))


def _node_loc(node: Node) -> str:
    line, col = node.start_point
    return f"line {line + 1}, col {col + 1}"


def _err(node: Node, message: str, path: Path | None = None) -> KotlinParseError:
    prefix = f"{path}: " if path is not None else ""
    return KotlinParseError(f"{prefix}{_node_loc(node)}: {message}")


def _warn(node: Node, message: str, path: Path | None = None) -> None:
    warning = _err(node, message, path)
    logging.warning(str(warning))


def _parse_string_literal(source: bytes, node: Node, path: Path | None = None) -> str:
    # In this grammar string_literal includes quotes; string_content holds inner.
    if node.type != "string_literal":
        raise _err(node, f"Expected string_literal, got {node.type}", path)
    content = _find_child(node, "string_content")
    return _text(source, content) if content is not None else ""


def _parse_decorator_from_annotation(source: bytes, ann: Node, path: Path | None = None) -> Annotations:
    # annotation: '@' (user_type | constructor_invocation) with optional use-site targets (e.g., field:)
    if ann.type != "annotation":
        raise _err(ann, f"Expected annotation, got {ann.type}", path)

    ctor = _find_child(ann, "constructor_invocation") or _find_descendant(ann, "constructor_invocation")
    if ctor is None:
        user_type = _find_child(ann, "user_type") or _find_descendant(ann, "user_type")
        if user_type is None:
            raise _err(ann, "annotation missing user_type", path)
        return Annotations(_text(source, user_type), None)

    user_type = _find_child(ctor, "user_type") or _find_descendant(ctor, "user_type")
    if user_type is None:
        raise _err(ctor, "constructor_invocation missing user_type", path)

    name = _text(source, user_type)
    value_args = _find_child(ctor, "value_arguments") or _find_descendant(ctor, "value_arguments")
    if value_args is None:
        return Annotations(name, None)

    args: list[StringValue] = []
    for arg in _find_children(value_args, "value_argument"):
        lit = _find_child(arg, "string_literal")
        if lit is None:
            args.append(StringValue(_text(source, arg)))
        else:
            args.append(StringValue(_parse_string_literal(source, lit, path)))
    return Annotations(name, args)


def _parse_annotations(source: bytes, modifiers: Node | None, path: Path | None = None) -> list[Annotations]:
    if modifiers is None:
        return []
    annos: list[Annotations] = []
    for ann in _find_children(modifiers, "annotation"):
        annos.append(_parse_decorator_from_annotation(source, ann, path))
    return annos

def _infer_literal_type(source: bytes, d: Node) -> str | None:
    match d.type:
        case "integer_literal" | "number_literal" | "float_literal":
            lit = _text(source, d)
            compact = lit.replace("_", "")
            lower = compact.lower()
            is_long = lower.endswith("l")
            is_float = lower.endswith("f")
            if is_long or is_float:
                lower = lower[:-1]
            if lower.startswith("0x") or lower.startswith("0b"):
                return "Long" if is_long else "Int"
            if "e" in lower or "." in lower:
                return "Float" if is_float else "Double"
            return "Long" if is_long else "Int"
        case "string_literal":
            return "String"
        case "boolean_literal":
            return "Boolean"
        case "identifier":
            ident = _text(source, d)
            return "Boolean" if ident in {"true", "false", "True", "False"} else None
        case "unary_expression":
            for c in d.children:
                if c.is_named:
                    return _infer_literal_type(source, c)
            return None
        case _:
            return None


def _infer_type_from_initializer(source: bytes, node: Node) -> str | None:
    init = None
    children = node.children
    for i, c in enumerate(children):
        if c.type == "=":
            for j in range(i + 1, len(children)):
                if children[j].is_named:
                    init = children[j]
                    break
            break
    if init is None:
        return None
    return _infer_literal_type(source, init)


def _parse_property_modifiers(
    source: bytes,
    modifiers: Node | None,
    mutability_token: Node,
) -> list[PropertyModifier]:
    def parse_modifier_text(modifier_text: str) -> PropertyModifier | None:
        normalized = modifier_text.strip()
        for property_modifier in PropertyModifier:
            if property_modifier.value == normalized:
                return property_modifier
        return None

    parsed: list[PropertyModifier] = []

    if modifiers is not None:
        for visibility in _find_children(modifiers, "visibility_modifier"):
            parsed_visibility = parse_modifier_text(_text(source, visibility))
            if parsed_visibility is not None and parsed_visibility not in parsed:
                parsed.append(parsed_visibility)
        for inheritance in _find_children(modifiers, "inheritance_modifier"):
            parsed_inheritance = parse_modifier_text(_text(source, inheritance))
            if parsed_inheritance is not None and parsed_inheritance not in parsed:
                parsed.append(parsed_inheritance)

    parsed_mutability = parse_modifier_text(mutability_token.type)
    if parsed_mutability is not None and parsed_mutability not in parsed:
        parsed.append(parsed_mutability)

    return parsed


def _parse_property_declaration(source: bytes, node: Node, path: Path | None = None) -> PropertyDecl | None:
    # property_declaration: (modifiers)? (val|var) variable_declaration ...
    if node.type != "property_declaration":
        raise _err(node, f"Expected property_declaration, got {node.type}", path)

    modifiers = _find_child(node, "modifiers")
    annos = _parse_annotations(source, modifiers, path)

    mod_tok = _find_child(node, "val") or _find_child(node, "var")
    if mod_tok is None:
        raise _err(node, "property_declaration missing val/var", path)
    modifier = _parse_property_modifiers(source, modifiers, mod_tok)

    var_decl = _find_child(node, "variable_declaration")
    if var_decl is None:
        raise _err(node, "property_declaration missing variable_declaration", path)

    name_node = _find_child(var_decl, "identifier")
    if name_node is None:
        raise _err(var_decl, "variable_declaration missing identifier", path)
    name = _text(source, name_node)

    # Type can be nullable_type, user_type, function_type, etc.
    # We'll parse it using our string-based Kotlin type parser.
    type_node = None
    for c in var_decl.children:
        if c.type in {"user_type", "nullable_type", "function_type", "parenthesized_type", "dynamic_type"}:
            type_node = c
            break
    if type_node is None:
        inferred = _infer_type_from_initializer(source, node)
        if inferred is None:
            _warn(var_decl, f"Property {name} missing type", path)
            return None
        try:
            t = parse_kt_type(inferred)
        except Exception as exc:
            _warn(var_decl, f"Invalid inferred type for {name}: {exc}", path)
            return None
    else:
        # tree-sitter-kotlin may parse qualified name in a wrong with ERROR node.
        error_text = ''
        has_error = False
        after_colon = False
        for i in var_decl.children:
            if after_colon and i.type == 'ERROR':
                error_text = i.text
                has_error = True
                break
            if i.type == ':':
                after_colon = True
        if has_error:
            raw_type = (error_text + type_node.text).decode("utf-8")
        else:
            raw_type = _text(source, type_node)
        try:
            t = parse_kt_type(raw_type)
        except Exception as exc:
            _warn(type_node, f"Invalid type for {name}: {exc}", path)
            return None

    return PropertyDecl(modifier=modifier, name=name, type=t, annotations=annos or None)


def _find_descendant(node: Node, t: str) -> Node | None:
    for d in _iter_descendants(node):
        if d.type == t:
            return d
    return None


def _class_member_nodes(container: Node) -> list[Node]:
    scope = container
    if container.type == "annotated_lambda":
        scope = _find_child(container, "lambda_literal") or container
    elif container.type == "call_suffix":
        scope = _find_descendant(container, "lambda_literal") or container

    members: list[Node] = []
    for c in scope.children:
        if c.type in {"property_declaration", "class_declaration"}:
            members.append(c)
        elif c.type == "annotated_expression":
            prop = _find_child(c, "property_declaration")
            if prop is not None:
                members.append(prop)
            else:
                members.append(c)
    return members


def _parse_class_members(
    source: bytes,
    container: Node,
    path: Path | None = None,
    parent_class_path: list[str] | None = None,
) -> list[Decl]:
    members: list[Decl] = []
    inherited_parent_path = list(parent_class_path or [])
    for member_node in _class_member_nodes(container):
        if member_node.type == "property_declaration":
            member = _parse_property_declaration(source, member_node, path)
        else:
            member = _parse_class_like(source, member_node, path, parent_class_path=inherited_parent_path)
        if member is not None:
            members.append(member)
    return members


def _parse_class_declaration(
    source: bytes,
    node: Node,
    path: Path | None = None,
    parent_class_path: list[str] | None = None,
) -> ClassDecl:
    if node.type != "class_declaration":
        raise _err(node, f"Expected class_declaration, got {node.type}", path)

    name_node = _find_child(node, "identifier")
    if name_node is None:
        raise _err(node, "class_declaration missing identifier", path)
    class_name = _text(source, name_node)
    inherited_parent_path = list(parent_class_path or [])

    modifiers = _find_child(node, "modifiers")
    annos = _parse_annotations(source, modifiers, path)

    body = _find_child(node, "class_body")
    child_parent_path = [*inherited_parent_path, class_name]
    members = _parse_class_members(source, body, path, parent_class_path=child_parent_path) if body is not None else []

    return ClassDecl(
        name=class_name,
        members=members or None,
        annotations=annos or None,
        parent_class=inherited_parent_path,
    )


def _unwrap_annotated_expression(node: Node) -> tuple[list[Node], Node | None]:
    ann_nodes: list[Node] = []
    current = node

    while current is not None and current.type == "annotated_expression":
        ann = _find_child(current, "annotation")
        if ann is not None:
            ann_nodes.append(ann)

        expr = None
        for c in current.children:
            if c.is_named and c.type != "annotation":
                expr = c
                break

        if expr is None or expr.type != "annotated_expression":
            return ann_nodes, expr
        current = expr

    return ann_nodes, current


def _parse_class_from_infix_expression(
    source: bytes,
    infix: Node,
    annotations: list[Annotations],
    path: Path | None = None,
    parent_class_path: list[str] | None = None,
) -> ClassDecl | None:
    if infix.type != "infix_expression":
        return None

    has_class_keyword = any(c.type == "identifier" and _text(source, c) == "class" for c in infix.children)
    if not has_class_keyword:
        return None

    callish = _find_child(infix, "call_expression")
    if callish is None:
        return None

    name_node = _find_child(callish, "identifier")
    if name_node is None:
        return None
    class_name = _text(source, name_node)
    inherited_parent_path = list(parent_class_path or [])

    container = (
        _find_child(callish, "annotated_lambda")
        or _find_child(callish, "lambda_literal")
        or _find_child(callish, "call_suffix")
    )
    if container is None:
        return None

    child_parent_path = [*inherited_parent_path, class_name]
    members = _parse_class_members(source, container, path, parent_class_path=child_parent_path)

    return ClassDecl(
        name=class_name,
        members=members or None,
        annotations=annotations or None,
        parent_class=inherited_parent_path,
    )


def _parse_class_like(
    source: bytes,
    node: Node,
    path: Path | None = None,
    parent_class_path: list[str] | None = None,
) -> ClassDecl | None:
    if node.type == "class_declaration":
        return _parse_class_declaration(source, node, path, parent_class_path=parent_class_path)
    if node.type != "annotated_expression":
        return None

    ann_nodes, expr = _unwrap_annotated_expression(node)
    annos = [_parse_decorator_from_annotation(source, ann, path) for ann in ann_nodes]

    if expr is None:
        return None
    if expr.type == "class_declaration":
        parsed = _parse_class_declaration(source, expr, path, parent_class_path=parent_class_path)
        if annos:
            parsed.annotations = (annos + parsed.annotations) if parsed.annotations is not None else annos
        return parsed
    if expr.type == "infix_expression":
        return _parse_class_from_infix_expression(source, expr, annos, path, parent_class_path=parent_class_path)
    return None


def _flatten_class_declarations(class_decl: ClassDecl) -> list[ClassDecl]:
    flattened: list[ClassDecl] = [class_decl]
    if not class_decl.members:
        return flattened

    non_class_members: list[Decl] = []
    for member in class_decl.members:
        if isinstance(member, ClassDecl):
            flattened.extend(_flatten_class_declarations(member))
        else:
            non_class_members.append(member)

    class_decl.members = non_class_members or None
    return flattened


_FULLWIDTH_MAP = str.maketrans({
    "，": ",",
    "：": ":",
    "（": "(",
    "）": ")",
    "｛": "{",
    "｝": "}",
})


def _normalize_source(source_code: str) -> str:
    return source_code.translate(_FULLWIDTH_MAP)


def _parse_package(source: bytes, root: Node) -> str | None:
    """Parse `package a.b.c;` from the Kotlin CST if present."""

    pkg = _find_child(root, "package_header")
    if pkg is None:
        return None

    qid = _find_child(pkg, "qualified_identifier")
    if qid is not None:
        parts = [c for c in qid.children if c.type == "identifier"]
        if parts:
            return ".".join(_text(source, p) for p in parts)

    # Fallback: parse from raw text.
    raw = _text(source, pkg).strip()
    if raw.startswith("package"):
        raw = raw[len("package"):].strip()
    if raw.endswith(";"):
        raw = raw[:-1].strip()
    return raw or None


@dataclass
class KotlinAstParser:
    source_code: str
    source_path: Path | None = None

    def __post_init__(self) -> None:
        language = Language(kt.language())
        self._parser = Parser(language)
        self._source = _normalize_source(self.source_code).encode("utf-8")
        self._tree: Tree = self._parser.parse(self._source)

    def parse(self) -> SourceFile:
        root = self._tree.root_node
        package_name = _parse_package(self._source, root)

        decls: list[Decl] = []

        for n in root.children:
            if not n.is_named:
                continue
            if n.type in {"class_declaration", "annotated_expression"}:
                parsed = _parse_class_like(self._source, n, self.source_path)
                if parsed is not None:
                    decls.extend(_flatten_class_declarations(parsed))

        return SourceFile(package=package_name, declarations=decls)


def parse_kotlin_source_file(source_code: str, path: Path | None = None) -> SourceFile:
    return KotlinAstParser(source_code, source_path=path).parse()


def parse_kotlin_source(source_code: str, path: Path | None = None) -> list[Decl]:
    # Backwards-compatible API: return only declarations.
    return parse_kotlin_source_file(source_code, path).declarations


def parse_kotlin_file(path: str | Path, encoding: str = "utf-8") -> SourceFile:
    """Parse a Kotlin file into declarations (legacy API)."""

    p = Path(path).expanduser().resolve()
    code = p.read_text(encoding=encoding)
    return parse_kotlin_source_file(code, p)



if __name__ == "__main__":
    file = BASE_DIR / "test" / "cases" / "kt" / "class14.kt"
    sf = parse_kotlin_file(file)
    print("abc")
    # cases_dir = BASE_DIR / "test" / "cases" / "kt"
    # for file_path in sorted(cases_dir.rglob("*.kt")):
    #     sf = parse_kotlin_file(file_path)
    #     print(file_path.name)
    #     print(sf)

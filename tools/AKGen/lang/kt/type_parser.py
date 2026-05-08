from __future__ import annotations

from dataclasses import dataclass

from lang.kt.kt_types import (
    AppType,
    ArrayType,
    NullableType,
    PrimType,
    PrimArrayType,
    PrimArrayTypeEnum,
    QualifiedType,
    RefType,
    Type,
    _PRIM_BY_NAME,
)

class KotlinTypeParseError(ValueError):
    pass


# Tokenization
@dataclass(frozen=True, slots=True)
class _Token:
    kind: str
    text: str
    pos: int

_SINGLE_CHAR = {
    ".": "DOT",
    "<": "LT",
    ">": "GT",
    ",": "COMMA",
    "?": "QMARK",
    "[": "LBRACK",
    "]": "RBRACK",
}


def _tokenize(s: str) -> list[_Token]:
    tokens: list[_Token] = []
    i = 0
    n = len(s)
    while i < n:
        c = s[i]
        if c.isspace():
            i += 1
            continue
        if c in _SINGLE_CHAR:
            tokens.append(_Token(_SINGLE_CHAR[c], c, i))
            i += 1
            continue
        if c.isalpha() or c == "_":
            j = i + 1
            while j < n and (s[j].isalnum() or s[j] == "_"):
                j += 1
            text = s[i:j]
            tokens.append(_Token("IDENT", text, i))
            i = j
            continue
        raise KotlinTypeParseError(f"Unexpected character {c!r} at position {i}")

    tokens.append(_Token("EOF", "", n))
    return tokens


# Parsing
class _Parser:
    def __init__(self, text: str):
        self._text = text
        self._tokens = _tokenize(text)
        self._i = 0

    def _peek(self) -> _Token:
        return self._tokens[self._i]

    def _accept(self, kind: str) -> bool:
        if self._peek().kind == kind:
            self._i += 1
            return True
        return False

    def _expect(self, kind: str) -> _Token:
        tok = self._peek()
        if tok.kind != kind:
            raise KotlinTypeParseError(
                f"Expected {kind} at position {tok.pos}, got {tok.kind}"
            )
        self._i += 1
        return tok

    def parse(self) -> Type:
        t = self._parse_type()
        eof = self._peek()
        if eof.kind != "EOF":
            raise KotlinTypeParseError(
                f"Unexpected token {eof.kind} ({eof.text!r}) at position {eof.pos}"
            )
        return t

    def _parse_type(self) -> Type:
        # Parse a qualified base (possibly generic), then postfixes ([], ?)
        t = self._parse_qualified_or_simple()

        # Optional generic args, e.g. Map<String, Int>
        if self._accept("LT"):
            args = [self._parse_type()]
            while self._accept("COMMA"):
                args.append(self._parse_type())
            self._expect("GT")
            t = AppType(t, args)

        # Postfix operators. Order matters: allow repeated, e.g. T?[]?
        while True:
            if self._accept("LBRACK"):
                self._expect("RBRACK")
                t = ArrayType(t)
                continue
            if self._accept("QMARK"):
                t = NullableType(t)
                continue
            break

        return t

    def _parse_qualified_or_simple(self) -> Type:
        # Parse a dotted sequence of identifiers: a.b.C
        parts = [self._expect("IDENT").text]
        while self._accept("DOT"):
            parts.append(self._expect("IDENT").text)

        # Simple name -> primitive or ref
        base_name = parts[-1]
        if len(parts) == 1:
            return _name_to_type(base_name)

        qualifiers = parts[:-1]
        return QualifiedType(qualifiers, _name_to_type(base_name))



def _name_to_type(name: str) -> Type:
    # Primitive arrays supported by this project
    if name == "IntArray":
        return PrimArrayType(PrimArrayTypeEnum.INTARRAY)
    if name == "LongArray":
        return PrimArrayType(PrimArrayTypeEnum.LONGARRAY)

    prim = _PRIM_BY_NAME.get(name)
    if prim is not None:
        return PrimType(prim)
    return RefType(name)


def parse_kt_type(text: str) -> Type:
    return _Parser(text).parse()

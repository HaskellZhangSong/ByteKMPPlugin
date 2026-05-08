from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import override
from utils.span import Span

@dataclass(frozen=True, repr=False, kw_only=True)
class Type(ABC):
    """Base class for all types."""
    span: Span | None = None
    def __repr__(self) -> str:
        return f"<{self.__class__.__qualname__} {self.signature}>"

    @property
    @abstractmethod
    def signature(self) -> str:
        """Return the representation of the type."""
    

class VoidType(Type):
    """Represents the 'void' type."""

    @property
    @override
    def signature(self) -> str:
        return "void"


class ThisType(Type):
    """Represents the 'this' type."""
    @property
    @override
    def signature(self) -> str:
        return "this"


class NullType(Type):
    """Represents the 'null' type."""

    @property
    @override
    def signature(self) -> str:
        return "null"


class UndefinedType(Type):
    """Represents the 'undefined' type."""

    @property
    @override
    def signature(self) -> str:
        return "undefined"


@dataclass(frozen=True, repr=False)
class StringType(Type):
    @property
    @override
    def signature(self):
        return "string"


@dataclass(frozen=True, repr=False)
class NumberType(Type):
    @property
    @override
    def signature(self):
        return "number"


@dataclass(frozen=True, repr=False)
class BigIntType(Type):
    @property
    @override
    def signature(self):
        return "bigint"


@dataclass(frozen=True, repr=False)
class BooleanType(Type):
    @property
    @override
    def signature(self):
        return "boolean"

@dataclass(frozen=True, repr=False)
class ObjectType(Type):
    """Represents the 'object' type."""
    @property
    @override
    def signature(self):
        return "object"


@dataclass(frozen=True, repr=False)
class AnyType(Type):
    """Represents the 'any' type."""
    @property
    @override
    def signature(self):
        return "any"


@dataclass(frozen=True, repr=False)
class RefType(Type):
    """Represents a reference to another type."""
    name: str

    @property
    @override
    def signature(self):
        # Prefer the explicit name; fall back to the attached `ref` if present.
        if self.name:
            return self.name
        if self.ref is not None and hasattr(self.ref, "name"):
            return getattr(self.ref, "name")
        return "<unknown>"


@dataclass(frozen=True, repr=False)
class ArrayType(Type):
    element_type: Type
    @property
    @override
    def signature(self):
        return self.element_type.signature + "[]"

@dataclass(frozen=True, repr=False)
class NullableType(Type):
    """Represents a nullable type."""
    base_type: Type

    @property
    @override
    def signature(self):
        return self.base_type.signature + "?"


@dataclass(frozen=True, repr=False)
class UnionType(Type):
    types: list[Type]

    @property
    @override
    def signature(self):
        return " | ".join(t.signature for t in self.types)


@dataclass(frozen=True, repr=False)
class AppType(Type):
    """Represents application-specific types."""

    type: Type
    args: list[Type]

    @property
    @override
    def signature(self):
        return self.type.signature + "<" + ", ".join([arg.signature for arg in self.args]) + ">"


@dataclass(frozen=True, repr=False)
class QualifiedType(Type):
    """Represents qualified types."""

    qualifiers: list[str]
    base_type: RefType

    @property
    @override
    def signature(self):
        return ".".join(self.qualifiers + [self.base_type.signature])


def unwrap_nullable(t: Type) -> Type:
    """Unwrap `NullableType(T)` and `T | null | undefined` down to `T` when possible."""

    if isinstance(t, NullableType):
        return unwrap_nullable(t.base_type)

    if isinstance(t, UnionType):
        non_nullable = [
            m for m in t.types if not isinstance(m, (NullType, UndefinedType))
        ]
        if len(non_nullable) == 1:
            return unwrap_nullable(non_nullable[0])
        else:
            raise ValueError("Cannot unwrap union type with multiple non-nullable members")
    return t

def union_type_to_nullable_type(t: Type) -> Type:
    """Convert `T | null | undefined` to `NullableType(T)` when possible."""

    if isinstance(t, UnionType):
        non_nullable = [
            m for m in t.types if not isinstance(m, (NullType, UndefinedType))
        ]
        if len(non_nullable) == 1:
            if isinstance(non_nullable[0], UnionType):
                return union_type_to_nullable_type(non_nullable[0])
            elif not isinstance(non_nullable[0], NullableType):
                return NullableType(non_nullable[0])
            elif isinstance(non_nullable[0], NullableType):
                return non_nullable[0]
        else:
            raise ValueError("Cannot convert union type with multiple non-nullable members to NullableType")
    return t

def _base_name(t: Type) -> str | None:
    """Extract the simple base name from a type, ignoring qualification."""
    if isinstance(t, QualifiedType):
        return _base_name(t.base_type)
    if isinstance(t, RefType):
        return t.name
    return None

def is_primitive_type(t: Type) -> bool:
    """Return True if `t` is a primitive-ish TypeScript type.

    Primitive-ish here means:
      - string / number / boolean / void / null / undefined
      - nullable wrappers around primitives (NullableType)
      - unions where the non-null/undefined members are primitive
        (e.g. `string | null`, `number | undefined`).
    """

    if isinstance(t, NullableType):
        return is_primitive_type(t.base_type)

    if isinstance(t, UnionType):
        non_nullable = [
            m for m in t.types if not isinstance(m, (NullType, UndefinedType))
        ]
        # `null | undefined` counts as primitive-ish
        if len(non_nullable) == 1:
            return is_primitive_type(non_nullable[0])
        else:
            raise ValueError("Cannot unwrap union type with multiple non-nullable members")

    return isinstance(
        t,
        (
            StringType,
            NumberType,
            BigIntType,
            BooleanType,
            VoidType,
            NullType,
            UndefinedType,
        ),
    )

def is_bigint_type(t: Type) -> bool:
    if isinstance(t, BigIntType):
        return True
    elif isinstance(t, NullableType):
        return is_bigint_type(t.base_type)
    elif isinstance(t, UnionType):
        non_nullable = [
            m for m in t.types if not isinstance(m, (NullType, UndefinedType))
        ]
        # `null | undefined` counts as bigint
        if len(non_nullable) == 1:
            return is_bigint_type(non_nullable[0])
        else:
            raise ValueError(f"Cannot unwrap union type with multiple non-nullable members in type {t}")
    return False

def is_type_app(t : str, ty : Type) -> bool:
    ty = unwrap_nullable(ty)
    if isinstance(ty, AppType):
        base = ty.type
        if isinstance(base, RefType):
            return base.name == t
    return False

# [collections, Map] -> collections.Map should return true
def is_qualified_type_app(t: list[str], ty: Type) -> bool:
    ty = unwrap_nullable(ty)
    if isinstance(ty, AppType):
        base = ty.type
        if isinstance(base, QualifiedType):
            name = base.base_type.name
            l = base.qualifiers + [name]
            return l == t
    return False

def is_builtin_array_type(t: Type) -> bool:
    return is_type_app("Array", t)

def is_builtin_list_type(t: Type) -> bool:
    return is_type_app("List", t)

def is_builtin_map_type(t: Type) -> bool:
    return is_type_app("Map", t)

def is_sendable_array_type(t: Type) -> bool:
    return is_qualified_type_app(["collections", "Array"], t)

def is_sendable_map_type(t: Type) -> bool:
    return is_qualified_type_app(["collections", "Map"], t)

def is_ref_type(t: Type) -> bool:
    t = unwrap_nullable(t)
    return isinstance(t, RefType)



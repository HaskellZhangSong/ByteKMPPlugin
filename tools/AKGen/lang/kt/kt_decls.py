from __future__ import annotations

from abc import ABC
from dataclasses import dataclass, field
from enum import Enum

from lang.kt.kt_types import Type


class Decl(ABC):
    pass


@dataclass
class SourceFile:
    package: str | None
    declarations: list[Decl]


class KtValue(ABC):
    pass


@dataclass
class IntegerValue(KtValue):
    number: int


@dataclass
class StringValue(KtValue):
    string: str


@dataclass
class Annotations:
    name: str
    args: list[KtValue] | None = None


@dataclass
class ClassDecl(Decl):
    name: str
    members: list[Decl] | None = None
    annotations: list[Annotations] | None = None
    parent_class: list[str] = field(default_factory=list)

    def annotation_contains(self, annotation_name: str) -> bool:
        if self.annotations is None:
            return False
        for annotation in self.annotations:
            if annotation.name == annotation_name:
                return True
        return False


class PropertyModifier(Enum):
    VAR = 'var'
    VAL = 'val'
    PUBLIC = 'public'
    PRIVATE = 'private'
    INTERNAL = 'internal'
    FINAL = 'final'

@dataclass
class PropertyDecl(Decl):
    modifier: list[PropertyModifier]
    name: str
    type: Type
    annotations: list[Annotations] | None = None
    
def get_kt_string_value(ktv: KtValue) -> str:
    if isinstance(ktv, StringValue):
        return ktv.string
    else:
        raise TypeError(f"Expected StringValue, got {type(ktv)}")

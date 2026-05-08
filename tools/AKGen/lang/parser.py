from abc import ABC
from collections import deque
from typing import Deque, Iterable, Callable, TypeVar
from tree_sitter import Node
from pathlib import Path
from utils.span import Span

A = TypeVar("B")
class Parser(ABC):
    """Abstract stream-based parser.

    This class intentionally only owns `stream` and provides concrete helpers
    to push/pop/peek tokens/nodes. Subclasses build the initial stream.
    """

    def __init__(self, stream: Deque[Node] | None = None, file_path: Path | str = ""):
        self.stream: Deque[Node] = stream if stream is not None else deque()
        self.file_path = file_path

    def pop(self) -> Node:
        return self.stream.popleft()

    def push(self, nodes: Iterable[Node]) -> None:
        # Put elements to the *front* of the stream while preserving order.
        seq = list(nodes)
        self.stream.extendleft(reversed(seq))

    def push_children(self) -> None:
        node = self.pop()
        self.push(node.children)

    # TODO: Change push_children to push_type_children to gain more safety
    def push_children_type(self, type_name: str) -> None:
        node = self.pop()
        if node.type != type_name:
            raise SyntaxError(f"Expected type {type_name}, but got {node.type}, parser at: {self.get_node_position(node)}")
        self.push(node.children)

    def peek(self):
        return self.stream[0] if self.stream else None

    def peek_type(self, node_type):
        node = self.peek()
        return node is not None and node.type == node_type

    def skip(self) -> None:
        p = self.pop()
        if p.type == 'ERROR':
            raise SyntaxError(f"counter token {p.type}, parser at: {self.get_node_position(p)}")

    def eat(self, token: str) -> None:
        p = self.pop()
        if p.type != token:
            raise SyntaxError(f"Expected token {token}, but got {p.type}, parser at: {self.get_node_position(p)}")
        self.skip_comments()

    def eat_choice(self, choices: set[str]):
        p = self.pop()
        if p.type not in choices:
            raise SyntaxError(f"Expected one of {choices}, but got {p.type}, parser at: {self.get_node_position(p)}")
        self.skip_comments()

    def parse_token_str_with(self, cond: Callable[[Node], bool]) -> str:
        res = ""
        while cond(self.peek()):
            res += self.pop().text.decode('utf-8')
        return res

    def sep_parse(self, sep_token: str, parse_func):
        res = []
        while True:
            item = parse_func()
            res.append(item)
            if self.peek().type == sep_token:
                self.eat(sep_token)
            else:
                break
        return res

    def many(self, node_type, parse_fun) -> list:
        res = []
        while (node := self.peek()).type == node_type:
            res.append(parse_fun())
        return res

    def many_in_table(self, parse_table: dict[str, Callable[[], A]]) -> list[A]:
        res = []
        while (node := self.peek()).type in parse_table:
            res.append(parse_table[node.type]())
        return res

    def parse_text(self) -> str:
        p = self.pop()
        self.skip_comments()
        return p.text.decode('utf-8')

    def parse_node_text(self, node_type: str):
        i = self.pop()
        self.skip_comments()
        if i.type == node_type:
            return i.text.decode('utf-8')
        raise SyntaxError(f"Expected {node_type}, but got {i.type}, parser at {self.get_node_position(i)}")

    def parse_identifier(self) -> str:
        i = self.pop()
        self.skip_comments()
        if i.type == 'identifier' or i.type == 'property_identifier' or i.type == 'type_identifier':
            return i.text.decode('utf-8')
        raise SyntaxError(f'Expected identifier, but got {i.type}, parser at: {self.get_node_position(i)}')

    def skip_until(self, token: str) -> None:
        while len(self.stream) > 0:
            if self.peek().type == token:
                return
            self.skip()

    def skip_token_in(self, tokens: set[str]) -> None:
        while len(self.stream) > 0:
            if self.peek().type in tokens:
                self.skip()
            else:
                return
        return

    def skip_cond_util(self, pred: Callable[[str], bool]) -> None:
        while len(self.stream) > 0:
            if pred(self.peek().type):
                return
            self.skip()

    def skip_comments(self):
        self.skip_cond_util(lambda i: i != 'comment')

    def get_node_span(self, node: Node) -> Span:
        return Span(
            file_name=self.file_path,
            start=(node.start_point.row+1, node.start_point.column+1),
            end=(node.end_point.row+1, node.end_point.column+1)
        )

    def get_node_position(self, node: Node) -> str:
        node_start_point = node.start_point
        node_end_point = node.end_point
        err_msg = (f'"{self.file_path.__str__()}:'
                   f'{node_start_point.row+1}:{node_start_point.column+1}'
                   f'-'
                   f'{node_end_point.row+1}:{node_end_point.column+1}"')
        return err_msg


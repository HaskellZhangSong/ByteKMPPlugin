from dataclasses import dataclass, field

@dataclass(frozen=True)
class Span:
    file_name: str | None
    start: tuple[int, int]  # (line, column), 1-based
    end: tuple[int, int]    # (line, column), 1-based

    def __str__(self) -> str:
        return f"{self.file_name}:{self.start[0]}:{self.start[1]}-{self.end[0]}:{self.end[1]}"
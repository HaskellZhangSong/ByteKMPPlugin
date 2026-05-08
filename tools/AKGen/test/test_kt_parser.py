import unittest
from pathlib import Path

from lang.kt.kt_parser import parse_kotlin_file


class KotlinParserCasesTest(unittest.TestCase):
    BASE_DIR = Path(__file__).resolve().parent
    KT_CASES_DIR = BASE_DIR / "cases" / "kt"

    # Expected total field count per file (sum of members across classes in that file).
    EXPECTED_FIELD_COUNTS: dict[str, int] = {
        "class0.kt": 2,
        "class1.kt": 4,
        "class2.kt": 4,
        "class3.kt": 1,
        "class4.kt": 1,
        "class5.kt": 17,
        "class6.kt": 14,
        "class7.kt": 0,
        "class8.kt": 1,
        "class9.kt": 1,
        "class10.kt": 0,
        "class11.kt": 1,
        "class12.kt": 1,
        "class13.kt": 6,
    }

    def _field_count(self, file_name: str) -> int:
        sf = parse_kotlin_file(self.KT_CASES_DIR / file_name)
        return sum(len(d.members or []) for d in sf.declarations)

    def _assert_case(self, file_name: str) -> None:
        self.assertEqual(self._field_count(file_name), self.EXPECTED_FIELD_COUNTS[file_name])

    def test_class0(self) -> None:
        self._assert_case("class0.kt")

    def test_class1(self) -> None:
        self._assert_case("class1.kt")

    def test_class2(self) -> None:
        self._assert_case("class2.kt")

    def test_class3(self) -> None:
        self._assert_case("class3.kt")

    def test_class4(self) -> None:
        self._assert_case("class4.kt")

    def test_class5(self) -> None:
        self._assert_case("class5.kt")

    def test_class6(self) -> None:
        self._assert_case("class6.kt")

    def test_class7(self) -> None:
        self._assert_case("class7.kt")

    def test_class8(self) -> None:
        self._assert_case("class8.kt")

    def test_class9(self) -> None:
        self._assert_case("class9.kt")

    def test_class10(self) -> None:
        self._assert_case("class10.kt")

    def test_class11(self) -> None:
        self._assert_case("class11.kt")

    def test_class12(self) -> None:
        self._assert_case("class12.kt")

    def test_class13(self) -> None:
        self._assert_case("class13.kt")


if __name__ == "__main__":
    unittest.main()

from __future__ import annotations

from convert.pretty import multi_line_str_to_kt_node
import unittest


class TestMultiLineStr(unittest.TestCase):
    def test_2_space_indent(self) -> None:
        s = """
          a
            b
            c
              d
        """
        self.assertEqual(multi_line_str_to_kt_node(s), ["a", ["b", "c", ["d"]]])

    def test_4_space_indent(self) -> None:
        s = """
            a
                b
                c
                    d
        """
        self.assertEqual(multi_line_str_to_kt_node(s), ["a", ["b", "c", ["d"]]])

    def test_jump_indent_treated_as_one_level_per_increase(self) -> None:
        s = """
        a
                b
                                c
            d
        """
        self.assertEqual(multi_line_str_to_kt_node(s), ["a", ["b", ["c"]], "d"])

    def test_dedent_goes_back_to_previous_level(self) -> None:
        s = """
        root
            child1
                grandchild
            child2
        root2
        """
        self.assertEqual(multi_line_str_to_kt_node(s), ["root", ["child1", ["grandchild"], "child2"], "root2"])

    def test_uneven_dedent_falls_back_sensibly(self) -> None:
        # The line "c" has a smaller, uneven indentation; we treat it as root-level.
        # The subsequent indented "d" becomes a nested block under root, not under "a".
        s = """
        a
            b
       c
            d
        """
        self.assertEqual(multi_line_str_to_kt_node(s), [["a", ["b"]], "c", ["d"]])

    def test_init_indent_offsets_nesting(self) -> None:
        s = """
          a
            b
        """
        # init_indent wraps the computed node once per level.
        self.assertEqual(multi_line_str_to_kt_node(s, init_indent=1), [["a", ["b"]]])


if __name__ == "__main__":
    unittest.main()

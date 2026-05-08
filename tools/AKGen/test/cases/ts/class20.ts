@ExportClass
class Foo {
    // 1
    public abc: number = 1;
    // 2
    public static abc: number = 1;
    // 3
    private readonly abc: number = 1;
    // 4
    private def: bigint = 0n;

    //5
    fgh: string = "abc";
    // 6
    public ids: Array<number> = [1, 2, 3]
    // 7
    public tags: Array<string> = ["a", "b", "c"];
    // 8
    public scores: Map<string, number> = new Map([
        ["math", 99],
        ["eng", 95],
    ]);
    // 9
    public grouped: Map<string, Array<number>> = new Map([
        ["x", [1, 2]],
        ["y", [3, 4]],
    ]);
    // 10
    private cache: Map<string, Map<string, Array<boolean>>> = new Map()

    // 11
    private maybeList: Array<string> | null = null;

    // 12
    private mixed: Array<number> = [1, "two", 3];
}

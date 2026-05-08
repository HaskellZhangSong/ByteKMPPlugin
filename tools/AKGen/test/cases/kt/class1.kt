package com.ss.ugc.aweme;

@ShareClass("fooKMP")
actual class FooKMP {
    var code: kotlin.Int?
    @ShareField("a")
    @JSONNAME("a")
    val code_2: kotlin.Long?
}

@ShareClass("fooKMP")
expect class FooKMP {
    var code: kotlin.Int?
    @ShareField("a")
    @JSONNAME("a")
    val code_2: kotlin.Long?
}

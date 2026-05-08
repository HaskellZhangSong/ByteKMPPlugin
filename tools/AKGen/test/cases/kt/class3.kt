package com.ss.ugc.aweme;

@kotlinx.serialization.Serializable
class FooKMP {
    @kotlinx.serialization.protobuf.ProtoNumber(1)
    var name: String? = null
    fun test(): String {
        return name
    }
}

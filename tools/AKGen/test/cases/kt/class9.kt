@Annotation
@Annotation(test)
@Annotation(test = "test" )
actual class M {
    @Annotation
    @Annotation(test)
    @Annotation(test = "test" )
    val a : Int = 0
    open fun <T> check(): T {
        val type: Class<*> = javaClass
        var required = false
        @Suppress("UNCHECKED_CAST")
        return this as T
    }
}
class M {
    open fun <T> check(): T {
        val type: Class<*> = javaClass
        var required = false
        @Suppress("UNCHECKED_CAST")
        return this as T
    }
}
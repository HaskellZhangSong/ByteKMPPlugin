class Wr {
    private fun fvdsa(rawValue: Any?) {
        if (isArray(element)) {
            @Suppress("UNCHECKED_CAST")
            (element as MutableList<Any?>).add(value)
        } else {
            throw IllegalStateException()
        }
    }
}
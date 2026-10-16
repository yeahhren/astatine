package yt.ren.astatine.memory

class LongObjectMap<T : Any>(capacity: Int = 256) {
    private var keys = LongArray(capacity)
    private var values = arrayOfNulls<Any>(capacity)
    private var used = BooleanArray(capacity)
    var size = 0
        private set

    @Suppress("UNCHECKED_CAST")
    operator fun get(key: Long): T? {
        val mask = keys.size - 1
        var index = mix(key) and mask
        while (used[index]) {
            if (keys[index] == key) return values[index] as T
            index = (index + 1) and mask
        }
        return null
    }

    operator fun set(key: Long, value: T) {
        if ((size + 1) * 2 > keys.size) grow()
        insert(key, value)
    }

    @Suppress("UNCHECKED_CAST")
    fun remove(key: Long): T? {
        val mask = keys.size - 1
        var index = mix(key) and mask
        while (used[index]) {
            if (keys[index] == key) {
                val removed = values[index] as T
                used[index] = false
                values[index] = null
                size--
                var next = (index + 1) and mask
                while (used[next]) {
                    val movedKey = keys[next]
                    val movedValue = values[next]
                    used[next] = false
                    values[next] = null
                    size--
                    insert(movedKey, movedValue!!)
                    next = (next + 1) and mask
                }
                return removed
            }
            index = (index + 1) and mask
        }
        return null
    }

    fun clear() {
        used.fill(false)
        values.fill(null)
        size = 0
    }

    @PublishedApi
    internal fun valuesSnapshot(): List<Any> {
        val result = ArrayList<Any>(size)
        for (i in keys.indices) if (used[i]) result += values[i]!!
        return result
    }

    private fun insert(key: Long, value: Any) {
        val mask = keys.size - 1
        var index = mix(key) and mask
        while (used[index] && keys[index] != key) index = (index + 1) and mask
        if (!used[index]) size++
        used[index] = true
        keys[index] = key
        values[index] = value
    }

    private fun grow() {
        val oldKeys = keys
        val oldValues = values
        val oldUsed = used
        keys = LongArray(oldKeys.size * 2)
        values = arrayOfNulls(oldKeys.size * 2)
        used = BooleanArray(oldKeys.size * 2)
        size = 0
        for (i in oldKeys.indices) if (oldUsed[i]) insert(oldKeys[i], oldValues[i]!!)
    }

    private fun mix(key: Long): Int {
        val hash = key * -0x61c8864680b583ebL
        return (hash xor (hash ushr 32)).toInt()
    }
}

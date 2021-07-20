package tice.utility

class SynchronizedList<T> {

    private var base: MutableList<T> = mutableListOf()
    val size: Int
        get() = base.size

    fun add(element: T): Boolean {
        return synchronized(this) {
            base.add(element)
        }
    }

    fun addAll(elements: Collection<T>): Boolean {
        return synchronized(this) {
            base.addAll(elements)
        }
    }

    fun clear() {
        return synchronized(this) {
            base.clear()
        }
    }

    fun dequeue(count: Int = 0): List<T> {
        return synchronized(this) {
            val temp = base.take(count)
            base.removeAll(temp)

            temp
        }
    }

    override fun hashCode(): Int {
        return synchronized(this) {
            base.hashCode()
        }
    }

    override fun toString(): String {
        return synchronized(this) {
            base.toString()
        }
    }

    override fun equals(other: Any?): Boolean {
        return synchronized(this) {
            base.equals(other)
        }
    }
}

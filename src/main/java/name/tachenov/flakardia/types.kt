package name.tachenov.flakardia

class LimitedValue<T : Comparable<T>> private constructor(val value: T, val min: T, val max: T) {
    override fun equals(other: Any?): Boolean = value == (other as? LimitedValue<*>?)?.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value.toString()

    fun withValue(value : T): LimitedValue<T> = of(value, min, max)

    companion object {
        fun <T : Comparable<T>> of(value : T, min: T, max: T): LimitedValue<T> =
            LimitedValue(value.coerceIn(min, max), min, max)
    }

}

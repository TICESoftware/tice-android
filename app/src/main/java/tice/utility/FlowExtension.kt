package tice.utility

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.withTimeout

suspend inline fun <T> Flow<T>.collectWithTimeout(timeout: Long, crossinline action: suspend (index: Int, value: T) -> Unit): List<T> {
    val values = mutableListOf<T>()
    return try {
        withTimeout(timeout) {
            collectIndexed { index, value ->
                values.add(value)
                return@collectIndexed action(index, value)
            }
        }
        values
    } catch (e: Exception) {
        values
    }
}

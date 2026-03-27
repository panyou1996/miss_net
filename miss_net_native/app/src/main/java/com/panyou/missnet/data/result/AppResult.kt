package com.panyou.missnet.data.result

sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data object Empty : AppResult<Nothing>
    data class Failure(
        val message: String,
        val cause: Throwable? = null
    ) : AppResult<Nothing>
}

fun <T> appResultOf(value: T?, isEmpty: (T) -> Boolean): AppResult<T> {
    return when {
        value == null -> AppResult.Empty
        isEmpty(value) -> AppResult.Empty
        else -> AppResult.Success(value)
    }
}

fun <T> appResultOfList(values: List<T>): AppResult<List<T>> = appResultOf(values) { it.isEmpty() }

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> {
    return when (this) {
        AppResult.Empty -> AppResult.Empty
        is AppResult.Failure -> this
        is AppResult.Success -> AppResult.Success(transform(data))
    }
}

fun <T> AppResult<List<T>>.orEmptyList(): List<T> {
    return when (this) {
        AppResult.Empty -> emptyList()
        is AppResult.Failure -> emptyList()
        is AppResult.Success -> data
    }
}

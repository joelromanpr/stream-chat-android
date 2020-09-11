package io.getstream.chat.android.client.utils

import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.argThat
import org.mockito.Mockito

/**
 * Returns Mockito.eq() as nullable type to avoid java.lang.IllegalStateException when
 * null is returned.
 *
 * Generic T is nullable because implicitly bounded by Any?.
 */
fun <T> eq(obj: T): T = Mockito.eq<T>(obj)

/**
 * Returns Mockito.any() as nullable type to avoid java.lang.IllegalStateException when
 * null is returned.
 */
fun <T> any(): T = Mockito.any<T>()

/**
 * Returns ArgumentCaptor.capture() as nullable type to avoid java.lang.IllegalStateException
 * when null is returned.
 */
fun <T> capture(argumentCaptor: ArgumentCaptor<T>): T = argumentCaptor.capture()

/**
 * Calls ArgumentMatcher.argThat() and returns result to avoid nulls when using with non-nullable parameters
 */
fun <T> safeArgThat(result: T, matcher: ((T) -> Boolean)): T {
    argThat(matcher)
    return result
}
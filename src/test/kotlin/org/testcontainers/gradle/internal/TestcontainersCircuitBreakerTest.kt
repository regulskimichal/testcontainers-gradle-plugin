package org.testcontainers.gradle.internal

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TestcontainersCircuitBreakerTest {

    @Test
    fun `resets Testcontainers fail-fast state and cached client factory`() {
        val failFast = assertNotNull(TestcontainersCircuitBreaker.failFast)
        val factoryInstanceField = assertNotNull(TestcontainersCircuitBreaker.factoryInstanceField)

        // Given fail-fast state has tripped
        failFast.set(true)

        // When
        TestcontainersCircuitBreaker.reset()

        // Then: both flags are cleared so Testcontainers permits a fresh connection attempt
        assertEquals(false, failFast.get())
        assertNull(factoryInstanceField.get(null))
    }
}

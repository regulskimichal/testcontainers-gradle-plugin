package org.testcontainers.gradle

import org.junit.jupiter.api.extension.ConditionEvaluationResult
import org.junit.jupiter.api.extension.ExecutionCondition
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.platform.commons.support.AnnotationSupport
import org.testcontainers.DockerClientFactory

class DockerAvailableCondition : ExecutionCondition {
    override fun evaluateExecutionCondition(context: ExtensionContext): ConditionEvaluationResult {
        val requiresDocker = AnnotationSupport.findAnnotation(context.element, RequiresDocker::class.java)
            .orElseGet {
                AnnotationSupport.findAnnotation(context.testClass, RequiresDocker::class.java).orElse(null)
            }

        val failIfUnavailable = requiresDocker?.failIfUnavailable ?: false

        return when {
            DockerClientFactory.instance().isDockerAvailable -> ConditionEvaluationResult.enabled("Docker is available")
            failIfUnavailable -> error("Docker is required but not available on this environment.")
            else -> ConditionEvaluationResult.disabled("Docker is not available, skipping integration test")
        }
    }
}

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ExtendWith(DockerAvailableCondition::class)
annotation class RequiresDocker(
    val failIfUnavailable: Boolean = true
)

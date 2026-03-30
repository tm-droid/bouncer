package com.tanush.bouncer.monitoring

import io.sentry.ITransaction
import io.sentry.Sentry
import io.sentry.SpanStatus
import com.tanush.bouncer.utils.sentryTrySuspend

/**
 * Minimal AI/LLM span helpers aligned to Sentry GenAI op conventions.
 */
object SentryAiSpans {
    suspend fun <T> withAgentInvocation(
        agentName: String,
        model: String,
        block: suspend (transaction: ITransaction) -> T
    ): T {
        val transaction = Sentry.startTransaction("invoke_agent $agentName", "gen_ai.invoke_agent")
        transaction.setData("gen_ai.agent.name", agentName)
        transaction.setData("gen_ai.request.model", model)
        return sentryTrySuspend("SentryAiSpans.withAgentInvocation") {
            try {
                val result = block(transaction)
                transaction.status = SpanStatus.OK
                result
            } catch (t: Throwable) {
                transaction.status = SpanStatus.INTERNAL_ERROR
                transaction.throwable = t
                throw t
            } finally {
                transaction.finish()
            }
        }
    }
}

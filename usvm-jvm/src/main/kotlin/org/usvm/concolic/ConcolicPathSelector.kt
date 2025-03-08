package org.usvm.concolic

import org.jacodb.api.cfg.JcInst
import org.usvm.UPathSelector
import org.usvm.machine.state.JcState

class ConcolicPathSelector(private val concolicTrace: MutableList<JcInst>) : UPathSelector<JcState> {
    private var nextState: JcState? = null
    private val forkedStates = ArrayDeque<JcState>()

    override fun isEmpty(): Boolean {
        return forkedStates.isEmpty() && nextState == null
    }

    override fun peek(): JcState {
        return if (forkedStates.isEmpty()) nextState!! else forkedStates.removeLast()
            .also { it.deviatedFromConcolicTrace = true }
    }

    override fun update(state: JcState) {
        // nothing to do
    }

    override fun add(states: Collection<JcState>) {
        if (nextState == null) {
            nextState = states.single()
            return
        }
        for (state in states) {
            if (concolicTrace.isNotEmpty()) {
                if (concolicTrace.first() == state.pathNode.statement) {
                    forkedStates.add(nextState!!)
                    nextState = state
                } else {
                    forkedStates.add(state)
                }
            }
        }
    }

    override fun remove(state: JcState) {
        require(state == nextState)
        nextState = null
    }
}
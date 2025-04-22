package org.usvm.concolic

import org.jacodb.api.jvm.cfg.JcInst
import org.usvm.machine.state.JcState
import org.usvm.statistics.collectors.StatesCollector

class ConcolicStatesCollector(private val concolicTrace: MutableList<JcInst>) : StatesCollector<JcState> {
    private val mutableCollectedStates = mutableListOf<JcState>()
    override val collectedStates: List<JcState> = mutableCollectedStates

    override fun onState(parent: JcState, forks: Sequence<JcState>) {
        if (concolicTrace.isNotEmpty() && forks.any()) {
            for (state in (forks + parent)) {
                if (concolicTrace.first() != state.pathNode.statement) {
                    mutableCollectedStates.add(state)
                }
            }
        }
    }
}
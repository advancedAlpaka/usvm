package org.usvm.instrumentation;

import org.usvm.instrumentation.collector.trace.ConcolicCollector.InstructionInfo;

import java.util.function.Function;

@SuppressWarnings("unused")
public class ConcolicHelper {
    public static Function<InstructionInfo, Void> stepAction;
    public static Function<InstructionInfo, Boolean> chooseBranchAction;

    public static int count = 0;

    private static void breakpoint() {
        count++;
    }

    public static void step(InstructionInfo info) {
        stepAction.apply(info);
    }

    public static void chooseBranch(InstructionInfo info) {
        System.err.println("CT invoke choose branch");
        Boolean need = chooseBranchAction.apply(info);
        if(need) {
            breakpoint();
        }
    }

    static {
        System.err.println("Hello from ct");
    }
}

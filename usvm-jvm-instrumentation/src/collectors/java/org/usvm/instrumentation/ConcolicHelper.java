package org.usvm.instrumentation;

import org.usvm.instrumentation.collector.trace.ConcolicCollector.InstructionInfo;

import java.util.function.Function;

public class ConcolicHelper {
    public static Function<InstructionInfo, Void> stepAction;
    public static Function<InstructionInfo, Void> chooseBranchAction;

    public static void step(InstructionInfo info) {
        stepAction.apply(info);
    }

    public static void chooseBranch(InstructionInfo info) {
        System.err.println("CT invoke choose branch");
        chooseBranchAction.apply(info);
    }

    static {
        System.err.println("Hello from ct");
    }
}

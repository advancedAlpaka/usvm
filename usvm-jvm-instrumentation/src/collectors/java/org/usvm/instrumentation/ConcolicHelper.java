package org.usvm.instrumentation;

import java.util.function.Function;

public class ConcolicHelper {
    public static Function<String, Void> beforeIfAction;
    public static Function<String, Object[]> changeBranchAction;

    public static void beforeIf(String obj) {
        beforeIfAction.apply(obj);
    }

}

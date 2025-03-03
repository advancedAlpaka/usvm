package org.usvm.instrumentation.collector.trace;

public class ConcolicCollector {

    public static int tracePointer = -1;
    public static InstructionInfo[] symbolicInstructionsTrace = new InstructionInfo[32];

    public static int stackPointer = -1;
    public static byte[] thisFlagsStack = new byte[32];
    public static byte[][] argumentsFlagsStack = new byte[32][];
    public static byte[][] localVariablesFlagsStack = new byte[32][];

    public static IdentityHashMap<Object, HeapObjectDescriptor> heapFlags = new IdentityHashMap<>();
    public static byte[] staticFieldsFlags = new byte[32];

    public static void resizeFlagsStacks() {
        int newLength = (stackPointer + 1) * 2;

        byte[] newThisFlagsStack = new byte[newLength];
        System.arraycopy(thisFlagsStack, 0, newThisFlagsStack, 0, thisFlagsStack.length);
        thisFlagsStack = newThisFlagsStack;

        byte[][] newArgumentsFlagsStack = new byte[newLength][];
        System.arraycopy(argumentsFlagsStack, 0, newArgumentsFlagsStack, 0, argumentsFlagsStack.length);
        argumentsFlagsStack = newArgumentsFlagsStack;

        byte[][] newLocalVariablesFlagsStack = new byte[newLength][];
        System.arraycopy(localVariablesFlagsStack, 0, newLocalVariablesFlagsStack, 0, localVariablesFlagsStack.length);
        localVariablesFlagsStack = newLocalVariablesFlagsStack;
    }

    public static void resizeSymbolicInstructionsTrace() {
        InstructionInfo[] newInstructionsTrace = new InstructionInfo[tracePointer * 2];
        System.arraycopy(symbolicInstructionsTrace, 0, newInstructionsTrace, 0, symbolicInstructionsTrace.length);
        symbolicInstructionsTrace = newInstructionsTrace;
    }

    public static void resizeStaticFieldsFlags() {
        byte[] newStaticFieldsFlags = new byte[staticFieldsFlags.length * 2];
        System.arraycopy(staticFieldsFlags, 0, newStaticFieldsFlags, 0, staticFieldsFlags.length);
        staticFieldsFlags = newStaticFieldsFlags;
    }

    public static class InstructionInfo {
        public long jcInstructionId;

        public int argumentsPointer = -1;
        public ConcreteArgument[] concreteArguments = new ConcreteArgument[8];

        public void addConcreteArgument(int index, Object value) {
            if (++argumentsPointer >= concreteArguments.length) {
                ConcreteArgument[] newConcreteArguments = new ConcreteArgument[argumentsPointer * 2];
                System.arraycopy(concreteArguments, 0, newConcreteArguments, 0, concreteArguments.length);
                concreteArguments = newConcreteArguments;
            }

            concreteArguments[argumentsPointer] = new ConcreteArgument(index, value);
        }

        public InstructionInfo(long jcInstructionId) {
            this.jcInstructionId = jcInstructionId;
        }
    }

    public static class ConcreteArgument {
        // value by -1 index is used for selected branch in `if` and `switch` instructions
        public int index;
        public Object value;

        public ConcreteArgument(int index, Object value) {
            this.index = index;
            this.value = value;
        }
    }

    private static class StackFrame {
        byte[] arguments;
        byte[] localVariables;
        byte thisDescriptor;

        StackFrame(int argumentsNum) {
            arguments = new byte[argumentsNum];
        }
    }

    public static class HeapObjectDescriptor {
        private byte[] inhabitants = new byte[32];

        public void addFlags(int inhabitantId, byte flags) {
            if (inhabitantId >= inhabitants.length) {
                byte[] newInhabitants = new byte[inhabitantId * 2];
                System.arraycopy(inhabitants, 0, newInhabitants, 0, inhabitants.length);
                inhabitants = newInhabitants;
            }
            inhabitants[inhabitantId] = flags;
        }

        public byte getFlags(int inhabitantId) {
            return inhabitantId < inhabitants.length ? inhabitants[inhabitantId] : 0;
        }
    }

    public static class IdentityHashMap<K,V> {
        private Object[] table = new Object[64];
        public int size;
        private int threshold = 21;

        @SuppressWarnings({"unchecked"})
        public V get(Object key) {
            Object[] tab = table;
            int len = tab.length;
            int i = hash(key, len);
            while (true) {
                Object item = tab[i];
                if (item == key)
                    return (V) tab[i + 1];
                if (item == null)
                    return null;
                i = nextKeyIndex(i, len);
            }
        }

        public void put(K key, V value) {
            Object[] tab = table;
            int len = tab.length;
            int i = hash(key, len);

            Object item;
            while ((item = tab[i]) != null) {
                if (item == key) {
                    tab[i + 1] = value;
                    return;
                }
                i = nextKeyIndex(i, len);
            }

            tab[i] = key;
            tab[i + 1] = value;
            if (++size >= threshold)
                resize(len);
        }

        private void resize(int newCapacity) {
            int newLength = newCapacity * 2;

            Object[] oldTable = table;
            Object[] newTable = new Object[newLength];
            threshold = newLength / 3;

            for (int j = 0; j < oldTable.length; j += 2) {
                Object key = oldTable[j];
                if (key != null) {
                    Object value = oldTable[j+1];
                    oldTable[j] = null;
                    oldTable[j+1] = null;
                    int i = hash(key, newLength);
                    while (newTable[i] != null)
                        i = nextKeyIndex(i, newLength);
                    newTable[i] = key;
                    newTable[i + 1] = value;
                }
            }
            table = newTable;
        }

        private static int hash(Object x, int length) {
            int h = System.identityHashCode(x);
            return ((h << 1) - (h << 8)) & (length - 1);
        }

        private static int nextKeyIndex(int i, int len) {
            return (i + 2 < len ? i + 2 : 0);
        }
    }
}

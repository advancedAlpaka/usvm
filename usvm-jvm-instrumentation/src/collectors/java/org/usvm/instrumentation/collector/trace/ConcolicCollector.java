package org.usvm.instrumentation.collector.trace;

public class ConcolicCollector {
    public static final ArrayList<InstructionInfo> symbolicInstructionsTrace = new ArrayList<>();

    public static int stackPointer = -1;

    public static byte[] thisFlagsStack = new byte[32];
    public static byte[][] argumentsFlagsStack = new byte[32][];
    public static byte[][] localVariablesFlagsStack = new byte[32][];

    public static final IdentityHashMap<Object, HeapObjectDescriptor> heapFlags = new IdentityHashMap<>();
    private static byte[] staticFieldsFlags = new byte[32];


    private static InstructionInfo lastInstruction;
    private static byte expressionFlagsBuffer;

    private static StackFrame newCallStackFrame;

    public static void initializeNewCallStackFrame(int argumentsNum) {
        int nextStackPointer = stackPointer + 1;

        if (nextStackPointer >= thisFlagsStack.length) {
            byte[] newThisFlagsStack = new byte[nextStackPointer * 2];
            System.arraycopy(thisFlagsStack, 0, newThisFlagsStack, 0, thisFlagsStack.length);
            thisFlagsStack = newThisFlagsStack;

            byte[][] newArgumentsFlagsStack = new byte[nextStackPointer * 2][];
            System.arraycopy(argumentsFlagsStack, 0, newArgumentsFlagsStack, 0, argumentsFlagsStack.length);
            argumentsFlagsStack = newArgumentsFlagsStack;

            byte[][] newLocalVariablesFlagsStack = new byte[nextStackPointer * 2][];
            System.arraycopy(localVariablesFlagsStack, 0, newLocalVariablesFlagsStack, 0, localVariablesFlagsStack.length);
            localVariablesFlagsStack = newLocalVariablesFlagsStack;
        }

        argumentsFlagsStack[nextStackPointer] = new byte[argumentsNum];
    }

    public static void onEnterFunction(int localVariablesNum) {
        stackPointer++;
        localVariablesFlagsStack[stackPointer] = new byte[localVariablesNum];
    }

    public static void onExitFunction() {
        argumentsFlagsStack[stackPointer] = null;
        localVariablesFlagsStack[stackPointer] = null;
        stackPointer--;
        if (stackPointer == -1) {
            saveLastInstructionIfSymbolic();
        }
    }

    public static void processLocalVariable(long jcInstructionId, int variableIndex, Object variableValue,
                                            int concreteArgumentIndex, boolean isCallReceiver, int callParameterIndex) {
        updateLastInstructionIfNeeded(jcInstructionId);
        byte variableFlags = localVariablesFlagsStack[stackPointer][variableIndex];
        expressionFlagsBuffer |= variableFlags;
        addExpressionValueIfConcrete(variableFlags, concreteArgumentIndex, variableValue);
        updateCallStackFrameIfNeeded(variableFlags, isCallReceiver, callParameterIndex);
    }

    public static void processByteLocalVariable(long jcInstructionId, int variableIndex, byte variableValue,
                                                int concreteArgumentIndex, boolean isCallReceiver, int callParameterIndex) {
        processLocalVariable(jcInstructionId, variableIndex, variableValue, concreteArgumentIndex, isCallReceiver, callParameterIndex);
    }

    public static void processShortLocalVariable(long jcInstructionId, int variableIndex, short variableValue,
                                                 int concreteArgumentIndex, boolean isCallReceiver, int callParameterIndex) {
        processLocalVariable(jcInstructionId, variableIndex, variableValue, concreteArgumentIndex, isCallReceiver, callParameterIndex);
    }

    public static void processIntLocalVariable(long jcInstructionId, int variableIndex, int variableValue,
                                               int concreteArgumentIndex, boolean isCallReceiver, int callParameterIndex) {
        processLocalVariable(jcInstructionId, variableIndex, variableValue, concreteArgumentIndex, isCallReceiver, callParameterIndex);
    }

    public static void processLongLocalVariable(long jcInstructionId, int variableIndex, long variableValue,
                                                int concreteArgumentIndex, boolean isCallReceiver, int callParameterIndex) {
        processLocalVariable(jcInstructionId, variableIndex, variableValue, concreteArgumentIndex, isCallReceiver, callParameterIndex);
    }

    public static void processFloatLocalVariable(long jcInstructionId, int variableIndex, float variableValue,
                                                 int concreteArgumentIndex, boolean isCallReceiver, int callParameterIndex) {
        processLocalVariable(jcInstructionId, variableIndex, variableValue, concreteArgumentIndex, isCallReceiver, callParameterIndex);
    }

    public static void processDoubleLocalVariable(long jcInstructionId, int variableIndex, double variableValue,
                                                  int concreteArgumentIndex, boolean isCallReceiver, int callParameterIndex) {
        processLocalVariable(jcInstructionId, variableIndex, variableValue, concreteArgumentIndex, isCallReceiver, callParameterIndex);
    }

    public static void processCharLocalVariable(long jcInstructionId, int variableIndex, char variableValue,
                                                int concreteArgumentIndex, boolean isCallReceiver, int callParameterIndex) {
        processLocalVariable(jcInstructionId, variableIndex, variableValue, concreteArgumentIndex, isCallReceiver, callParameterIndex);
    }

    public static void processBooleanLocalVariable(long jcInstructionId, int variableIndex, boolean variableValue,
                                                   int concreteArgumentIndex, boolean isCallReceiver, int callParameterIndex) {
        processLocalVariable(jcInstructionId, variableIndex, variableValue, concreteArgumentIndex, isCallReceiver, callParameterIndex);
    }

    public static void processArgument(long jcInstructionId, int argumentIndex, Object argumentValue,
                                       int concreteArgumentIndex, boolean isCallReceiver, int callParameterIndex) {
        updateLastInstructionIfNeeded(jcInstructionId);
        byte argumentFlags = argumentsFlagsStack[stackPointer][argumentIndex];
        expressionFlagsBuffer |= argumentFlags;
        addExpressionValueIfConcrete(argumentFlags, concreteArgumentIndex, argumentValue);
        updateCallStackFrameIfNeeded(argumentFlags, isCallReceiver, callParameterIndex);
    }

    public static void processByteArgument(long jcInstructionId, int argumentIndex, byte argumentValue,
                                           int concreteArgumentIndex, boolean isCallReceiver, int callParameterIndex) {
        processArgument(jcInstructionId, argumentIndex, argumentValue, concreteArgumentIndex, isCallReceiver, callParameterIndex);
    }

    public static void processShortArgument(long jcInstructionId, int argumentIndex, short argumentValue,
                                            int concreteArgumentIndex, boolean isCallReceiver, int callParameterIndex) {
        processArgument(jcInstructionId, argumentIndex, argumentValue, concreteArgumentIndex, isCallReceiver, callParameterIndex);
    }

    public static void processIntArgument(long jcInstructionId, int argumentIndex, int argumentValue,
                                          int concreteArgumentIndex, boolean isCallReceiver, int callParameterIndex) {
        processArgument(jcInstructionId, argumentIndex, argumentValue, concreteArgumentIndex, isCallReceiver, callParameterIndex);
    }

    public static void processLongArgument(long jcInstructionId, int argumentIndex, long argumentValue,
                                           int concreteArgumentIndex, boolean isCallReceiver, int callParameterIndex) {
        processArgument(jcInstructionId, argumentIndex, argumentValue, concreteArgumentIndex, isCallReceiver, callParameterIndex);
    }

    public static void processFloatArgument(long jcInstructionId, int argumentIndex, float argumentValue,
                                            int concreteArgumentIndex, boolean isCallReceiver, int callParameterIndex) {
        processArgument(jcInstructionId, argumentIndex, argumentValue, concreteArgumentIndex, isCallReceiver, callParameterIndex);
    }

    public static void processDoubleArgument(long jcInstructionId, int argumentIndex, double argumentValue,
                                             int concreteArgumentIndex, boolean isCallReceiver, int callParameterIndex) {
        processArgument(jcInstructionId, argumentIndex, argumentValue, concreteArgumentIndex, isCallReceiver, callParameterIndex);
    }

    public static void processCharArgument(long jcInstructionId, int argumentIndex, char argumentValue,
                                           int concreteArgumentIndex, boolean isCallReceiver, int callParameterIndex) {
        processArgument(jcInstructionId, argumentIndex, argumentValue, concreteArgumentIndex, isCallReceiver, callParameterIndex);
    }

    public static void processBooleanArgument(long jcInstructionId, int argumentIndex, boolean argumentValue,
                                              int concreteArgumentIndex, boolean isCallReceiver, int callParameterIndex) {
        processArgument(jcInstructionId, argumentIndex, argumentValue, concreteArgumentIndex, isCallReceiver, callParameterIndex);
    }

    public static void processThis(long jcInstructionId, Object thisValue,
                                   int concreteArgumentIndex, boolean isCallReceiver, int callParameterIndex) {
        updateLastInstructionIfNeeded(jcInstructionId);
        byte thisFlags = thisFlagsStack[stackPointer];
        expressionFlagsBuffer |= thisFlags;
        addExpressionValueIfConcrete(thisFlags, concreteArgumentIndex, thisValue);
        updateCallStackFrameIfNeeded(thisFlags, isCallReceiver, callParameterIndex);
    }

    public static void processField(long jcInstructionId, Object instance, int fieldId, Object fieldValue,
                                    int concreteArgumentIndex, boolean isCallReceiver, int callParameterIndex) {
        updateLastInstructionIfNeeded(jcInstructionId);
        byte fieldFlags = 0;
        if (instance == null) {
            if (fieldId < staticFieldsFlags.length) {
                fieldFlags = staticFieldsFlags[fieldId];
            }
        } else {
            HeapObjectDescriptor objectDescriptor = heapFlags.get(instance);
            if (objectDescriptor != null) {
                fieldFlags = objectDescriptor.getFlags(fieldId);
            }
        }
        expressionFlagsBuffer |= fieldFlags;
        addExpressionValueIfConcrete(fieldFlags, concreteArgumentIndex, fieldValue);
        updateCallStackFrameIfNeeded(fieldFlags, isCallReceiver, callParameterIndex);
    }

    public static void processByteField(long jcInstructionId, Object instance, int fieldId, byte fieldValue,
                                        int concreteArgumentIndex, boolean isCallReceiver, int callParameterIndex) {
        processField(jcInstructionId, instance, fieldId, fieldValue, concreteArgumentIndex, isCallReceiver, callParameterIndex);
    }

    public static void processShortField(long jcInstructionId, Object instance, int fieldId, short fieldValue,
                                         int concreteArgumentIndex, boolean isCallReceiver, int callParameterIndex) {
        processField(jcInstructionId, instance, fieldId, fieldValue, concreteArgumentIndex, isCallReceiver, callParameterIndex);
    }

    public static void processIntField(long jcInstructionId, Object instance, int fieldId, int fieldValue,
                                       int concreteArgumentIndex, boolean isCallReceiver, int callParameterIndex) {
        processField(jcInstructionId, instance, fieldId, fieldValue, concreteArgumentIndex, isCallReceiver, callParameterIndex);
    }

    public static void processLongField(long jcInstructionId, Object instance, int fieldId, long fieldValue,
                                        int concreteArgumentIndex, boolean isCallReceiver, int callParameterIndex) {
        processField(jcInstructionId, instance, fieldId, fieldValue, concreteArgumentIndex, isCallReceiver, callParameterIndex);
    }

    public static void processFloatField(long jcInstructionId, Object instance, int fieldId, float fieldValue,
                                         int concreteArgumentIndex, boolean isCallReceiver, int callParameterIndex) {
        processField(jcInstructionId, instance, fieldId, fieldValue, concreteArgumentIndex, isCallReceiver, callParameterIndex);
    }

    public static void processDoubleField(long jcInstructionId, Object instance, int fieldId, double fieldValue,
                                          int concreteArgumentIndex, boolean isCallReceiver, int callParameterIndex) {
        processField(jcInstructionId, instance, fieldId, fieldValue, concreteArgumentIndex, isCallReceiver, callParameterIndex);
    }

    public static void processCharField(long jcInstructionId, Object instance, int fieldId, char fieldValue,
                                        int concreteArgumentIndex, boolean isCallReceiver, int callParameterIndex) {
        processField(jcInstructionId, instance, fieldId, fieldValue, concreteArgumentIndex, isCallReceiver, callParameterIndex);
    }

    public static void processBooleanField(long jcInstructionId, Object instance, int fieldId, boolean fieldValue,
                                           int concreteArgumentIndex, boolean isCallReceiver, int callParameterIndex) {
        processField(jcInstructionId, instance, fieldId, fieldValue, concreteArgumentIndex, isCallReceiver, callParameterIndex);
    }

    public static void processArrayAccess(long jcInstructionId, Object arrayInstance, int arrayIndex, Object value,
                                          int concreteArgumentIndex, boolean isCallReceiver, int callParameterIndex) {
        updateLastInstructionIfNeeded(jcInstructionId);
        byte elementFlags = 0;
        HeapObjectDescriptor objectDescriptor = heapFlags.get(arrayInstance);
        if (objectDescriptor != null) {
            elementFlags = objectDescriptor.getFlags(arrayIndex);
        }
        expressionFlagsBuffer |= elementFlags;
        addExpressionValueIfConcrete(elementFlags, concreteArgumentIndex, value);
        updateCallStackFrameIfNeeded(elementFlags, isCallReceiver, callParameterIndex);
    }

    public static void processByteArrayAccess(long jcInstructionId, Object arrayInstance, int arrayIndex, byte value,
                                              int concreteArgumentIndex, boolean isCallReceiver, int callParameterIndex) {
        processArrayAccess(jcInstructionId, arrayInstance, arrayIndex, value, concreteArgumentIndex, isCallReceiver, callParameterIndex);
    }

    public static void processShortArrayAccess(long jcInstructionId, Object arrayInstance, int arrayIndex, short value,
                                               int concreteArgumentIndex, boolean isCallReceiver, int callParameterIndex) {
        processArrayAccess(jcInstructionId, arrayInstance, arrayIndex, value, concreteArgumentIndex, isCallReceiver, callParameterIndex);
    }

    public static void processIntArrayAccess(long jcInstructionId, Object arrayInstance, int arrayIndex, int value,
                                             int concreteArgumentIndex, boolean isCallReceiver, int callParameterIndex) {
        processArrayAccess(jcInstructionId, arrayInstance, arrayIndex, value, concreteArgumentIndex, isCallReceiver, callParameterIndex);
    }

    public static void processLongArrayAccess(long jcInstructionId, Object arrayInstance, int arrayIndex, long value,
                                              int concreteArgumentIndex, boolean isCallReceiver, int callParameterIndex) {
        processArrayAccess(jcInstructionId, arrayInstance, arrayIndex, value, concreteArgumentIndex, isCallReceiver, callParameterIndex);
    }

    public static void processFloatArrayAccess(long jcInstructionId, Object arrayInstance, int arrayIndex, float value,
                                               int concreteArgumentIndex, boolean isCallReceiver, int callParameterIndex) {
        processArrayAccess(jcInstructionId, arrayInstance, arrayIndex, value, concreteArgumentIndex, isCallReceiver, callParameterIndex);
    }

    public static void processDoubleArrayAccess(long jcInstructionId, Object arrayInstance, int arrayIndex, double value,
                                                int concreteArgumentIndex, boolean isCallReceiver, int callParameterIndex) {
        processArrayAccess(jcInstructionId, arrayInstance, arrayIndex, value, concreteArgumentIndex, isCallReceiver, callParameterIndex);
    }

    public static void processCharArrayAccess(long jcInstructionId, Object arrayInstance, int arrayIndex, char value,
                                              int concreteArgumentIndex, boolean isCallReceiver, int callParameterIndex) {
        processArrayAccess(jcInstructionId, arrayInstance, arrayIndex, value, concreteArgumentIndex, isCallReceiver, callParameterIndex);
    }

    public static void processBooleanArrayAccess(long jcInstructionId, Object arrayInstance, int arrayIndex, boolean value,
                                                 int concreteArgumentIndex, boolean isCallReceiver, int callParameterIndex) {
        processArrayAccess(jcInstructionId, arrayInstance, arrayIndex, value, concreteArgumentIndex, isCallReceiver, callParameterIndex);
    }

    private static void updateLastInstructionIfNeeded(long jcInstructionId) {
        if (lastInstruction == null || lastInstruction.jcInstructionId != jcInstructionId) {
            saveLastInstructionIfSymbolic();
            lastInstruction = new InstructionInfo(jcInstructionId);
            expressionFlagsBuffer = 0;
        }
    }

    private static void updateCallStackFrameIfNeeded(Byte flags, boolean isCallReceiver, int callParameterIndex) {
        int nextStackPointer = stackPointer + 1;
        if (isCallReceiver) {
            thisFlagsStack[nextStackPointer] = flags;
        } else if (callParameterIndex != -1) {
            argumentsFlagsStack[nextStackPointer][callParameterIndex] = flags;
        }
    }

    private static void saveLastInstructionIfSymbolic() {
        if (lastInstruction != null && isSymbolic(expressionFlagsBuffer)) {
            symbolicInstructionsTrace.add(lastInstruction);
        }
    }

    private static void addExpressionValueIfConcrete(byte valueFlags, int index, Object value) {
        if (!isSymbolic(valueFlags)) {
            lastInstruction.concreteArguments.add(new ConcreteArgument(index, value));
        }
    }

    public static void assignToLocalVariable(int variableIndex) {
        localVariablesFlagsStack[stackPointer][variableIndex] = expressionFlagsBuffer;
    }

    public static void assignToArgument(int argumentIndex) {
        argumentsFlagsStack[stackPointer][argumentIndex] = expressionFlagsBuffer;
    }

    public static void assignToField(Object instance, int fieldId) {
        if (instance == null) {
            if (fieldId >= staticFieldsFlags.length) {
                byte[] newStaticFieldsFlags = new byte[fieldId * 2];
                System.arraycopy(staticFieldsFlags, 0, newStaticFieldsFlags, 0, staticFieldsFlags.length);
                staticFieldsFlags = newStaticFieldsFlags;
            }
            staticFieldsFlags[fieldId] = expressionFlagsBuffer;
        } else {
            HeapObjectDescriptor objectDescriptor = heapFlags.get(instance);
            if (objectDescriptor == null) {
                if (expressionFlagsBuffer == 0) {
                    return;
                }
                objectDescriptor = new HeapObjectDescriptor();
            }
            objectDescriptor.addFlags(fieldId, expressionFlagsBuffer);
            heapFlags.put(instance, objectDescriptor);
        }
    }

    public static void assignToArray(Object arrayInstance, int index) {
        HeapObjectDescriptor objectDescriptor = heapFlags.get(arrayInstance);
        if (objectDescriptor == null) {
            if (expressionFlagsBuffer == 0) {
                return;
            }
            objectDescriptor = new HeapObjectDescriptor();
        }
        objectDescriptor.addFlags(index, expressionFlagsBuffer);
        heapFlags.put(arrayInstance, objectDescriptor);
    }

    private static boolean isSymbolic(byte flags) {
        return isBitSetInFlags(flags, 0);
    }

    private static boolean isBitSetInFlags(byte flags, int position) {
        return (flags & (1 << position)) != 0;
    }

    public static class InstructionInfo {
        public long jcInstructionId;
        public ArrayList<ConcreteArgument> concreteArguments = new ArrayList<>();

        public InstructionInfo(long jcInstructionId) {
            this.jcInstructionId = jcInstructionId;
        }
    }

    public static class ConcreteArgument {
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

        void addFlags(int inhabitantId, byte flags) {
            if (inhabitantId >= inhabitants.length) {
                byte[] newInhabitants = new byte[inhabitantId * 2];
                System.arraycopy(inhabitants, 0, newInhabitants, 0, inhabitants.length);
                inhabitants = newInhabitants;
            }
            inhabitants[inhabitantId] = flags;
        }

        byte getFlags(int inhabitantId) {
            return inhabitantId < inhabitants.length ? inhabitants[inhabitantId] : 0;
        }
    }

    public static class ArrayList<T> {
        private Object[] array;
        private int size;

        public ArrayList() {
            this.array = new Object[10];
        }

        public void add(T element) {
            if (size >= array.length) {
                resize();
            }
            array[size] = element;
            size++;
        }

        @SuppressWarnings("unchecked")
        public T get(int index) {
            return (T) array[index];
        }

        public T last() {
            return get(size - 1);
        }

        public int size() {
            return size;
        }

        public void removeLast() {
            array[size] = null;
            if (size == 0) return;
            size--;
        }

        public void clear() {
            for (int i = 0; i < size; i++) {
                array[i] = null;
            }
            size = 0;
        }

        private void resize() {
            Object[] newArray = new Object[array.length * 2];
            System.arraycopy(array, 0, newArray, 0, array.length);
            array = newArray;
        }

    public static class HashMap<K, V> {
        private static final int DEFAULT_CAPACITY = 16;
        private static final double LOAD_FACTOR = 0.75;

        private Entry<K, V>[] table;
        private int size = 0;

        @SuppressWarnings({"unchecked", "rawtypes"})
        public HashMap() {
            this.table = new Entry[DEFAULT_CAPACITY];
        }

    private static class IdentityHashMap<K,V> {
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

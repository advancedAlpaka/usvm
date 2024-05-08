package org.usvm.instrumentation.collector.trace;

public class ConcolicCollector {
    public static final ArrayList<InstructionInfo> symbolicInstructionsTrace = new ArrayList<>();
    private static final ArrayList<StackFrame> stackValuesFlags = new ArrayList<>();
    private static final HashMap<Integer, HeapObjectDescriptor> heapFlags = new HashMap<>();
    private static final HashMap<String, Byte> staticFieldsFlags = new HashMap<>();


    private static InstructionInfo lastInstruction;
    private static byte expressionFlagsBuffer;


    private static StackFrame newCallStackFrame = new StackFrame();
    public static void onEnterCall() {
        stackValuesFlags.add(newCallStackFrame);
        newCallStackFrame = new StackFrame();
    }

    public static void onExitCall() {
        stackValuesFlags.removeLast();
        if (stackValuesFlags.size == 0) {
            saveLastInstructionIfSymbolic();
        }
    }

    public static void processLocalVariable(long jcInstructionId, int variableIndex, Object variableValue,
                                            int concreteArgumentIndex, boolean isCallReceiver, int callParameterIndex) {
        updateLastInstructionIfNeeded(jcInstructionId);
        Byte variableFlags = stackValuesFlags.last().localVariables.get(variableIndex);
        if (variableFlags == null) {
            variableFlags = 0;
        }
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
        Byte argumentFlags = stackValuesFlags.last().arguments.get(argumentIndex);
        if (argumentFlags == null) {
            argumentFlags = 0;
        }
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
        Byte thisFlags = stackValuesFlags.last().thisDescriptor;
        if (thisFlags == null) {
            thisFlags = 0;
        }
        expressionFlagsBuffer |= thisFlags;
        addExpressionValueIfConcrete(thisFlags, concreteArgumentIndex, thisValue);
        updateCallStackFrameIfNeeded(thisFlags, isCallReceiver, callParameterIndex);
    }

    public static void processField(long jcInstructionId, Object instance, String fieldId, Object fieldValue,
                                    int concreteArgumentIndex, boolean isCallReceiver, int callParameterIndex) {
        updateLastInstructionIfNeeded(jcInstructionId);
        Byte fieldFlags = null;
        if (instance == null) {
            fieldFlags = staticFieldsFlags.get(fieldId);
        } else {
            HeapObjectDescriptor objectDescriptor = heapFlags.get(System.identityHashCode(instance));
            if (objectDescriptor != null) {
                fieldFlags = objectDescriptor.fields.get(fieldId);
            }
        }
        if (fieldFlags == null) {
            fieldFlags = 0;
        }
        expressionFlagsBuffer |= fieldFlags;
        addExpressionValueIfConcrete(fieldFlags, concreteArgumentIndex, fieldValue);
        updateCallStackFrameIfNeeded(fieldFlags, isCallReceiver, callParameterIndex);
    }

    public static void processByteField(long jcInstructionId, Object instance, String fieldId, byte fieldValue,
                                        int concreteArgumentIndex, boolean isCallReceiver, int callParameterIndex) {
        processField(jcInstructionId, instance, fieldId, fieldValue, concreteArgumentIndex, isCallReceiver, callParameterIndex);
    }

    public static void processShortField(long jcInstructionId, Object instance, String fieldId, short fieldValue,
                                         int concreteArgumentIndex, boolean isCallReceiver, int callParameterIndex) {
        processField(jcInstructionId, instance, fieldId, fieldValue, concreteArgumentIndex, isCallReceiver, callParameterIndex);
    }

    public static void processIntField(long jcInstructionId, Object instance, String fieldId, int fieldValue,
                                       int concreteArgumentIndex, boolean isCallReceiver, int callParameterIndex) {
        processField(jcInstructionId, instance, fieldId, fieldValue, concreteArgumentIndex, isCallReceiver, callParameterIndex);
    }

    public static void processLongField(long jcInstructionId, Object instance, String fieldId, long fieldValue,
                                        int concreteArgumentIndex, boolean isCallReceiver, int callParameterIndex) {
        processField(jcInstructionId, instance, fieldId, fieldValue, concreteArgumentIndex, isCallReceiver, callParameterIndex);
    }

    public static void processFloatField(long jcInstructionId, Object instance, String fieldId, float fieldValue,
                                         int concreteArgumentIndex, boolean isCallReceiver, int callParameterIndex) {
        processField(jcInstructionId, instance, fieldId, fieldValue, concreteArgumentIndex, isCallReceiver, callParameterIndex);
    }

    public static void processDoubleField(long jcInstructionId, Object instance, String fieldId, double fieldValue,
                                          int concreteArgumentIndex, boolean isCallReceiver, int callParameterIndex) {
        processField(jcInstructionId, instance, fieldId, fieldValue, concreteArgumentIndex, isCallReceiver, callParameterIndex);
    }

    public static void processCharField(long jcInstructionId, Object instance, String fieldId, char fieldValue,
                                        int concreteArgumentIndex, boolean isCallReceiver, int callParameterIndex) {
        processField(jcInstructionId, instance, fieldId, fieldValue, concreteArgumentIndex, isCallReceiver, callParameterIndex);
    }

    public static void processBooleanField(long jcInstructionId, Object instance, String fieldId, boolean fieldValue,
                                           int concreteArgumentIndex, boolean isCallReceiver, int callParameterIndex) {
        processField(jcInstructionId, instance, fieldId, fieldValue, concreteArgumentIndex, isCallReceiver, callParameterIndex);
    }

    public static void processArrayAccess(long jcInstructionId, Object arrayInstance, int arrayIndex, Object value,
                                          int concreteArgumentIndex, boolean isCallReceiver, int callParameterIndex) {
        updateLastInstructionIfNeeded(jcInstructionId);
        Byte elementFlags = null;
        HeapObjectDescriptor objectDescriptor = heapFlags.get(System.identityHashCode(arrayInstance));
        if (objectDescriptor != null) {
            elementFlags = objectDescriptor.arrayElements.get(arrayIndex);
        }
        if (elementFlags == null) {
            elementFlags = 0;
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
        if (isCallReceiver) {
            newCallStackFrame.thisDescriptor = flags;
        } else if (callParameterIndex != -1) {
            newCallStackFrame.arguments.put(callParameterIndex, flags);
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
        stackValuesFlags.last().localVariables.put(variableIndex, expressionFlagsBuffer);
    }

    public static void assignToArgument(int argumentIndex) {
        stackValuesFlags.last().arguments.put(argumentIndex, expressionFlagsBuffer);
    }

    public static void assignToField(Object instance, String fieldId) {
        if (instance == null) {
            staticFieldsFlags.put(fieldId, expressionFlagsBuffer);
        } else {
            int heapRef = System.identityHashCode(instance);
            HeapObjectDescriptor objectDescriptor = heapFlags.get(heapRef);
            if (objectDescriptor == null) {
                objectDescriptor = new HeapObjectDescriptor();
            }
            objectDescriptor.fields.put(fieldId, expressionFlagsBuffer);
            heapFlags.put(heapRef, objectDescriptor);
        }
    }

    public static void assignToArray(Object arrayInstance, int index) {
        int heapRef = System.identityHashCode(arrayInstance);
        HeapObjectDescriptor objectDescriptor = heapFlags.get(heapRef);
        if (objectDescriptor == null) {
            objectDescriptor = new HeapObjectDescriptor();
        }
        objectDescriptor.arrayElements.put(index, expressionFlagsBuffer);
        heapFlags.put(heapRef, objectDescriptor);
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
        HashMap<Integer, Byte> arguments = new HashMap<>();
        HashMap<Integer, Byte> localVariables = new HashMap<>();
        Byte thisDescriptor;
    }

    private static class HeapObjectDescriptor {
        private final HashMap<String, Byte> fields = new HashMap<>();
        private final HashMap<Integer, Byte> arrayElements = new HashMap<>();
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
            for (int i = 0; i < array.length; i++) {
                newArray[i] = array[i];
            }
            array = newArray;
        }
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

        static class Entry<K, V> {
            K key;
            V value;
            Entry<K, V> next;

            Entry(K key, V value) {
                this.key = key;
                this.value = value;
                this.next = null;
            }
        }

        public void put(K key, V value) {
            if (size >= table.length * LOAD_FACTOR) {
                resize();
            }

            int index = getIndex(key);
            Entry<K, V> entry = table[index];

            if (entry == null) {
                table[index] = new Entry<>(key, value);
                size++;
            } else {
                while (entry.next != null) {
                    if (entry.key.equals(key)) {
                        entry.value = value;
                        return;
                    }
                    entry = entry.next;
                }
                if (entry.key.equals(key)) {
                    entry.value = value;
                } else {
                    entry.next = new Entry<>(key, value);
                    size++;
                }
            }
        }

        public V get(K key) {
            int index = getIndex(key);
            Entry<K, V> entry = table[index];

            while (entry != null) {
                if (entry.key.equals(key)) {
                    return entry.value;
                }
                entry = entry.next;
            }

            return null;
        }

        private int getIndex(K key) {
            int h;
            int hash = (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
            return (table.length - 1) & hash;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private void resize() {
            Entry<K, V>[] oldTable = table;
            table = new Entry[table.length * 2];
            size = 0;

            for (Entry<K, V> entry : oldTable) {
                while (entry != null) {
                    put(entry.key, entry.value);
                    entry = entry.next;
                }
            }
        }

        public int size() {
            return size;
        }
    }
}

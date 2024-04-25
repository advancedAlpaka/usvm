package org.usvm.instrumentation.collector.trace;

public class ConcolicCollector {
    public static final ArrayList<InstructionInfo> symbolicInstructionsTrace = new ArrayList<>();
    private static final ArrayList<StackFrame> stackValuesFlags = new ArrayList<>();
    private static final IntKeyHashMap<Byte> heapFlags = new IntKeyHashMap<>();

    private static Long lastInstruction;
    private static byte expressionFlagsBuffer;


    public static void onEnterCall() {
        stackValuesFlags.add(new StackFrame());
    }

    public static void onExitCall() {
        stackValuesFlags.removeLast();
    }

    public static void applyFlagsFromLocalVariable(long jcInstructionId, int variableIndex) {
        updateLastInstructionIfNeeded(jcInstructionId);
        StackVariableDescriptor variableDescriptor = stackValuesFlags.last().localVariables.get(variableIndex);
        if (variableDescriptor != null) {
            expressionFlagsBuffer |= variableDescriptor.getFlags();
        }
    }

    public static void applyFlagsFromArgument(long jcInstructionId, int argumentIndex) {
        updateLastInstructionIfNeeded(jcInstructionId);
        expressionFlagsBuffer |= stackValuesFlags.last().arguments.get(argumentIndex).getFlags();
    }

    public static void applyFlagsFromThis(long jcInstructionId) {
        updateLastInstructionIfNeeded(jcInstructionId);
        expressionFlagsBuffer |= stackValuesFlags.last().thisDescriptor.getFlags();
    }

    private static void updateLastInstructionIfNeeded(long jcInstructionId) {
        if (lastInstruction == null || lastInstruction != jcInstructionId) {
            lastInstruction = jcInstructionId;
            expressionFlagsBuffer = 0;
        }
    }

    public static void assignFlagsToPrimitiveLocalVariable(int variableIndex) {
        stackValuesFlags.last().localVariables.put(variableIndex, createDescriptorForValue());
    }

    public static void assignFlagsToReferenceLocalVariable(int variableIndex, Object localVariableContent) {
        stackValuesFlags.last().localVariables.put(variableIndex, createDescriptorForReference(localVariableContent));
    }

    public static void assignFlagsToPrimitiveArgument(int argumentIndex) {
        stackValuesFlags.last().arguments.put(argumentIndex, createDescriptorForValue());
    }

    public static void assignFlagsToReferenceArgument(int argumentIndex, Object argumentContent) {
        stackValuesFlags.last().arguments.put(argumentIndex, createDescriptorForReference(argumentContent));
    }

    public static void assignFlagsToThis(Object thisContent) {
        stackValuesFlags.last().thisDescriptor = createDescriptorForReference(thisContent);
    }

    private static StackVariableDescriptor createDescriptorForReference(Object content) {
        int heapReference = System.identityHashCode(content);
        heapFlags.put(heapReference, expressionFlagsBuffer);

        return StackVariableDescriptor.forReference(heapReference);
    }

    private static StackVariableDescriptor createDescriptorForValue() {
        return StackVariableDescriptor.forValue(expressionFlagsBuffer);
    }

    public static class InstructionInfo {
        public long jcInstructionId;
        public ArrayList<ConcreteArgument> concreteArguments;

        public InstructionInfo(long jcInstructionId, ArrayList<ConcreteArgument> concreteArguments) {
            this.jcInstructionId = jcInstructionId;
            this.concreteArguments = concreteArguments;
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
        IntKeyHashMap<StackVariableDescriptor> arguments = new IntKeyHashMap<>();
        IntKeyHashMap<StackVariableDescriptor> localVariables = new IntKeyHashMap<>();
        StackVariableDescriptor thisDescriptor;
    }

    private static class StackVariableDescriptor {
        private final int heapReference;
        private final byte valueFlags;
        private final boolean isReference;

        private StackVariableDescriptor(int heapReference, byte valueFlags, boolean isReference) {
            this.heapReference = heapReference;
            this.valueFlags = valueFlags;
            this.isReference = isReference;
        }

        static StackVariableDescriptor forReference(int heapReference) {
            return new StackVariableDescriptor(heapReference, (byte) 0, true);
        }

        static StackVariableDescriptor forValue(byte flags) {
            return new StackVariableDescriptor(0, flags, false);
        }

        byte getFlags() {
            if (isReference) {
                Byte heapFlagsContent = heapFlags.get(heapReference);
                return heapFlagsContent == null ? 0 : heapFlagsContent;
            }
            return valueFlags;
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
            for (int i = 0; i < array.length; i++) {
                newArray[i] = array[i];
            }
            array = newArray;
        }
    }

    public static class IntKeyHashMap<V> {
        private static final int DEFAULT_CAPACITY = 16;
        private static final double LOAD_FACTOR = 0.75;

        private Entry<V>[] table;
        private int size = 0;

        @SuppressWarnings({"unchecked", "rawtypes"})
        public IntKeyHashMap() {
            this.table = new Entry[DEFAULT_CAPACITY];
        }

        static class Entry <V> {
            int key;
            V value;
            Entry<V> next;

            Entry(int key, V value) {
                this.key = key;
                this.value = value;
                this.next = null;
            }
        }

        public void put(int key, V value) {
            if (size >= table.length * LOAD_FACTOR) {
                resize();
            }

            int index = getIndex(key);
            Entry<V> entry = table[index];

            if (entry == null) {
                table[index] = new Entry<>(key, value);
                size++;
            } else {
                while (entry.next != null) {
                    if (entry.key == key) {
                        entry.value = value;
                        return;
                    }
                    entry = entry.next;
                }
                if (entry.key == key) {
                    entry.value = value;
                } else {
                    entry.next = new Entry<>(key, value);
                    size++;
                }
            }
        }

        public V get(int key) {
            int index = getIndex(key);
            Entry<V> entry = table[index];

            while (entry != null) {
                if (entry.key == key) {
                    return entry.value;
                }
                entry = entry.next;
            }

            return null;
        }

        private int getIndex(int key) {
            return key % table.length;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private void resize() {
            Entry<V>[] oldTable = table;
            table = new Entry[table.length * 2];
            size = 0;

            for (Entry<V> entry : oldTable) {
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

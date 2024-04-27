package org.usvm.instrumentation.collector.trace;

public class ConcolicCollector {
    public static final ArrayList<InstructionInfo> symbolicInstructionsTrace = new ArrayList<>();
    private static final ArrayList<StackFrame> stackValuesFlags = new ArrayList<>();
    private static final HashMap<Integer, HeapObjectDescriptor> heapFlags = new HashMap<>();
    private static final HashMap<String, Byte> staticFieldsFlags = new HashMap<>();


    private static Long lastInstruction;
    private static byte expressionFlagsBuffer;


    private static StackFrame newCallStackFrame = new StackFrame();
    public static void onEnterCall() {
        stackValuesFlags.add(newCallStackFrame);
        newCallStackFrame = new StackFrame();
    }

    public static void onExitCall() {
        stackValuesFlags.removeLast();
    }

    public static void applyFlagsFromLocalVariable(long jcInstructionId, int variableIndex,
                                                   boolean isThisArgument, int parameterIndex) {
        updateLastInstructionIfNeeded(jcInstructionId);
        Byte variableFlags = stackValuesFlags.last().localVariables.get(variableIndex);
        if (variableFlags != null) {
            expressionFlagsBuffer |= variableFlags;
            updateCallStackFrameIfNeeded(variableFlags, isThisArgument, parameterIndex);
        }
    }

    public static void applyFlagsFromArgument(long jcInstructionId, int argumentIndex,
                                              boolean isThisArgument, int parameterIndex) {
        updateLastInstructionIfNeeded(jcInstructionId);
        Byte argumentFlags = stackValuesFlags.last().arguments.get(argumentIndex);
        if (argumentFlags != null) {
            expressionFlagsBuffer |= argumentFlags;
            updateCallStackFrameIfNeeded(argumentFlags, isThisArgument, parameterIndex);
        }
    }

    public static void applyFlagsFromThis(long jcInstructionId, boolean isThisArgument, int parameterIndex) {
        updateLastInstructionIfNeeded(jcInstructionId);
        Byte thisFlags = stackValuesFlags.last().thisDescriptor;
        if (thisFlags != null) {
            expressionFlagsBuffer |= thisFlags;
            updateCallStackFrameIfNeeded(thisFlags, isThisArgument, parameterIndex);
        }
    }

    public static void applyFlagsFromField(long jcInstructionId, Object instance, String fieldId,
                                           boolean isThisArgument, int parameterIndex) {
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
        if (fieldFlags != null) {
            expressionFlagsBuffer |= fieldFlags;
            updateCallStackFrameIfNeeded(fieldFlags, isThisArgument, parameterIndex);
        }
    }

    public static void applyFlagsFromArrayAccess(long jcInstructionId, Object arrayInstance, int index,
                                                 boolean isThisArgument, int parameterIndex) {
        updateLastInstructionIfNeeded(jcInstructionId);
        Byte elementFlags = null;
        HeapObjectDescriptor objectDescriptor = heapFlags.get(System.identityHashCode(arrayInstance));
        if (objectDescriptor != null) {
            elementFlags = objectDescriptor.arrayElements.get(index);
        }
        if (elementFlags != null) {
            expressionFlagsBuffer |= elementFlags;
            updateCallStackFrameIfNeeded(elementFlags, isThisArgument, parameterIndex);
        }
    }

    private static void updateLastInstructionIfNeeded(long jcInstructionId) {
        if (lastInstruction == null || lastInstruction != jcInstructionId) {
            lastInstruction = jcInstructionId;
            expressionFlagsBuffer = 0;
        }
    }

    private static void updateCallStackFrameIfNeeded(Byte flags, boolean isThisArgument, int parameterIndex) {
        if (isThisArgument) {
            newCallStackFrame.thisDescriptor = flags;
        } else if (parameterIndex != -1) {
            newCallStackFrame.arguments.put(parameterIndex, flags);
        }
    }

    public static void assignFlagsToLocalVariable(int variableIndex) {
        stackValuesFlags.last().localVariables.put(variableIndex, expressionFlagsBuffer);
    }

    public static void assignFlagsToArgument(int argumentIndex) {
        stackValuesFlags.last().arguments.put(argumentIndex, expressionFlagsBuffer);
    }

    public static void assignFlagsToField(Object instance, String fieldId) {
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

    public static void assignFlagsToArray(Object arrayInstance, int index) {
        int heapRef = System.identityHashCode(arrayInstance);
        HeapObjectDescriptor objectDescriptor = heapFlags.get(heapRef);
        if (objectDescriptor == null) {
            objectDescriptor = new HeapObjectDescriptor();
        }
        objectDescriptor.arrayElements.put(index, expressionFlagsBuffer);
        heapFlags.put(heapRef, objectDescriptor);
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

        static class Entry <K, V> {
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

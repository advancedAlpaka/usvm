#!/bin/bash

# Build the JVM TI agent
echo "Building JVM TI agent..."
make clean
make

if [ $? -ne 0 ]; then
    echo "Failed to build agent"
    exit 1
fi

echo "Agent built successfully"

# Compile the test Java program
echo "Compiling test program..."
javac -cp "../../usvm-jvm-instrumentation/src/collectors/java" TestConcolic.java

if [ $? -ne 0 ]; then
    echo "Failed to compile test program"
    exit 1
fi

echo "Test program compiled successfully"

# Run the test program with the agent
echo "Running test program with JVM TI agent..."
java -agentpath:./libconcolic_agent.so -cp ".:../../usvm-jvm-instrumentation/src/collectors/java" TestConcolic
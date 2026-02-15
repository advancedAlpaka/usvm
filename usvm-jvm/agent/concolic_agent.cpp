#include <jvmti.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>

static jvmtiEnv *jvmti = NULL;
static jmethodID breakpoint_method = NULL;

// Callback for ClassPrepare - find our target method and set breakpoint
void JNICALL callbackClassPrepare(jvmtiEnv *jvmti_env,
                                   JNIEnv* jni_env,
                                   jthread thread,
                                   jclass klass) {
    char *class_sig = NULL;
    jvmti_env->GetClassSignature(klass, &class_sig, NULL);
    
    // Check if this is the ConcolicHelper class
    if (class_sig && strcmp(class_sig, "Lorg/usvm/instrumentation/ConcolicHelper;") == 0) {
        printf("Class ConcolicHelper loaded, setting up breakpoint...\n");
        
        // Find the breakpoint method
        jint method_count;
        jmethodID *methods;
        jvmti_env->GetClassMethods(klass, &method_count, &methods);
        
        for (int i = 0; i < method_count; i++) {
            char *method_name = NULL;
            char *method_sig = NULL;
            jvmti_env->GetMethodName(methods[i], &method_name, &method_sig, NULL);
            
            // Look for the private static breakpoint() method
            if (method_name && strcmp(method_name, "breakpoint") == 0 && 
                method_sig && strcmp(method_sig, "()V") == 0) {
                printf("Found breakpoint method\n");
                breakpoint_method = methods[i];
                
                // Set breakpoint at the beginning of the method (location 0)
                jvmtiError err = jvmti_env->SetBreakpoint(breakpoint_method, 0);
                if (err == JVMTI_ERROR_NONE) {
                    printf("Breakpoint set at breakpoint method entry\n");
                } else {
                    printf("Failed to set breakpoint: %d\n", err);
                }
                
                // Clean up method name and signature
                if (method_name) jvmti_env->Deallocate((unsigned char*)method_name);
                if (method_sig) jvmti_env->Deallocate((unsigned char*)method_sig);
                break;
            }
            
            // Clean up method name and signature
            if (method_name) jvmti_env->Deallocate((unsigned char*)method_name);
            if (method_sig) jvmti_env->Deallocate((unsigned char*)method_sig);
        }
        
        jvmti_env->Deallocate((unsigned char*)methods);
    }
    
    if (class_sig) jvmti_env->Deallocate((unsigned char*)class_sig);
}

// Callback for Breakpoint - called when breakpoint is hit
void JNICALL callbackBreakpoint(jvmtiEnv *jvmti_env,
                                 JNIEnv* jni_env,
                                 jthread thread,
                                 jmethodID method,
                                 jlocation location) {
    
    if (method == breakpoint_method) {
        printf("Breakpoint hit in ConcolicHelper.breakpoint() method\n");
        
        // Here you can add any custom logic you want to execute when the breakpoint is hit
        // For example, you could:
        // 1. Print stack trace
        // 2. Modify local variables
        // 3. Collect information about the execution context
        // 4. Communicate with other components
        
        // For now, we'll just print a message
        printf("ConcolicHelper breakpoint executed\n");
    }
}

// Agent initialization
JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
    printf("JVM TI ConcolicHelper Agent loaded\n");
    
    // Get JVMTI environment
    jint result = jvm->GetEnv((void **)&jvmti, JVMTI_VERSION_1_2);
    if (result != JNI_OK || jvmti == NULL) {
        printf("ERROR: Unable to access JVMTI!\n");
        return JNI_ERR;
    }
    
    // Set capabilities
    jvmtiCapabilities capabilities;
    memset(&capabilities, 0, sizeof(capabilities));
    capabilities.can_generate_breakpoint_events = 1;
    
    jvmtiError error = jvmti->AddCapabilities(&capabilities);
    if (error != JVMTI_ERROR_NONE) {
        printf("ERROR: Unable to set capabilities: %d\n", error);
        return JNI_ERR;
    }
    
    // Set event callbacks
    jvmtiEventCallbacks callbacks;
    memset(&callbacks, 0, sizeof(callbacks));
    callbacks.ClassPrepare = &callbackClassPrepare;
    callbacks.Breakpoint = &callbackBreakpoint;
    
    error = jvmti->SetEventCallbacks(&callbacks, sizeof(callbacks));
    if (error != JVMTI_ERROR_NONE) {
        printf("ERROR: Unable to set event callbacks: %d\n", error);
        return JNI_ERR;
    }
    
    // Enable ClassPrepare event to detect when our class loads
    error = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_CLASS_PREPARE, NULL);
    if (error != JVMTI_ERROR_NONE) {
        printf("ERROR: Unable to enable ClassPrepare event: %d\n", error);
        return JNI_ERR;
    }
    
    // Enable Breakpoint event
    error = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_BREAKPOINT, NULL);
    if (error != JVMTI_ERROR_NONE) {
        printf("ERROR: Unable to enable Breakpoint event: %d\n", error);
        return JNI_ERR;
    }
    
    printf("JVM TI ConcolicHelper Agent initialized\n");
    return JNI_OK;
}

JNIEXPORT void JNICALL Agent_OnUnload(JavaVM *vm) {
    printf("JVM TI ConcolicHelper Agent unloaded\n");
}
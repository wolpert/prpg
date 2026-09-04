package com.prpg.ecs;

/** Returns false to freeze input-driven entities (e.g. during dialogue or activities). */
public interface InputGate {
    /** Return true if input-driven entities should respond to input this frame. */
    boolean isInputActive();
}

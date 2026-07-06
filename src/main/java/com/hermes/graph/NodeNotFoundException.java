package com.hermes.graph;

/** Thrown when an operation references a system id that is not in the graph. */
public class NodeNotFoundException extends RuntimeException {
    public NodeNotFoundException(String message) {
        super(message);
    }
}

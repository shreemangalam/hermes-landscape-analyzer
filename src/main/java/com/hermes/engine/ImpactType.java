package com.hermes.engine;

/** How an affected system relates to the failed node. */
public enum ImpactType {
    /** One hop away from the failed system. */
    DIRECT,
    /** Two or more hops away — hit by the ripple, not the stone. */
    CASCADE
}

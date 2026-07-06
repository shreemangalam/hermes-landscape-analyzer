package com.hermes.engine;

/** Overall business priority of an incident, ITSM style. */
public enum Priority {
    P1("Critical business event — immediate action required"),
    P2("High impact — action required this shift"),
    P3("Moderate impact — schedule remediation"),
    P4("Informational — monitor only");

    private final String description;

    Priority(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}

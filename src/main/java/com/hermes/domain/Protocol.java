package com.hermes.domain;

/**
 * Transport/adapter protocol of an integration, mirroring the adapter types
 * commonly found on an SAP Integration Suite (CPI) tenant.
 */
public enum Protocol {
    RFC,
    IDOC,
    REST,
    SOAP,
    ODATA,
    FILE,
    SFTP,
    JDBC,
    AS2
}

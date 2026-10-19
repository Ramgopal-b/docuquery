package com.sedin.docuquery;

/**
 * DFC constant values read at runtime (not inlined at compile time), so the
 * pre-built jar works with any DFC version.
 */
final class Dfc {
    static final int DM_BOOLEAN = get("com.documentum.fc.common.IDfAttr", "DM_BOOLEAN", 0);
    static final int DM_INTEGER = get("com.documentum.fc.common.IDfAttr", "DM_INTEGER", 1);
    static final int DM_STRING = get("com.documentum.fc.common.IDfAttr", "DM_STRING", 2);
    static final int DM_ID = get("com.documentum.fc.common.IDfAttr", "DM_ID", 3);
    static final int DM_TIME = get("com.documentum.fc.common.IDfAttr", "DM_TIME", 4);
    static final int DM_DOUBLE = get("com.documentum.fc.common.IDfAttr", "DM_DOUBLE", 5);
    static final int DF_READ_QUERY = get("com.documentum.fc.client.IDfQuery", "DF_READ_QUERY", 0);
    static final int DF_QUERY = get("com.documentum.fc.client.IDfQuery", "DF_QUERY", 1);

    private Dfc() {}

    private static int get(String cls, String field, int def) {
        try {
            return Class.forName(cls).getField(field).getInt(null);
        } catch (Throwable t) {
            return def;
        }
    }
}

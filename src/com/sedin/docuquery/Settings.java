package com.sedin.docuquery;

/** Global settings that must be readable without loading any DFC-dependent class. */
final class Settings {
    /** Extras > "Edit values in result grid". */
    static boolean editInGrid = !"false".equals(Prefs.get("grid.edit", "true"));

    private Settings() {}
}

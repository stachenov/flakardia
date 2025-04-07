module flakardia {
    requires java.base;
    requires java.desktop;
    requires java.prefs;
    requires kotlin.stdlib;
    requires darklaf.core;
    requires kotlinx.coroutines.core;
    requires kotlinx.serialization.core;
    requires kotlinx.serialization.json;
    requires jortho;
    exports name.tachenov.flakardia.storage;
}

module flakardia {
    requires java.prefs;
    requires kotlin.stdlib;
    requires darklaf.core;
    requires kotlinx.coroutines.core;
    requires kotlinx.serialization.core;
    requires kotlinx.serialization.json;
    requires jortho;
    requires org.jetbrains.annotations;
    requires org.apache.commons.text;
    exports name.tachenov.flakardia.storage;
}

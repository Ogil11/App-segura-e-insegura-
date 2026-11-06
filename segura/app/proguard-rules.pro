# Reglas de R8 de MiBanco Segura.
# OkHttp, AndroidX y Compose ya traen sus propias reglas (consumer rules).

# SEGURO: elimina las llamadas a Log.v/d/i en release para no filtrar datos
# en logcat (MASVS-STORAGE-2).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

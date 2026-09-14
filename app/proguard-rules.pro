# Reglas de ProGuard/R8 específicas de SIMONA.
# PLAN_MEJORAS_20.md, punto 5: se activó isMinifyEnabled/isShrinkResources
# en el build de release. La app en sí no usa reflexión propia ni Gson/Moshi
# (ver sección 4.2 del contexto del proyecto — "sin dependencias externas"),
# así que la única regla necesaria es para WorkManager: instancia
# AlertaHuertasWorker por reflexión (Class.forName + constructor
# (Context, WorkerParameters)) al disparar el trabajo periódico programado
# en SimonaApp, y esa instanciación no es visible para el analizador
# estático de R8 como uso real de la clase/constructor.
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# org.json (org.json.JSONObject/JSONArray) es parte del SDK de Android, no
# una librería externa — no necesita reglas propias.

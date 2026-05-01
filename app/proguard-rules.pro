# Tidal WearOS ProGuard Rules

# Ktor references JVM management classes not available on Android
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean

# Правила для R8/ProGuard (release-сборка).

# Room генерирует код на этапе компиляции, но сохраним модели явно —
# это защищает от изменений имён при обфускации.
-keep class com.paysplit.app.data.model.** { *; }

# Kotlin
-dontwarn org.jetbrains.annotations.**
-keepclassmembers class kotlin.Metadata { *; }

# Vico
-dontwarn com.patrykandpatrick.vico.**

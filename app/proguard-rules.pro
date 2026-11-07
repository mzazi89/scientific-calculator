# Keep the calculator engine untouched by R8 (pure Kotlin, no reflection,
# but keeping names makes crash reports readable).
-keep class com.mzazi.scientificcalculator.CalculatorEngine { *; }
-keep class com.mzazi.scientificcalculator.CalculatorException { *; }

# Kotlin metadata
-keep class kotlin.Metadata { *; }

# Scientific Calculator

A fast, offline scientific calculator for Android, written in Kotlin with a hand-rolled
expression parser. No ads, no analytics, no network permission — just math.

```
2 + 3 × 4        →  14
sin(30)          →  0.5          (degrees)
2^3^2            →  512          (power is right-associative)
5!               →  120
0.5!             →  0.886226925  (Γ function, so fractions work)
2π               →  6.283185307
```

## Features

**Math**
- `+ − × ÷`, parentheses, `x²`, `xʸ`, unary minus
- Trigonometry: `sin`, `cos`, `tan` plus inverses, in **DEG** or **RAD**
- Logarithms: `ln`, `log` (base 10); inverse mode gives `eˣ` and `10ˣ`
- `√`, `∛`, `n!` (Lanczos gamma, so `0.5!` works), `%`, `π`, `e`
- Implicit multiplication: `2π`, `3(4+1)`, `2sin(30)` all parse correctly
- 12-significant-digit formatting with automatic scientific notation

**App**
- Live preview of the result while you type
- Calculation history (100 entries, tap any row to reuse the result)
- Memory register: `MC`, `MR`, `M+`, `M−`, with an on-screen `M` indicator
- `ANS` reuses the previous answer; state survives rotation and app restarts
- Material-style dark UI, haptic feedback, accessible content descriptions

## Install

Download the latest `app-release.apk` from
[Releases](https://github.com/mzazi89/scientific-calculator/releases) and open it on your
phone (allow "install from unknown sources" when prompted).

Requires Android 8.0 (API 26) or newer.

## Build from source

```bash
git clone https://github.com/mzazi89/scientific-calculator.git
cd scientific-calculator

# Debug build, installable on a connected device
./gradlew installDebug

# Release APK (signed with the debug key so it can be side-loaded)
./gradlew assembleRelease
# → app/build/outputs/apk/release/app-release.apk
```

You need JDK 17+ and the Android SDK with platform 34 / build-tools 34.0.0.
`ANDROID_HOME` (or a `local.properties` with `sdk.dir`) must point at your SDK.

Run the unit tests with:

```bash
./gradlew test
```

## How it works

| File | Responsibility |
| --- | --- |
| `CalculatorEngine.kt` | Tokenizer + recursive-descent parser + evaluator. Pure Kotlin, no Android dependencies, fully unit-tested. |
| `MainActivity.kt` | Keypad wiring, input state machine, live preview, history dialog, memory register. |
| `CalculatorStore.kt` | `SharedPreferences` persistence for history, memory, angle mode and last answer. |
| `HistoryFormat.kt` | Re-encodes stored results (including `1.5e-10`) into parseable expressions. |

The grammar the parser implements:

```
expression := term (('+' | '-') term)*
term       := unary (('*' | '/') unary)*
unary      := ('-' | '+') unary | power
power      := postfix ('^' unary)?          // right associative
postfix    := primary ('!' | '%')*
primary    := number | constant | '(' expression ')' | function primary
```

`CalculatorEngine` never mutates state, throws `CalculatorException` for anything the user
should see ("Cannot divide by zero", "Domain error", "Overflow"), and is covered by 20 unit
tests in `app/src/test`.

## CI

`.github/workflows/android.yml` runs the unit tests and builds both APKs on every push and
pull request. Pushing a `v*` tag publishes a GitHub release with the APKs attached.

## License

MIT — see [LICENSE](LICENSE).

# Project-specific ProGuard / R8 rules (appended to the Android defaults).

-verbose

-dontwarn android.support.**
-dontwarn com.badlogic.gdx.backends.android.AndroidFragmentApplication

# SnakeYAML references java.beans.* for its bean-introspection path. We use
# BeanAccess.FIELD in ConfigLoader so that path never runs at runtime, but
# R8 still needs to be told the missing references are intentional.
-dontwarn java.beans.**
# SnakeYAML reflectively populates the public fields of YAML POJOs; without
# this rule, R8 strips them on release Android builds and configs deserialize
# to zero-filled objects, crashing on first launch. The wildcard matches any
# package whose path contains a `config` segment, which is why every YAML POJO
# in this project lives in a leaf `config` package. If you rename the package
# containing your YAML POJOs to something without `.config.`, update this pattern.
-keep class **.config.** { *; }

# Needed by the gdx-controllers official extension.
-keep class com.badlogic.gdx.controllers.android.AndroidControllers

# Scene2D UI (skins are loaded reflectively).
-keep public class com.badlogic.gdx.scenes.scene2d.** { *; }
-keep public class com.badlogic.gdx.graphics.g2d.BitmapFont { *; }
-keep public class com.badlogic.gdx.graphics.Color { *; }

# Retrace support; see https://developer.android.com/build/shrink-code#retracing
-keepattributes LineNumberTable,SourceFile
-renamesourcefileattribute SourceFile

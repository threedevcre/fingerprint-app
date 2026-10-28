# The app's own classes are small and referenced from the manifest and layouts; keep them
# intact so release behaviour matches what was tested. Libraries are still shrunk.
-keep class com.posfingerprint.app.** { *; }

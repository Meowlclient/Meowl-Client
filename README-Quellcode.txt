Meowl Client + Meowl Server – Quellcode-Sicherung
=================================================
project/        Client-Quellcode (Fabric, MC 1.21.11, Yarn-Namen)
build.sh        baut dist/meowl-client-<version>.jar (javac + tiny-remapper, ohne Gradle)
cc.sh           schnelle Kompilierprüfung
tools/          Remap.java (named->intermediary), Widen.java + aw_named.py (Access Widener für den Compile-Classpath)
server/         Meowl-Server-Quellcode; build-server.sh erzeugt dist/meowl-server-setup.sh
test/           MixinCheck, Vorschau-Renderer der AFK-Sternenschlacht, Smoke-Test
libs/ (nicht enthalten): Minecraft-Jar (Yarn), mappings.tiny, Fabric API, Loader, Mixin, tiny-remapper –
alles aus %USERPROFILE%\.gradle\caches und .minecraft\libraries.

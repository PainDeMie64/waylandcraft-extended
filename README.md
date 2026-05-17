![waylandcraft banner](/assets/title_scaled.png)

Wayland Compositor in Minecraft

[Demo video](https://youtu.be/cTkEM7b0IQw)

## System dependencies
- OS: Linux
- Minecraft 26.1.2
- Fabric mod loader
- xkbcommon library 1.11.0
- xkbcommon tools (xkbcli)

## Compilation
You need a Rust development environment and a Java 25 SDK.
Change into the `native` subdirectory and run `cargo build`.
This compiles the native code and puts the shared library into `native/target/debug/libwaylandcraft.so`.
From here you can just return to the main directory and then run `./gradlew build` to produce the jar file
in `build/libs/` or run `./gradlew runClient` to run it in a development environment.

## Disclaimer
This compositor still has lots of issues and bugs. Use it at your own risk or whatever.

The entire project was written **without the usage of any generative AI**.

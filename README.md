![WaylandCraft Extended banner](/assets/title_extended_scaled.png)

# WaylandCraft Extended

**WaylandCraft Extended** is a feature-focused fork of [WaylandCraft](https://github.com/EVV1E/waylandcraft) that turns Minecraft into an experimental Linux desktop space.

It expands the original in-game Wayland compositor with Xwayland support, Steam and Proton app support, direct keyboard and pointer capture, real client cursor rendering, persistent in-world monitor layouts, workspace-aware desktop controls, and a physical desktop panel for launching, placing, focusing, minimizing, and closing windows from inside the world.

[Upstream WaylandCraft](https://github.com/EVV1E/waylandcraft) remains the source for the original project, base documentation, screenshots, build instructions, system requirements, general usage, and the original [Modrinth project](https://modrinth.com/mod/waylandcraft). This README documents what is different in this fork.

## What This Fork Adds

### Xwayland, Steam, and Proton support

- Adds Xwayland support so X11 applications can run inside WaylandCraft.
- Integrates X11 windows into the existing WaylandCraft window model, window manager, in-world displays, items, focus handling, resizing, fullscreen, and close actions.
- Allows Steam and Proton-launched games to appear as WaylandCraft windows instead of requiring native Wayland support.
- Keeps native Wayland applications working through the original WaylandCraft compositor path.

### In-world monitor controls

- Adds hover controls directly on placed monitors:
  - move
  - close
  - outer corner handles for resizing the in-world monitor size
  - inner corner handles for resizing the client/app where the app allows it
- Adds a focus outline around active or hovered monitors.
- Keeps monitor controls visible while aiming from the window content to the controls.
- Disables app/client resize controls for fullscreen apps while still allowing monitor-size resizing.
- Removes the artificial maximum in-world monitor presentation size.

### Placement, snapping, and rotation

- Adds a configurable snapping toggle for monitor placement.
- Snapping uses whole Minecraft block coordinates and 5 degree orientations.
- Adds configurable monitor rotation mode.
- While rotating, `X`, `Y`, and `Z` constrain rotation to one axis.
- Keeps existing grab behavior for moving windows from the window manager.

### Keyboard and pointer input

- Adds configurable normal keyboard capture, with the same key also releasing capture.
- Adds configurable hard capture for applications that need relative mouse movement or stronger pointer capture.
- Adds real Wayland client cursor surface support during hard capture, including cursor hotspots, client-hidden cursors, custom game cursors, and a built-in default cursor fallback.
- Frees `Escape` so it can be sent to focused applications instead of always being reserved for leaving capture.
- Fixes several X11 pointer focus, stacking, and grab-routing paths so clicks are delivered to the intended window.

## Fixes Compared To Upstream

- Fixes X11 fullscreen presentation scaling so fullscreen X11 clients are aspect-fit into WaylandCraft displays instead of being shown in the wrong corner or over the UI.
- Fixes dmabuf texture lifetime handling for X11 windows, preventing stale or random Minecraft textures from appearing in app windows.
- Fixes X11 focus/stacking behavior needed for Steam and Proton windows to receive input reliably.
- Releases pointer capture when opening the window manager and refocuses pointer targets before routing clicks.
- Fixes hard pointer capture cursor feedback by rendering actual client-provided cursor surfaces instead of a compositor-side placeholder.
- Cleans up embedded app processes when Minecraft shuts down, reducing leftover Steam/Proton child processes after crashes or forced exits.
- Adds X11 lifecycle diagnostics for hidden, unmapped, destroyed, or untracked X11 windows.

## Important Launch Setting

On NVIDIA systems, launch Minecraft with this environment variable:

```sh
__GL_THREADED_OPTIMIZATIONS=0
```

This avoids known EGL/context issues seen while testing WaylandCraft Extended. In Modrinth App, add it under the instance settings' custom environment variables.

## App Discovery

If the app launcher only shows Minecraft or the launcher itself, use a native system package for your Minecraft launcher rather than a sandboxed package such as Flatpak. Sandboxed launchers can hide the desktop application list from WaylandCraft.

## Debugging Flags

The fork includes opt-in diagnostics for development and bug reports:

- `WAYLANDCRAFT_DEBUG_X11=1`: native Xwayland/XWM lifecycle logging.
- `WAYLANDCRAFT_DEBUG_WINDOWS=1`: Java-side window, monitor, routing, and presentation logging.
- `WAYLANDCRAFT_DEBUG_INPUT=1`: low-level native input and X11 event diagnostics.
- `WAYLANDCRAFT_DEBUG_TEXTURES=1`: texture, dmabuf, framebuffer, and TextureManager lifecycle logging.
- `WAYLANDCRAFT_DEBUG_OVERLAY=1`: visual overlay for presentation/fit debugging.

## Documentation

Use upstream WaylandCraft documentation for:

- installation
- Minecraft and Fabric setup
- base keybinds and screens
- system dependencies
- build instructions
- original FAQ and known limitations

Start here: [EVV1E/waylandcraft](https://github.com/EVV1E/waylandcraft)

Fork-specific controls are configurable in Minecraft's Controls menu under `WaylandCraft Extended`:

- `V`: app launcher
- `B`: window manager
- toggle normal keyboard capture
- toggle hard keyboard/pointer capture
- toggle monitor rotation mode
- place the desktop panel
- toggle monitor snapping
- rotation axis controls while monitor rotation is active

## Status

WaylandCraft Extended is experimental and Linux-only. Xwayland, Steam, Proton, and in-world monitor controls are active fork features and may change quickly.

This repository is intended to be easy to find and share as the Xwayland/Steam/Proton-focused WaylandCraft fork. The recommended public repository name is `waylandcraft-extended`.

## Contribution Policy

All contributions must comply with the GPLv3 license. See [LICENSE](LICENSE).

This fork may contain and accept AI-assisted changes. Contributors should review, test, and take responsibility for submitted code.

The upstream WaylandCraft project has a no-generative-AI contribution policy, so changes from this fork may not be suitable for upstream submission unless they are independently rewritten and reviewed under upstream's rules.

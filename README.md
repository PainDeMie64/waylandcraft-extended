# waylandcraft
Wayland Compositor in Minecraft

## Implemented protocols
- core
	- wl_compositor
	- wl_subcompositor
	- wl_data_device_manager *(only selection)*
	- wl_shm
	- wl_seat *(pointer, keyboard)*
	- wl_output
- xdg-shell (xdg_wm_base)
- viewporter (wp_viewporter)
- single-pixel-buffer-v1 (wp_single_pixel_buffer_manager_v1)
- linux-dmabuf-v1 (zwp_linux_dmabuf_v1)
- cursor-shape-v1 (wp_cursor_shape_manager_v1) *(only partially)*
- pointer-constraints-unstable-v1 (zwp_pointer_constraints_v1) *(only locked pointers)*
- relative-pointer-unstable-v1 (zwp_relative_pointer_manager_v1)

## System dependencies
- OS: Linux
- Minecraft 1.20.6
- Fabric mod loader
- xkbcommon library 1.11.0
- xkbcommon tools (xkbcli)

## Disclaimer
This is all authentic human-written code and it has several bugs and quirks, as such this compositor
is not generally meant for everyday use. Use it at your own risk or whatever.

This project was written entirely for the fun of coding **without the usage of any generative AI** tools.

use crate::bridge::BridgeState;
use crate::ddm::WLCDataState;
use crate::egl::EGLHelper;
use crate::output::WLCOutput;
use crate::seat::WLCSeatState;
use crate::x11_probe::X11InputProbe;
use crate::xdg_spec::XDGSpecHelper;
use smithay::{
    backend::allocator::dmabuf::Dmabuf,
    delegate_compositor, delegate_dmabuf, delegate_shm,
    delegate_single_pixel_buffer, delegate_viewporter, delegate_xdg_shell,
    delegate_xwayland_shell,
    input::{
        Seat, SeatHandler, SeatState,
        dnd::DndGrabHandler,
        keyboard::{KeyboardHandle, XkbConfig},
        pointer::CursorImageStatus,
    },
    reexports::{
        calloop::{
            self, EventLoop, LoopHandle, generic::Generic as GenericEvent,
        },
        wayland_protocols::xdg::shell::server::xdg_toplevel::ResizeEdge,
        wayland_server::{
            self, Display, DisplayHandle, Resource,
            backend::{ClientData, ClientId, DisconnectReason},
            protocol::{
                wl_buffer::WlBuffer, wl_output::WlOutput, wl_seat::WlSeat,
                wl_surface::WlSurface,
            },
        },
    },
    utils::{Logical, Rectangle, Serial},
    wayland::{
        buffer::BufferHandler,
        compositor::{
            CompositorClientState, CompositorHandler, CompositorState,
        },
        dmabuf::{
            DmabufFeedbackBuilder, DmabufGlobal, DmabufHandler, DmabufState,
            ImportNotifier,
        },
        shell::xdg::{
            PopupSurface, PositionerState, ToplevelSurface, XdgShellHandler,
            XdgShellState,
        },
        shm::{ShmHandler, ShmState},
        single_pixel_buffer::SinglePixelBufferState,
        socket::ListeningSocketSource,
        viewporter::ViewporterState,
        xwayland_shell::{XWaylandShellHandler, XWaylandShellState},
    },
    xwayland::{
        X11Surface, X11Wm, XWayland, XWaylandClientData, XWaylandEvent,
        xwm::{
            Reorder, ResizeEdge as X11ResizeEdge, WmWindowType, XwmHandler,
            XwmId,
        },
    },
};
use std::ffi::OsString;
use std::process::Stdio;
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};
use std::time::Duration;
use x11rb::connection::Connection as _;
use x11rb::protocol::xproto::{
    AtomEnum as X11AtomEnum, ConnectionExt as _, PropMode as X11PropMode,
    Window as X11Window,
};
use x11rb::rust_connection::RustConnection;
use x11rb::wrapper::ConnectionExt as _;

mod bridge;
mod ddm;
mod egl;
mod input_trace;
mod output;
mod process;
mod seat;
mod svg;
mod utils;
mod x11_probe;
mod xdg_spec;

static DEBUG_INPUT_OVERRIDE: AtomicBool = AtomicBool::new(false);

fn debug_x11_enabled() -> bool {
    std::env::var("WAYLANDCRAFT_DEBUG_X11")
        .map(|value| {
            value == "1"
                || value.eq_ignore_ascii_case("true")
                || value.eq_ignore_ascii_case("yes")
        })
        .unwrap_or(false)
}

pub(crate) fn debug_input_enabled() -> bool {
    if input_trace::enabled() {
        return true;
    }
    if DEBUG_INPUT_OVERRIDE.load(Ordering::Relaxed) {
        return true;
    }
    std::env::var("WAYLANDCRAFT_DEBUG_INPUT")
        .or_else(|_| std::env::var("WAYLANDCRAFT_DEBUG_WINDOWS"))
        .map(|value| {
            value == "1"
                || value.eq_ignore_ascii_case("true")
                || value.eq_ignore_ascii_case("yes")
        })
        .unwrap_or(false)
}

pub(crate) fn set_debug_input_enabled(enabled: bool) {
    DEBUG_INPUT_OVERRIDE.store(enabled, Ordering::Relaxed);
    if enabled {
        println!("WLC input native debug enabled by Java bridge");
    }
}

fn x11_atom(conn: &RustConnection, name: &str) -> Result<u32, String> {
    conn.intern_atom(false, name.as_bytes())
        .map_err(|err| err.to_string())?
        .reply()
        .map_err(|err| err.to_string())
        .map(|reply| reply.atom)
}

fn cleanup_x11_root_refs(
    display: u32,
    stale_windows: &[X11Window],
) -> Result<(), String> {
    let display_name = format!(":{display}");
    let (conn, screen_num) = RustConnection::connect(Some(&display_name))
        .map_err(|err| err.to_string())?;
    let root = conn.setup().roots[screen_num].root;
    let client_list = x11_atom(&conn, "_NET_CLIENT_LIST")?;
    let client_list_stacking = x11_atom(&conn, "_NET_CLIENT_LIST_STACKING")?;

    let changed_clients = cleanup_x11_root_list(
        &conn,
        root,
        client_list,
        "_NET_CLIENT_LIST",
        stale_windows,
    )?;
    let changed_stacking = cleanup_x11_root_list(
        &conn,
        root,
        client_list_stacking,
        "_NET_CLIENT_LIST_STACKING",
        stale_windows,
    )?;

    if debug_x11_enabled() && (changed_clients || changed_stacking) {
        eprintln!(
            "WLC X11 root cleanup display=:{display} ids={stale_windows:x?} clientListChanged={changed_clients} stackingChanged={changed_stacking}"
        );
    }

    conn.flush().map_err(|err| err.to_string())
}

fn cleanup_x11_root_list(
    conn: &RustConnection,
    root: X11Window,
    property: u32,
    property_name: &str,
    stale_windows: &[X11Window],
) -> Result<bool, String> {
    let reply = conn
        .get_property(false, root, property, X11AtomEnum::WINDOW, 0, u32::MAX)
        .map_err(|err| err.to_string())?
        .reply()
        .map_err(|err| err.to_string())?;
    let Some(values) = reply.value32() else {
        return Ok(false);
    };
    let old: Vec<X11Window> = values.collect();
    let new: Vec<X11Window> = old
        .iter()
        .copied()
        .filter(|window| !stale_windows.contains(window))
        .collect();

    if old.len() == new.len() {
        return Ok(false);
    }

    conn.change_property32(
        X11PropMode::REPLACE,
        root,
        property,
        X11AtomEnum::WINDOW,
        &new,
    )
    .map_err(|err| err.to_string())?;

    if debug_x11_enabled() {
        eprintln!(
            "WLC X11 root property cleanup property={property_name} old={old:x?} new={new:x?}"
        );
    }

    Ok(true)
}

pub(crate) fn log_native_stderr(message: &str) {
    unsafe {
        let _ = libc::write(
            libc::STDERR_FILENO,
            message.as_ptr().cast::<libc::c_void>(),
            message.len(),
        );
        let _ = libc::write(
            libc::STDERR_FILENO,
            b"\n".as_ptr().cast::<libc::c_void>(),
            1,
        );
    }
}

pub(crate) struct WaylandCraft<'a> {
    pub state: WLCState,
    pub event_loop: EventLoop<'a, WLCState>,
    pub bridge: BridgeState,
    pub egl: EGLHelper,
    pub xdg: XDGSpecHelper,
}

pub struct WLCState {
    pub display_handle: DisplayHandle,
    pub socket: OsString,
    pub compositor_state: CompositorState,
    pub shm_state: ShmState,
    pub xdg_state: XdgShellState,
    pub viewporter_state: ViewporterState,
    pub single_pixel_buffer_state: SinglePixelBufferState,
    pub dmabuf_state: DmabufState,
    pub dmabuf_global: DmabufGlobal,
    pub requests: WindowRequests,
    pub seat: WLCSeatState,
    pub data: WLCDataState,
    pub output: WLCOutput,
    pub xwayland_shell_state: XWaylandShellState,
    pub xwayland_seat_state: SeatState<WLCState>,
    pub xwayland_seat: Seat<WLCState>,
    pub xwayland_keyboard: Option<KeyboardHandle<WLCState>>,
    pub xwm: Option<X11Wm>,
    pub xdisplay: Option<u32>,
    pub x11_windows: Vec<Box<X11Surface>>,
    pub focused_x11_window: Option<X11Surface>,
    pub(crate) x11_input_probe: Option<X11InputProbe>,
}

#[derive(Default)]
pub struct WindowRequests {
    pub minimize: Vec<ToplevelSurface>,
    pub maximize: Vec<ToplevelSurface>,
    pub unmaximize: Vec<ToplevelSurface>,
    pub fullscreen: Vec<ToplevelSurface>,
    pub unfullscreen: Vec<ToplevelSurface>,
    pub x11_minimize: Vec<X11Surface>,
    pub move_interactive: Vec<Serial>,
    pub resize_interactive: Vec<(Serial, ResizeEdge)>,
}

impl WLCState {
    fn new(disp: DisplayHandle, egl: &EGLHelper) -> Self {
        let compositor_state = CompositorState::new::<WLCState>(&disp);
        let shm_state = ShmState::new::<WLCState>(&disp, vec![]);
        let xdg_state = XdgShellState::new::<WLCState>(&disp);
        let viewporter_state = ViewporterState::new::<WLCState>(&disp);
        let single_pixel_buffer_state =
            SinglePixelBufferState::new::<WLCState>(&disp);

        let mut dmabuf_state = DmabufState::new();
        let dmabuf_global = init_dmabuf(&disp, &mut dmabuf_state, egl);

        let seat = WLCSeatState::new();
        seat.create_globals(&disp);

        let data = WLCDataState::new(&disp);
        data.create_global();

        let output = WLCOutput::new(&disp);
        output.create_global();

        let xwayland_shell_state = XWaylandShellState::new::<WLCState>(&disp);

        let mut xwayland_seat_state = SeatState::new();
        let mut xwayland_seat =
            xwayland_seat_state.new_seat("waylandcraft-xwayland");
        let xwayland_keyboard = xwayland_seat
            .add_keyboard(XkbConfig::default(), 200, 25)
            .ok();

        Self {
            display_handle: disp.clone(),
            socket: OsString::new(),
            compositor_state,
            shm_state,
            xdg_state,
            viewporter_state,
            single_pixel_buffer_state,
            dmabuf_state,
            dmabuf_global,
            requests: WindowRequests::default(),
            seat,
            data,
            output,
            xwayland_shell_state,
            xwayland_seat_state,
            xwayland_seat,
            xwayland_keyboard,
            xwm: None,
            xdisplay: None,
            x11_windows: vec![],
            focused_x11_window: None,
            x11_input_probe: None,
        }
    }

    pub(crate) fn should_track_x11_window(window: &X11Surface) -> bool {
        Self::x11_track_reject_reason(window).is_none()
    }

    fn x11_track_reject_reason(window: &X11Surface) -> Option<&'static str> {
        if window.is_override_redirect() {
            return Some("override-redirect");
        }

        if !matches!(
            window.window_type(),
            None | Some(WmWindowType::Dialog) | Some(WmWindowType::Normal)
        ) {
            return Some("unsupported-window-type");
        }

        None
    }

    fn track_x11_window(&mut self, window: X11Surface) {
        if let Some(reason) = Self::x11_track_reject_reason(&window) {
            if debug_x11_enabled() {
                eprintln!(
                    "WLC X11 reject track reason={} {}",
                    reason,
                    self.describe_x11_window(&window)
                );
            }
            self.untrack_x11_window(&window);
            return;
        }

        if !self.x11_windows.iter().any(|w| **w == window) {
            let label = self.describe_x11_window(&window);
            if debug_x11_enabled() {
                eprintln!("WLC X11 track {label}");
            }
            if debug_input_enabled() {
                println!("WLC input X11 track {label}");
                input_trace::event(
                    "native-xwm",
                    "track",
                    &format!("\"window\":{}", input_trace::json(&label)),
                );
                if let Some(probe) = &self.x11_input_probe {
                    probe.watch_window(
                        window.window_id(),
                        window.mapped_window_id(),
                        label,
                    );
                }
            }
            self.x11_windows.push(Box::new(window));
        }
    }

    fn untrack_x11_window(&mut self, window: &X11Surface) {
        if debug_x11_enabled()
            && self.x11_windows.iter().any(|w| **w == *window)
        {
            eprintln!("WLC X11 untrack {}", self.describe_x11_window(window));
        }
        if self
            .focused_x11_window
            .as_ref()
            .is_some_and(|focused| focused == window)
        {
            if debug_input_enabled() {
                println!(
                    "WLC input X11 focus cleared removed {}",
                    self.describe_x11_window(window)
                );
                input_trace::event(
                    "native-xwm",
                    "focus_cleared",
                    &format!(
                        "\"reason\":\"removed\",\"window\":{}",
                        input_trace::json(&self.describe_x11_window(window))
                    ),
                );
            }
            self.focused_x11_window = None;
        }
        self.x11_windows.retain(|w| **w != *window);
    }

    pub(crate) fn describe_x11_window(&self, window: &X11Surface) -> String {
        let surface = window
            .wl_surface()
            .as_ref()
            .map(|surface| format!("{:?}", surface.id()))
            .unwrap_or_else(|| "none".into());
        format!(
            "handle={:p} xid=0x{:x} mapped_xid={} title={:?} class={:?} geometry={:?} type={:?} fullscreen={} mapped={} wl_surface={}",
            window,
            window.window_id(),
            window
                .mapped_window_id()
                .map(|id| format!("0x{id:x}"))
                .unwrap_or_else(|| "none".into()),
            window.title(),
            window.class(),
            window.geometry(),
            window.window_type(),
            window.is_fullscreen(),
            window.is_mapped(),
            surface,
        )
    }

    pub(crate) fn describe_x11_surface_owner(
        &self,
        surface: &WlSurface,
    ) -> Option<String> {
        self.x11_windows
            .iter()
            .find(|window| {
                window
                    .wl_surface()
                    .as_ref()
                    .is_some_and(|candidate| candidate == surface)
            })
            .map(|window| self.describe_x11_window(window))
    }

    fn output_x11_geometry(&self) -> Rectangle<i32, Logical> {
        Rectangle::from_size(self.output.bounds())
    }

    fn fullscreen_x11_geometry(&self) -> Rectangle<i32, Logical> {
        Rectangle::from_size(self.output.size())
    }

    fn fullscreen_x11_geometry_for(
        &self,
        window: &X11Surface,
    ) -> Rectangle<i32, Logical> {
        let mut geometry = window.geometry();

        if geometry.size.w <= 1 || geometry.size.h <= 1 {
            return self.fullscreen_x11_geometry();
        }

        geometry.loc.x = 0;
        geometry.loc.y = 0;
        geometry
    }

    fn clamped_x11_geometry(
        &self,
        mut geometry: Rectangle<i32, Logical>,
    ) -> Rectangle<i32, Logical> {
        let bounds = self.output.bounds();
        geometry.size.w = geometry.size.w.max(1).min(bounds.w.max(1));
        geometry.size.h = geometry.size.h.max(1).min(bounds.h.max(1));
        geometry.loc.x = geometry.loc.x.max(0).min(bounds.w - geometry.size.w);
        geometry.loc.y = geometry.loc.y.max(0).min(bounds.h - geometry.size.h);
        geometry
    }

    fn cleanup_destroyed_x11_root_refs(
        &self,
        window_id: X11Window,
        mapped_window_id: Option<X11Window>,
    ) {
        let Some(display) = self.xdisplay else {
            return;
        };

        let ids = if let Some(mapped_window_id) = mapped_window_id {
            vec![window_id, mapped_window_id]
        } else {
            vec![window_id]
        };

        if let Err(err) = cleanup_x11_root_refs(display, &ids) {
            if debug_x11_enabled() {
                eprintln!(
                    "WLC X11 root cleanup failed display=:{display} ids={ids:x?} err={err}"
                );
            }
        }
    }

    fn start_xwayland(&mut self, handle: LoopHandle<'static, WLCState>) {
        let display_handle = self.display_handle.clone();
        let (xwayland, client) = match XWayland::spawn(
            &display_handle,
            None,
            std::iter::empty::<(String, String)>(),
            true,
            Stdio::null(),
            Stdio::null(),
            |_| (),
        ) {
            Ok(xwayland) => xwayland,
            Err(err) => {
                eprintln!("Failed to start Xwayland: {err}");
                return;
            }
        };

        let ret = handle.clone().insert_source(xwayland, move |event, _, data| {
            match event {
                XWaylandEvent::Ready {
                    x11_socket,
                    display_number,
                } => {
                    let wm = match X11Wm::start_wm(
                        handle.clone(),
                        &display_handle,
                        x11_socket,
                        client.clone(),
                    ) {
                        Ok(wm) => wm,
                        Err(err) => {
                            eprintln!("Failed to attach Xwayland window manager: {err}");
                            return;
                        }
                    };
                    data.xwm = Some(wm);
                    data.xdisplay = Some(display_number);
                    if debug_input_enabled() {
                        data.x11_input_probe =
                            Some(X11InputProbe::start(display_number));
                    }
                }
                XWaylandEvent::Error => {
                    eprintln!("Xwayland crashed on startup");
                }
            }
        });

        if let Err(err) = ret {
            eprintln!("Failed to insert Xwayland event source: {err}");
        }
    }
}

fn init_dmabuf(
    disp: &DisplayHandle,
    state: &mut DmabufState,
    egl: &EGLHelper,
) -> DmabufGlobal {
    let render_node = egl
        .get_render_node()
        .expect("Failed to get render node!")
        .dev_id();

    let formats = egl.query_dmabuf_formats();

    let feedback = DmabufFeedbackBuilder::new(render_node, formats)
        .build()
        .unwrap();

    state.create_global_with_default_feedback::<WLCState>(disp, &feedback)
}

impl CompositorHandler for WLCState {
    fn compositor_state(&mut self) -> &mut CompositorState {
        &mut self.compositor_state
    }

    fn client_compositor_state<'a>(
        &self,
        client: &'a wayland_server::Client,
    ) -> &'a CompositorClientState {
        if let Some(client_data) = client.get_data::<WLCClient>() {
            return &client_data.compositor_state;
        }
        if let Some(client_data) = client.get_data::<XWaylandClientData>() {
            return &client_data.compositor_state;
        }
        panic!("Unknown WaylandCraft client data")
    }

    fn commit(&mut self, _surface: &WlSurface) {}
}

impl BufferHandler for WLCState {
    fn buffer_destroyed(&mut self, _buffer: &WlBuffer) {}
}

impl ShmHandler for WLCState {
    fn shm_state(&self) -> &ShmState {
        &self.shm_state
    }
}

impl DmabufHandler for WLCState {
    fn dmabuf_state(&mut self) -> &mut DmabufState {
        &mut self.dmabuf_state
    }

    fn dmabuf_imported(
        &mut self,
        _global: &DmabufGlobal,
        _dmabuf: Dmabuf,
        notifier: ImportNotifier,
    ) {
        let _ = notifier.successful::<WLCState>();
    }
}

impl XdgShellHandler for WLCState {
    fn xdg_shell_state(&mut self) -> &mut XdgShellState {
        &mut self.xdg_state
    }

    fn new_toplevel(&mut self, surface: ToplevelSurface) {
        surface.send_configure();
    }

    fn new_popup(
        &mut self,
        surface: PopupSurface,
        positioner: PositionerState,
    ) {
        surface.with_pending_state(|state| {
            state.geometry = positioner.get_geometry();
            state.positioner = positioner;
        });
        surface.send_configure().expect("popup initial configure");
    }

    fn grab(&mut self, _surface: PopupSurface, _seat: WlSeat, _serial: Serial) {
    }

    fn reposition_request(
        &mut self,
        surface: PopupSurface,
        positioner: PositionerState,
        token: u32,
    ) {
        surface.with_pending_state(|state| {
            state.geometry = positioner.get_geometry();
            state.positioner = positioner;
        });
        surface.send_repositioned(token);
    }

    fn minimize_request(&mut self, surface: ToplevelSurface) {
        self.requests.minimize.push(surface);
    }

    fn maximize_request(&mut self, surface: ToplevelSurface) {
        self.requests.maximize.push(surface);
    }

    fn unmaximize_request(&mut self, surface: ToplevelSurface) {
        self.requests.unmaximize.push(surface);
    }

    fn fullscreen_request(
        &mut self,
        surface: ToplevelSurface,
        _output: Option<WlOutput>,
    ) {
        self.requests.fullscreen.push(surface);
    }

    fn unfullscreen_request(&mut self, surface: ToplevelSurface) {
        self.requests.unfullscreen.push(surface);
    }

    fn move_request(
        &mut self,
        _surface: ToplevelSurface,
        _seat: WlSeat,
        serial: Serial,
    ) {
        self.requests.move_interactive.push(serial);
    }

    fn resize_request(
        &mut self,
        _surface: ToplevelSurface,
        _seat: WlSeat,
        serial: Serial,
        edges: ResizeEdge,
    ) {
        self.requests.resize_interactive.push((serial, edges));
    }
}

impl XWaylandShellHandler for WLCState {
    fn xwayland_shell_state(&mut self) -> &mut XWaylandShellState {
        &mut self.xwayland_shell_state
    }

    fn surface_associated(
        &mut self,
        _xwm: XwmId,
        wl_surface: WlSurface,
        surface: X11Surface,
    ) {
        if debug_x11_enabled() {
            eprintln!(
                "WLC X11 surface associated wl_surface={:?} {}",
                wl_surface.id(),
                self.describe_x11_window(&surface)
            );
        }
        self.track_x11_window(surface);
    }
}

impl XwmHandler for WLCState {
    fn xwm_state(&mut self, _xwm: XwmId) -> &mut X11Wm {
        self.xwm.as_mut().expect("Xwayland WM is not ready")
    }

    fn new_window(&mut self, _xwm: XwmId, window: X11Surface) {
        if debug_x11_enabled() {
            eprintln!(
                "WLC X11 new window {}",
                self.describe_x11_window(&window)
            );
        }
    }

    fn new_override_redirect_window(
        &mut self,
        _xwm: XwmId,
        window: X11Surface,
    ) {
        if debug_x11_enabled() {
            eprintln!(
                "WLC X11 new override window {}",
                self.describe_x11_window(&window)
            );
        }
    }

    fn map_window_request(&mut self, _xwm: XwmId, window: X11Surface) {
        if debug_x11_enabled() {
            eprintln!(
                "WLC X11 map request {}",
                self.describe_x11_window(&window)
            );
        }
        input_trace::event(
            "native-xwm",
            "map_window_request",
            &format!(
                "\"window\":{}",
                input_trace::json(&self.describe_x11_window(&window))
            ),
        );
        let _ = window.set_mapped(true);
        self.track_x11_window(window.clone());
        let geometry = if window.is_fullscreen() {
            self.fullscreen_x11_geometry_for(&window)
        } else {
            self.clamped_x11_geometry(window.geometry())
        };
        let _ = window.configure(Some(geometry));
    }

    fn mapped_override_redirect_window(
        &mut self,
        _xwm: XwmId,
        window: X11Surface,
    ) {
        if debug_x11_enabled() {
            eprintln!(
                "WLC X11 mapped override window {}",
                self.describe_x11_window(&window)
            );
        }
    }

    fn unmapped_window(&mut self, _xwm: XwmId, window: X11Surface) {
        if debug_x11_enabled() {
            eprintln!("WLC X11 unmapped {}", self.describe_x11_window(&window));
        }
        input_trace::event(
            "native-xwm",
            "unmapped_window",
            &format!(
                "\"window\":{}",
                input_trace::json(&self.describe_x11_window(&window))
            ),
        );
        self.untrack_x11_window(&window);
        if !window.is_override_redirect() {
            let _ = window.set_mapped(false);
        }
    }

    fn destroyed_window(&mut self, _xwm: XwmId, window: X11Surface) {
        if debug_x11_enabled() {
            eprintln!(
                "WLC X11 destroyed {}",
                self.describe_x11_window(&window)
            );
        }
        input_trace::event(
            "native-xwm",
            "destroyed_window",
            &format!(
                "\"window\":{}",
                input_trace::json(&self.describe_x11_window(&window))
            ),
        );
        self.cleanup_destroyed_x11_root_refs(
            window.window_id(),
            window.mapped_window_id(),
        );
        self.untrack_x11_window(&window);
    }

    fn configure_request(
        &mut self,
        _xwm: XwmId,
        window: X11Surface,
        x: Option<i32>,
        y: Option<i32>,
        w: Option<u32>,
        h: Option<u32>,
        _reorder: Option<Reorder>,
    ) {
        let mut geometry = window.geometry();
        if let Some(x) = x {
            geometry.loc.x = x;
        }
        if let Some(y) = y {
            geometry.loc.y = y;
        }
        if let Some(w) = w {
            geometry.size.w = w.max(1) as i32;
        }
        if let Some(h) = h {
            geometry.size.h = h.max(1) as i32;
        }
        let applied = if window.is_fullscreen() {
            self.fullscreen_x11_geometry_for(&window)
        } else {
            self.clamped_x11_geometry(geometry)
        };

        if debug_x11_enabled() {
            eprintln!(
                "WLC X11 configure request title={:?} class={:?} fullscreen={} requested=({:?},{:?},{:?},{:?}) applied={:?}",
                window.title(),
                window.class(),
                window.is_fullscreen(),
                x,
                y,
                w,
                h,
                applied
            );
        }
        input_trace::event(
            "native-xwm",
            "configure_request",
            &format!(
                "\"window\":{},\"requested_x\":{},\"requested_y\":{},\"requested_w\":{},\"requested_h\":{},\"applied\":{}",
                input_trace::json(&self.describe_x11_window(&window)),
                input_trace::json(&format!("{x:?}")),
                input_trace::json(&format!("{y:?}")),
                input_trace::json(&format!("{w:?}")),
                input_trace::json(&format!("{h:?}")),
                input_trace::json(&format!("{applied:?}"))
            ),
        );
        let _ = window.configure(Some(applied));
    }

    fn configure_notify(
        &mut self,
        _xwm: XwmId,
        window: X11Surface,
        _geometry: Rectangle<i32, Logical>,
        _above: Option<u32>,
    ) {
        if debug_x11_enabled() {
            eprintln!(
                "WLC X11 configure notify title={:?} class={:?} mapped={} geometry={:?}",
                window.title(),
                window.class(),
                window.is_mapped(),
                window.geometry()
            );
        }
        if window.is_mapped() && Self::should_track_x11_window(&window) {
            self.track_x11_window(window);
        }
    }

    fn maximize_request(&mut self, _xwm: XwmId, window: X11Surface) {
        let _ = window.set_maximized(true);
        let _ = window.configure(Some(self.output_x11_geometry()));
    }

    fn unmaximize_request(&mut self, _xwm: XwmId, window: X11Surface) {
        let _ = window.set_maximized(false);
    }

    fn fullscreen_request(&mut self, _xwm: XwmId, window: X11Surface) {
        let geometry = self.fullscreen_x11_geometry_for(&window);
        if debug_x11_enabled() {
            eprintln!(
                "WLC X11 fullscreen request title={:?} class={:?} output={:?} mode={:?}",
                window.title(),
                window.class(),
                self.fullscreen_x11_geometry(),
                geometry
            );
        }
        input_trace::event(
            "native-xwm",
            "fullscreen_request",
            &format!(
                "\"window\":{},\"geometry\":{}",
                input_trace::json(&self.describe_x11_window(&window)),
                input_trace::json(&format!("{geometry:?}"))
            ),
        );
        let _ = window.set_fullscreen(true);
        let _ = window.configure(Some(geometry));
    }

    fn unfullscreen_request(&mut self, _xwm: XwmId, window: X11Surface) {
        let _ = window.set_fullscreen(false);
    }

    fn minimize_request(&mut self, _xwm: XwmId, window: X11Surface) {
        if debug_x11_enabled() {
            eprintln!(
                "WLC X11 minimize request title={:?} class={:?}",
                window.title(),
                window.class()
            );
        }
        let _ = window.set_hidden(true);
        self.requests.x11_minimize.push(window);
    }

    fn unminimize_request(&mut self, _xwm: XwmId, window: X11Surface) {
        let _ = window.set_hidden(false);
    }

    fn resize_request(
        &mut self,
        _xwm: XwmId,
        _window: X11Surface,
        _button: u32,
        _resize_edge: X11ResizeEdge,
    ) {
    }

    fn move_request(&mut self, _xwm: XwmId, _window: X11Surface, _button: u32) {
    }
}

impl SeatHandler for WLCState {
    type KeyboardFocus = X11Surface;
    type PointerFocus = X11Surface;
    type TouchFocus = X11Surface;

    fn seat_state(&mut self) -> &mut SeatState<Self> {
        &mut self.xwayland_seat_state
    }

    fn cursor_image(&mut self, _seat: &Seat<Self>, _image: CursorImageStatus) {}
}

impl DndGrabHandler for WLCState {}

pub(crate) struct WLCClient {
    compositor_state: CompositorClientState,
}

impl WLCClient {
    fn new() -> Self {
        Self {
            compositor_state: CompositorClientState::default(),
        }
    }
}

impl ClientData for WLCClient {
    fn initialized(&self, _id: ClientId) {}

    fn disconnected(&self, _id: ClientId, _reason: DisconnectReason) {}
}

pub(crate) fn wlc_init(
    egl: EGLHelper,
) -> Result<WaylandCraft<'static>, Box<dyn std::error::Error>> {
    let event_loop: EventLoop<WLCState> = EventLoop::try_new()?;
    let display: Display<WLCState> = Display::new()?;
    let socket = ListeningSocketSource::new_auto()?;

    let mut state = WLCState::new(display.handle(), &egl);
    state.socket = socket.socket_name().to_os_string();

    let ev_handle = event_loop.handle();
    state.start_xwayland(ev_handle.clone());

    ev_handle
        .insert_source(socket, |stream, _, state| {
            let client = WLCClient::new();
            state
                .display_handle
                .insert_client(stream, Arc::new(client))
                .unwrap();
        })
        .unwrap();

    let display_source = GenericEvent::new(
        display,
        calloop::Interest::READ,
        calloop::Mode::Level,
    );
    ev_handle
        .insert_source(display_source, |_, display_io, state| {
            unsafe {
                display_io.get_mut().dispatch_clients(state).unwrap();
            }
            Ok(calloop::PostAction::Continue)
        })
        .unwrap();

    let xdg = XDGSpecHelper::init();

    let instance = WaylandCraft {
        state,
        event_loop,
        bridge: BridgeState::new(),
        egl,
        xdg,
    };
    Ok(instance)
}

impl<'a> WaylandCraft<'a> {
    pub fn update(&mut self) {
        let state = &mut self.state;
        let event_loop = &mut self.event_loop;
        if let Err(err) = event_loop.dispatch(Some(Duration::ZERO), state) {
            log_native_stderr(&format!(
                "WLC native update dispatch failed: {err}"
            ));
        }
        if let Err(err) = state.display_handle.flush_clients() {
            log_native_stderr(&format!(
                "WLC native update flush_clients failed: {err}"
            ));
        }
    }
}

delegate_compositor!(WLCState);
delegate_shm!(WLCState);
delegate_xdg_shell!(WLCState);
delegate_viewporter!(WLCState);
delegate_single_pixel_buffer!(WLCState);
delegate_dmabuf!(WLCState);
delegate_xwayland_shell!(WLCState);

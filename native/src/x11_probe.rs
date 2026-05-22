use std::sync::mpsc::{self, Sender};
use std::thread;
use std::time::Duration;

use x11rb::connection::{Connection, RequestConnection};
use x11rb::errors::ParseError;
use x11rb::protocol::Event;
use x11rb::protocol::record::{self, ConnectionExt as _};
use x11rb::protocol::xproto::{
    self, ChangeWindowAttributesAux, ConnectionExt as _, EventMask, Window,
};
use x11rb::rust_connection::RustConnection;
use x11rb::x11_utils::TryParse;

use crate::input_trace;

#[derive(Clone)]
pub(crate) struct X11InputProbe {
    tx: Sender<X11ProbeCommand>,
}

#[derive(Clone)]
struct X11ProbeCommand {
    window: Window,
    mapped_window: Option<Window>,
    label: String,
}

impl X11InputProbe {
    pub(crate) fn start(display_number: u32) -> Self {
        let (tx, rx) = mpsc::channel::<X11ProbeCommand>();
        let display = format!(":{display_number}");

        {
            let display = display.clone();
            thread::Builder::new()
                .name("waylandcraft-x11-input-probe".into())
                .spawn(move || event_probe_thread(display, rx))
                .unwrap_or_else(|err| {
                    println!("WLC input x11-probe failed to spawn event thread: {err}");
                    thread::spawn(|| {})
                });
        }

        thread::Builder::new()
            .name("waylandcraft-x11-record-probe".into())
            .spawn(move || record_probe_thread(display))
            .unwrap_or_else(|err| {
                println!(
                    "WLC input x11-probe failed to spawn RECORD thread: {err}"
                );
                thread::spawn(|| {})
            });

        Self { tx }
    }

    pub(crate) fn watch_window(
        &self,
        window: Window,
        mapped_window: Option<Window>,
        label: String,
    ) {
        let _ = self.tx.send(X11ProbeCommand {
            window,
            mapped_window,
            label,
        });
    }
}

fn event_probe_thread(display: String, rx: mpsc::Receiver<X11ProbeCommand>) {
    let (conn, _) = match RustConnection::connect(Some(&display)) {
        Ok(conn) => conn,
        Err(err) => {
            println!(
                "WLC input x11-probe event connect display={display} err={err}"
            );
            return;
        }
    };

    println!("WLC input x11-probe event connected display={display}");

    loop {
        while let Ok(command) = rx.try_recv() {
            watch_one(&conn, command.window, &command.label, "window");
            if let Some(mapped_window) = command.mapped_window
                && mapped_window != command.window
            {
                watch_one(&conn, mapped_window, &command.label, "mapped");
            }
            let _ = conn.flush();
        }

        match conn.poll_for_event() {
            Ok(Some(event)) => log_x11_event(event),
            Ok(None) => thread::sleep(Duration::from_millis(10)),
            Err(err) => {
                println!("WLC input x11-probe event poll err={err}");
                thread::sleep(Duration::from_millis(100));
            }
        }
    }
}

fn watch_one(conn: &RustConnection, window: Window, label: &str, kind: &str) {
    let mask = EventMask::ENTER_WINDOW
        | EventMask::LEAVE_WINDOW
        | EventMask::POINTER_MOTION
        | EventMask::BUTTON_PRESS
        | EventMask::BUTTON_RELEASE;
    let aux = ChangeWindowAttributesAux::new().event_mask(mask);
    match conn.change_window_attributes(window, &aux) {
        Ok(cookie) => match cookie.check() {
            Ok(()) => {
                println!(
                    "WLC input x11-probe watching kind={kind} window=0x{window:x} label={label}"
                );
            }
            Err(err) => {
                println!(
                    "WLC input x11-probe watch-failed kind={kind} window=0x{window:x} label={label} err={err}"
                );
            }
        },
        Err(err) => {
            println!(
                "WLC input x11-probe watch-submit-failed kind={kind} window=0x{window:x} label={label} err={err}"
            );
        }
    }
}

fn log_x11_event(event: Event) {
    match event {
        Event::EnterNotify(event) => {
            let fields = format!(
                "\"event\":\"0x{:x}\",\"child\":\"0x{:x}\",\"root_x\":{},\"root_y\":{},\"event_x\":{},\"event_y\":{},\"mode\":{},\"detail\":{}",
                event.event,
                event.child,
                event.root_x,
                event.root_y,
                event.event_x,
                event.event_y,
                input_trace::json(&format!("{:?}", event.mode)),
                input_trace::json(&format!("{:?}", event.detail))
            );
            input_trace::event("x11-event", "enter", &fields);
            println!(
                "WLC input x11-event enter event=0x{:x} child=0x{:x} root=({}, {}) event=({}, {}) mode={:?} detail={:?}",
                event.event,
                event.child,
                event.root_x,
                event.root_y,
                event.event_x,
                event.event_y,
                event.mode,
                event.detail
            );
        }
        Event::LeaveNotify(event) => {
            let fields = format!(
                "\"event\":\"0x{:x}\",\"child\":\"0x{:x}\",\"root_x\":{},\"root_y\":{},\"event_x\":{},\"event_y\":{},\"mode\":{},\"detail\":{}",
                event.event,
                event.child,
                event.root_x,
                event.root_y,
                event.event_x,
                event.event_y,
                input_trace::json(&format!("{:?}", event.mode)),
                input_trace::json(&format!("{:?}", event.detail))
            );
            input_trace::event("x11-event", "leave", &fields);
            println!(
                "WLC input x11-event leave event=0x{:x} child=0x{:x} root=({}, {}) event=({}, {}) mode={:?} detail={:?}",
                event.event,
                event.child,
                event.root_x,
                event.root_y,
                event.event_x,
                event.event_y,
                event.mode,
                event.detail
            );
        }
        Event::MotionNotify(event) => {
            let fields = format!(
                "\"event\":\"0x{:x}\",\"child\":\"0x{:x}\",\"root_x\":{},\"root_y\":{},\"event_x\":{},\"event_y\":{},\"state\":{}",
                event.event,
                event.child,
                event.root_x,
                event.root_y,
                event.event_x,
                event.event_y,
                input_trace::json(&format!("{:?}", event.state))
            );
            input_trace::event("x11-event", "motion", &fields);
            println!(
                "WLC input x11-event motion event=0x{:x} child=0x{:x} root=({}, {}) event=({}, {}) state={:?}",
                event.event,
                event.child,
                event.root_x,
                event.root_y,
                event.event_x,
                event.event_y,
                event.state
            );
        }
        Event::ButtonPress(event) => {
            let fields = format!(
                "\"event\":\"0x{:x}\",\"child\":\"0x{:x}\",\"detail\":{},\"root_x\":{},\"root_y\":{},\"event_x\":{},\"event_y\":{},\"state\":{}",
                event.event,
                event.child,
                event.detail,
                event.root_x,
                event.root_y,
                event.event_x,
                event.event_y,
                input_trace::json(&format!("{:?}", event.state))
            );
            input_trace::event("x11-event", "button_press", &fields);
            println!(
                "WLC input x11-event button-press event=0x{:x} child=0x{:x} detail={} root=({}, {}) event=({}, {}) state={:?}",
                event.event,
                event.child,
                event.detail,
                event.root_x,
                event.root_y,
                event.event_x,
                event.event_y,
                event.state
            );
        }
        Event::ButtonRelease(event) => {
            let fields = format!(
                "\"event\":\"0x{:x}\",\"child\":\"0x{:x}\",\"detail\":{},\"root_x\":{},\"root_y\":{},\"event_x\":{},\"event_y\":{},\"state\":{}",
                event.event,
                event.child,
                event.detail,
                event.root_x,
                event.root_y,
                event.event_x,
                event.event_y,
                input_trace::json(&format!("{:?}", event.state))
            );
            input_trace::event("x11-event", "button_release", &fields);
            println!(
                "WLC input x11-event button-release event=0x{:x} child=0x{:x} detail={} root=({}, {}) event=({}, {}) state={:?}",
                event.event,
                event.child,
                event.detail,
                event.root_x,
                event.root_y,
                event.event_x,
                event.event_y,
                event.state
            );
        }
        _ => {}
    }
}

fn record_probe_thread(display: String) {
    let (ctrl_conn, _) = match x11rb::connect(Some(&display)) {
        Ok(conn) => conn,
        Err(err) => {
            println!(
                "WLC input x11-record control connect display={display} err={err}"
            );
            return;
        }
    };
    let (data_conn, _) = match x11rb::connect(Some(&display)) {
        Ok(conn) => conn,
        Err(err) => {
            println!(
                "WLC input x11-record data connect display={display} err={err}"
            );
            return;
        }
    };

    if ctrl_conn
        .extension_information(record::X11_EXTENSION_NAME)
        .ok()
        .flatten()
        .is_none()
    {
        println!("WLC input x11-record unavailable display={display}");
        return;
    }

    match ctrl_conn.record_query_version(1, 13) {
        Ok(cookie) => match cookie.reply() {
            Ok(version) => println!(
                "WLC input x11-record connected display={display} version={}.{}",
                version.major_version, version.minor_version
            ),
            Err(err) => {
                println!(
                    "WLC input x11-record version failed display={display} err={err}"
                );
                return;
            }
        },
        Err(err) => {
            println!(
                "WLC input x11-record version failed display={display} err={err}"
            );
            return;
        }
    }

    let rc = match ctrl_conn.generate_id() {
        Ok(id) => id,
        Err(err) => {
            println!(
                "WLC input x11-record context-id failed display={display} err={err}"
            );
            return;
        }
    };

    let empty = record::Range8 { first: 0, last: 0 };
    let empty_ext = record::ExtRange {
        major: empty,
        minor: record::Range16 { first: 0, last: 0 },
    };
    let range = record::Range {
        core_requests: record::Range8 {
            first: 26,
            last: 42,
        },
        core_replies: empty,
        ext_requests: empty_ext,
        ext_replies: empty_ext,
        delivered_events: empty,
        device_events: record::Range8 {
            first: xproto::KEY_PRESS_EVENT,
            last: xproto::MOTION_NOTIFY_EVENT,
        },
        errors: empty,
        client_started: false,
        client_died: false,
    };

    match ctrl_conn.record_create_context(
        rc,
        0,
        &[record::CS::ALL_CLIENTS.into()],
        &[range],
    ) {
        Ok(cookie) => {
            if let Err(err) = cookie.check() {
                println!(
                    "WLC input x11-record create-context failed display={display} err={err}"
                );
                return;
            }
        }
        Err(err) => {
            println!(
                "WLC input x11-record create-context failed display={display} err={err}"
            );
            return;
        }
    }

    const RECORD_FROM_SERVER: u8 = 0;
    const RECORD_FROM_CLIENT: u8 = 1;
    const START_OF_DATA: u8 = 4;

    let replies = match data_conn.record_enable_context(rc) {
        Ok(replies) => replies,
        Err(err) => {
            println!(
                "WLC input x11-record enable failed display={display} err={err}"
            );
            return;
        }
    };

    for reply in replies {
        let Ok(reply) = reply else {
            continue;
        };
        match reply.category {
            START_OF_DATA => {
                println!("WLC input x11-record active display={display}")
            }
            RECORD_FROM_CLIENT => log_x11_record_requests(&reply.data),
            RECORD_FROM_SERVER => log_x11_record_events(&reply.data),
            _ => {}
        }
    }
}

fn log_x11_record_requests(mut data: &[u8]) {
    while !data.is_empty() {
        let opcode = data[0];
        let len = if data.len() >= 4 {
            u16::from_ne_bytes([data[2], data[3]]) as usize * 4
        } else {
            32
        }
        .max(4)
        .min(data.len());

        match opcode {
            26 => println!(
                "WLC input x11-record request GrabPointer raw={:?}",
                &data[..len]
            ),
            27 => println!(
                "WLC input x11-record request UngrabPointer raw={:?}",
                &data[..len]
            ),
            28 => println!(
                "WLC input x11-record request GrabButton raw={:?}",
                &data[..len]
            ),
            29 => println!(
                "WLC input x11-record request UngrabButton raw={:?}",
                &data[..len]
            ),
            35 => println!(
                "WLC input x11-record request AllowEvents raw={:?}",
                &data[..len]
            ),
            31 => println!(
                "WLC input x11-record request GrabKeyboard raw={:?}",
                &data[..len]
            ),
            32 => println!(
                "WLC input x11-record request UngrabKeyboard raw={:?}",
                &data[..len]
            ),
            42 => println!(
                "WLC input x11-record request SetInputFocus raw={:?}",
                &data[..len]
            ),
            _ => {}
        }
        match opcode {
            26 | 27 | 28 | 29 | 31 | 32 | 35 | 42 => input_trace::event(
                "x11-record",
                "request",
                &format!(
                    "\"opcode\":{},\"name\":{},\"raw\":{}",
                    opcode,
                    input_trace::json(request_name(opcode)),
                    input_trace::json(&format!("{:?}", &data[..len]))
                ),
            ),
            _ => {}
        }

        data = &data[len..];
    }
}

fn log_x11_record_events(mut data: &[u8]) {
    while !data.is_empty() {
        match data[0] {
            xproto::KEY_PRESS_EVENT => {
                match xproto::KeyPressEvent::try_parse(data) {
                    Ok((event, remaining)) => {
                        input_trace::event(
                            "x11-record",
                            "delivered_key_press",
                            &format!(
                                "\"event\":\"0x{:x}\",\"child\":\"0x{:x}\",\"detail\":{},\"state\":{}",
                                event.event,
                                event.child,
                                event.detail,
                                input_trace::json(&format!(
                                    "{:?}",
                                    event.state
                                ))
                            ),
                        );
                        println!(
                            "WLC input x11-record delivered key-press event=0x{:x} child=0x{:x} detail={}",
                            event.event, event.child, event.detail
                        );
                        data = remaining;
                    }
                    Err(err) => {
                        log_record_parse_error(err, "key-press");
                        break;
                    }
                }
            }
            xproto::KEY_RELEASE_EVENT => {
                match xproto::KeyReleaseEvent::try_parse(data) {
                    Ok((event, remaining)) => {
                        input_trace::event(
                            "x11-record",
                            "delivered_key_release",
                            &format!(
                                "\"event\":\"0x{:x}\",\"child\":\"0x{:x}\",\"detail\":{},\"state\":{}",
                                event.event,
                                event.child,
                                event.detail,
                                input_trace::json(&format!(
                                    "{:?}",
                                    event.state
                                ))
                            ),
                        );
                        println!(
                            "WLC input x11-record delivered key-release event=0x{:x} child=0x{:x} detail={}",
                            event.event, event.child, event.detail
                        );
                        data = remaining;
                    }
                    Err(err) => {
                        log_record_parse_error(err, "key-release");
                        break;
                    }
                }
            }
            xproto::BUTTON_PRESS_EVENT => {
                match xproto::ButtonPressEvent::try_parse(data) {
                    Ok((event, remaining)) => {
                        input_trace::event(
                            "x11-record",
                            "delivered_button_press",
                            &format!(
                                "\"event\":\"0x{:x}\",\"child\":\"0x{:x}\",\"detail\":{}",
                                event.event, event.child, event.detail
                            ),
                        );
                        println!(
                            "WLC input x11-record delivered button-press event=0x{:x} child=0x{:x} detail={}",
                            event.event, event.child, event.detail
                        );
                        data = remaining;
                    }
                    Err(err) => {
                        log_record_parse_error(err, "button-press");
                        break;
                    }
                }
            }
            xproto::BUTTON_RELEASE_EVENT => {
                match xproto::ButtonReleaseEvent::try_parse(data) {
                    Ok((event, remaining)) => {
                        input_trace::event(
                            "x11-record",
                            "delivered_button_release",
                            &format!(
                                "\"event\":\"0x{:x}\",\"child\":\"0x{:x}\",\"detail\":{}",
                                event.event, event.child, event.detail
                            ),
                        );
                        println!(
                            "WLC input x11-record delivered button-release event=0x{:x} child=0x{:x} detail={}",
                            event.event, event.child, event.detail
                        );
                        data = remaining;
                    }
                    Err(err) => {
                        log_record_parse_error(err, "button-release");
                        break;
                    }
                }
            }
            xproto::MOTION_NOTIFY_EVENT => {
                match xproto::MotionNotifyEvent::try_parse(data) {
                    Ok((event, remaining)) => {
                        input_trace::event(
                            "x11-record",
                            "delivered_motion",
                            &format!(
                                "\"event\":\"0x{:x}\",\"child\":\"0x{:x}\"",
                                event.event, event.child
                            ),
                        );
                        println!(
                            "WLC input x11-record delivered motion event=0x{:x} child=0x{:x}",
                            event.event, event.child
                        );
                        data = remaining;
                    }
                    Err(err) => {
                        log_record_parse_error(err, "motion");
                        break;
                    }
                }
            }
            0 if data.len() >= 8 => {
                let len =
                    u32::from_ne_bytes([data[4], data[5], data[6], data[7]])
                        as usize
                        * 4
                        + 32;
                data = &data[len.min(data.len())..];
            }
            _ => {
                if data.len() < 32 {
                    break;
                }
                data = &data[32..];
            }
        }
    }
}

fn request_name(opcode: u8) -> &'static str {
    match opcode {
        26 => "GrabPointer",
        27 => "UngrabPointer",
        28 => "GrabButton",
        29 => "UngrabButton",
        31 => "GrabKeyboard",
        32 => "UngrabKeyboard",
        35 => "AllowEvents",
        42 => "SetInputFocus",
        _ => "unknown",
    }
}

fn log_record_parse_error(err: ParseError, kind: &str) {
    println!("WLC input x11-record parse-failed kind={kind} err={err}");
}

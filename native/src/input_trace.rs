use std::{
    fs::File,
    io::Write,
    sync::{
        Mutex, OnceLock,
        atomic::{AtomicBool, AtomicI64, Ordering},
    },
    time::{SystemTime, UNIX_EPOCH},
};

static ENABLED: AtomicBool = AtomicBool::new(false);
static CURRENT_TRACE: AtomicI64 = AtomicI64::new(0);
static WRITER: OnceLock<Mutex<Option<File>>> = OnceLock::new();

pub(crate) fn init(path: &str) {
    match File::create(path) {
        Ok(file) => {
            *writer().lock().unwrap() = Some(file);
            ENABLED.store(true, Ordering::Relaxed);
            eprintln!("WLC input trace native={path}");
            event("native", "trace.init", &format!("\"path\":{}", json(path)));
        }
        Err(err) => {
            eprintln!("WLC input trace native failed path={path} err={err}");
        }
    }
}

pub(crate) fn enabled() -> bool {
    ENABLED.load(Ordering::Relaxed)
        || std::env::var("WAYLANDCRAFT_INPUT_TRACE")
            .map(|value| {
                value == "1"
                    || value.eq_ignore_ascii_case("true")
                    || value.eq_ignore_ascii_case("yes")
            })
            .unwrap_or(false)
}

pub(crate) fn set_current(trace: i64) {
    CURRENT_TRACE.store(trace, Ordering::Relaxed);
}

pub(crate) fn clear_current() {
    CURRENT_TRACE.store(0, Ordering::Relaxed);
}

pub(crate) fn current() -> i64 {
    CURRENT_TRACE.load(Ordering::Relaxed)
}

pub(crate) fn event(layer: &str, event_type: &str, fields: &str) {
    if !enabled() {
        return;
    }

    let Some(lock) = WRITER.get() else {
        return;
    };
    let Ok(mut guard) = lock.lock() else {
        return;
    };
    let Some(writer) = guard.as_mut() else {
        return;
    };

    let now = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default();
    let thread = std::thread::current();
    let thread_name = thread.name().unwrap_or("unnamed");
    let thread_id = format!("{:?}", thread.id());
    let _ = write!(
        writer,
        "{{\"wall_nanos\":{},\"trace\":{},\"thread\":{},\"thread_id\":{},\"layer\":{},\"event_type\":{}",
        now.as_nanos(),
        current(),
        json(thread_name),
        json(&thread_id),
        json(layer),
        json(event_type)
    );
    if !fields.is_empty() {
        let _ = write!(writer, ",{fields}");
    }
    let _ = writeln!(writer, "}}");
    let _ = writer.flush();
}

pub(crate) fn json(value: &str) -> String {
    let mut out = String::with_capacity(value.len() + 2);
    out.push('"');
    for c in value.chars() {
        match c {
            '\\' => out.push_str("\\\\"),
            '"' => out.push_str("\\\""),
            '\n' => out.push_str("\\n"),
            '\r' => out.push_str("\\r"),
            '\t' => out.push_str("\\t"),
            c if c < ' ' => out.push_str(&format!("\\u{:04x}", c as u32)),
            c => out.push(c),
        }
    }
    out.push('"');
    out
}

fn writer() -> &'static Mutex<Option<File>> {
    WRITER.get_or_init(|| Mutex::new(None))
}

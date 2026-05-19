use std::collections::HashSet;
use std::ffi::{OsStr, OsString};
use std::fs;
use std::os::unix::ffi::OsStrExt;
use std::process::{Command, Stdio};

pub fn spawn(
    cmd: String,
    args: Vec<String>,
    env: Vec<(OsString, OsString)>,
) -> Result<(), ()> {
    let mut command = Command::new(cmd);
    command
        .args(args)
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null());

    // Remove evil environment variables of the devil
    command
        .env_remove("DISPLAY")
        .env_remove("WAYLAND_DISPLAY")
        .env_remove("LD_LIBRARY_PATH");

    command.envs(env);

    // Double-fork to run the executable.
    // Has to do with preventing zombie processes and such
    match unsafe { libc::fork() } {
        0 => {
            // child process
            unsafe {
                libc::setsid();
            }
            let _ = command.spawn();
            unsafe {
                libc::_exit(0);
            }
        }
        -1 => {
            // fork failed
            return Err(());
        }
        _ => { // parent process
        }
    }

    unsafe {
        libc::wait(std::ptr::null_mut());
    }

    Ok(())
}

pub fn cleanup_display_processes(
    socket: &OsStr,
    xdisplay: Option<u32>,
) -> usize {
    let own_pid = unsafe { libc::getpid() };
    let own_pgid = unsafe { libc::getpgid(0) };
    let mut process_groups = HashSet::new();

    let proc_dir = match fs::read_dir("/proc") {
        Ok(proc_dir) => proc_dir,
        Err(_) => return 0,
    };

    for entry in proc_dir.flatten() {
        let file_name = entry.file_name();
        let Some(pid) = file_name.to_string_lossy().parse::<libc::pid_t>().ok()
        else {
            continue;
        };
        if pid == own_pid {
            continue;
        }

        let environ = match fs::read(entry.path().join("environ")) {
            Ok(environ) => environ,
            Err(_) => continue,
        };
        if !environment_matches_display(&environ, socket, xdisplay) {
            continue;
        }

        let pgid = unsafe { libc::getpgid(pid) };
        if pgid <= 0 || pgid == own_pgid {
            continue;
        }
        process_groups.insert(pgid);
    }

    if process_groups.is_empty() {
        return 0;
    }

    let count = process_groups.len();

    for pgid in &process_groups {
        unsafe {
            libc::kill(-*pgid, libc::SIGTERM);
        }
    }

    std::thread::sleep(std::time::Duration::from_millis(750));

    for pgid in process_groups {
        if process_group_still_matches(pgid, socket, xdisplay) {
            unsafe {
                libc::kill(-pgid, libc::SIGKILL);
            }
        }
    }

    count
}

fn environment_matches_display(
    environ: &[u8],
    socket: &OsStr,
    xdisplay: Option<u32>,
) -> bool {
    let wayland_display = [b"WAYLAND_DISPLAY=", socket.as_bytes()].concat();
    let display = xdisplay.map(|display| format!("DISPLAY=:{display}"));

    environ.split(|b| *b == 0).any(|entry| {
        entry == wayland_display
            || display
                .as_ref()
                .is_some_and(|display| entry == display.as_bytes())
    })
}

fn process_group_still_matches(
    pgid: libc::pid_t,
    socket: &OsStr,
    xdisplay: Option<u32>,
) -> bool {
    let proc_dir = match fs::read_dir("/proc") {
        Ok(proc_dir) => proc_dir,
        Err(_) => return false,
    };

    for entry in proc_dir.flatten() {
        let file_name = entry.file_name();
        let Some(pid) = file_name.to_string_lossy().parse::<libc::pid_t>().ok()
        else {
            continue;
        };

        if unsafe { libc::getpgid(pid) } != pgid {
            continue;
        }

        let environ = match fs::read(entry.path().join("environ")) {
            Ok(environ) => environ,
            Err(_) => continue,
        };
        if environment_matches_display(&environ, socket, xdisplay) {
            return true;
        }
    }

    false
}

// Prevents additional console window on Windows in release, DO NOT REMOVE!!
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use std::process::{Child, Command};
use std::sync::{Arc, Mutex};
use tauri::{Manager, WindowEvent};

struct AppState {
    server_process: Arc<Mutex<Option<Child>>>,
}

#[tauri::command]
fn get_server_status(state: tauri::State<AppState>) -> String {
    let proc = state.server_process.lock().unwrap();
    if proc.is_some() {
        "running".to_string()
    } else {
        "stopped".to_string()
    }
}

fn main() {
    let server_process: Arc<Mutex<Option<Child>>> = Arc::new(Mutex::new(None));

    // Automatically spawn local inference host if available
    let proc_clone = Arc::clone(&server_process);
    std::thread::spawn(move || {
        let child = Command::new("node")
            .arg("../../server/index.js")
            .arg("--port")
            .arg("8080")
            .spawn();

        if let Ok(c) = child {
            let mut lock = proc_clone.lock().unwrap();
            *lock = Some(c);
        }
    });

    let exit_proc = Arc::clone(&server_process);

    tauri::Builder::default()
        .manage(AppState {
            server_process: Arc::clone(&server_process),
        })
        .invoke_handler(tauri::generate_handler![get_server_status])
        .on_window_event(move |_window, event| {
            if let WindowEvent::Destroyed = event {
                // Ensure child process is killed on exit
                let mut lock = exit_proc.lock().unwrap();
                if let Some(mut child) = lock.take() {
                    let _ = child.kill();
                }
            }
        })
        .run(tauri::generate_context!())
        .expect("error while running OmniMind desktop application");
}

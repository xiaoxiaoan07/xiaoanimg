use std::collections::HashMap;
use std::path::{Path, PathBuf};
use std::ptr;
use std::sync::atomic::{AtomicU8, AtomicU64, Ordering};
use std::sync::{Arc, LazyLock, Mutex};

use jni::errors::ThrowRuntimeExAndDefault;
use jni::objects::{JClass, JString};
use jni::strings::JNIString;
use jni::sys::{jint, jlong, jstring};
use jni::{Env, EnvUnowned, jni_str};
use serde::Serialize;

use payload_extract::extract::{self, ExtractConfig};
use payload_extract::input::{self, OpenOptions, ProgressCallback};
use payload_extract::payload::PayloadView;

/// Helper: throw a Java exception and return null.
fn throw_and_return_null(env: &mut Env, msg: &str) -> jstring {
    let _ = env.throw_new(jni_str!("java/lang/RuntimeException"), JNIString::from(msg));
    ptr::null_mut()
}

/// Helper: get a Rust string from a JString.
fn get_string(env: &mut Env, s: &JString) -> Result<String, String> {
    s.try_to_string(env)
        .map_err(|e| format!("Failed to get string: {e}"))
}

/// Helper: get PayloadView reference from a handle (raw pointer).
///
/// # Safety
/// The caller must ensure the handle is a valid pointer returned by `open`.
unsafe fn get_payload<'a>(handle: jlong) -> &'a PayloadView {
    unsafe { &*(handle as *const PayloadView) }
}

// ============================================================
// Extraction progress registry
//
// `extractPartition` publishes two-phase progress into a token-keyed cell that
// the Kotlin side polls via `getExtractProgress`. The progress callbacks only
// touch atomics (no JNI calls), so the rayon/async worker threads that invoke
// them never need to attach to the JVM.
// ============================================================

const PHASE_DOWNLOAD: u8 = 0;
const PHASE_EXTRACT: u8 = 1;

struct ProgressCell {
    phase: AtomicU8,
    current: AtomicU64,
    total: AtomicU64,
}

static EXTRACT_PROGRESS: LazyLock<Mutex<HashMap<jlong, Arc<ProgressCell>>>> =
    LazyLock::new(|| Mutex::new(HashMap::new()));

/// Removes the progress entry on drop, cleaning up the token on every exit path.
struct ProgressGuard(jlong);

impl Drop for ProgressGuard {
    fn drop(&mut self) {
        if let Ok(mut map) = EXTRACT_PROGRESS.lock() {
            map.remove(&self.0);
        }
    }
}

/// A `ProgressCallback` that records `(current, total)` for `phase` into `cell`.
fn progress_sink(cell: Arc<ProgressCell>, phase: u8) -> ProgressCallback {
    Arc::new(move |current, total| {
        cell.phase.store(phase, Ordering::Relaxed);
        cell.total.store(total, Ordering::Relaxed);
        cell.current.store(current, Ordering::Relaxed);
    })
}

// ============================================================
// JNI exports
// ============================================================

/// Open a payload file (local .bin, .zip, or URL) and return an opaque handle.
/// This opens for metadata only (header + manifest). For HTTP URLs, blob data
/// is NOT downloaded — use extractPartition which re-opens with open_for_extract.
#[unsafe(no_mangle)]
pub extern "system" fn Java_native_PayloadExtractNative_open(
    mut unowned_env: EnvUnowned,
    _class: JClass,
    input_path: JString,
) -> jlong {
    unowned_env
        .with_env(|env| -> Result<jlong, jni::errors::Error> {
            let input_str = match get_string(env, &input_path) {
                Ok(s) => s,
                Err(e) => {
                    let _ = env.throw_new(
                        jni_str!("java/lang/RuntimeException"),
                        JNIString::from(e.as_str()),
                    );
                    return Ok(0);
                }
            };

            match input::open(&input_str, false, None) {
                Ok(payload) => {
                    let boxed = Box::new(payload);
                    Ok(Box::into_raw(boxed) as jlong)
                }
                Err(e) => {
                    let _ = env.throw_new(
                        jni_str!("java/lang/RuntimeException"),
                        JNIString::from(format!("Failed to open payload: {e}").as_str()),
                    );
                    Ok(0)
                }
            }
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

/// List partitions as a JSON string.
#[unsafe(no_mangle)]
pub extern "system" fn Java_native_PayloadExtractNative_listPartitionsJson(
    mut unowned_env: EnvUnowned,
    _class: JClass,
    handle: jlong,
    with_hash: bool,
) -> jstring {
    unowned_env
        .with_env(|env| -> Result<jstring, jni::errors::Error> {
            let payload = unsafe { get_payload(handle) };

            #[derive(Serialize)]
            struct PartitionEntry {
                name: String,
                size: u64,
                operations: usize,
                #[serde(skip_serializing_if = "Option::is_none")]
                hash: Option<String>,
            }

            let entries: Vec<PartitionEntry> = payload
                .partitions()
                .iter()
                .map(|p| {
                    let info = p.new_partition_info.as_ref();
                    let size = info.and_then(|i| i.size).unwrap_or(0);
                    let hash = if with_hash {
                        info.and_then(|i| i.hash.as_ref()).map(hex::encode)
                    } else {
                        None
                    };
                    PartitionEntry {
                        name: p.partition_name.clone(),
                        size,
                        operations: p.operations.len(),
                        hash,
                    }
                })
                .collect();

            let json = match serde_json::to_string(&entries) {
                Ok(s) => s,
                Err(e) => {
                    return Ok(throw_and_return_null(
                        env,
                        &format!("JSON serialization failed: {e}"),
                    ));
                }
            };

            match JString::from_str(env, &json) {
                Ok(s) => Ok(s.into_raw()),
                Err(e) => Ok(throw_and_return_null(
                    env,
                    &format!("Failed to create Java string: {e}"),
                )),
            }
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

/// Get payload metadata as a JSON string.
#[unsafe(no_mangle)]
pub extern "system" fn Java_native_PayloadExtractNative_getMetadataJson(
    mut unowned_env: EnvUnowned,
    _class: JClass,
    handle: jlong,
) -> jstring {
    unowned_env
        .with_env(|env| -> Result<jstring, jni::errors::Error> {
            let payload = unsafe { get_payload(handle) };
            let header = payload.header();
            let manifest = payload.manifest();

            #[derive(Serialize)]
            struct PayloadMetadata {
                version: u64,
                manifest_size: u64,
                metadata_signature_size: u32,
                block_size: u32,
                partition_count: usize,
                #[serde(skip_serializing_if = "Option::is_none")]
                max_timestamp: Option<i64>,
                #[serde(skip_serializing_if = "Option::is_none")]
                partial_update: Option<bool>,
                #[serde(skip_serializing_if = "Option::is_none")]
                security_patch_level: Option<String>,
            }

            let metadata = PayloadMetadata {
                version: header.version,
                manifest_size: header.manifest_size,
                metadata_signature_size: header.metadata_signature_size,
                block_size: payload.block_size(),
                partition_count: manifest.partitions.len(),
                max_timestamp: manifest.max_timestamp,
                partial_update: manifest.partial_update,
                security_patch_level: manifest.security_patch_level.clone(),
            };

            let json = match serde_json::to_string(&metadata) {
                Ok(s) => s,
                Err(e) => {
                    return Ok(throw_and_return_null(
                        env,
                        &format!("JSON serialization failed: {e}"),
                    ));
                }
            };

            match JString::from_str(env, &json) {
                Ok(s) => Ok(s.into_raw()),
                Err(e) => Ok(throw_and_return_null(
                    env,
                    &format!("Failed to create Java string: {e}"),
                )),
            }
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

/// Extract a single partition, publishing two-phase progress under `token`.
///
/// Re-opens the payload with `open_for_extract_with` to ensure blob data is
/// available (necessary for HTTP URLs, where `open` only downloads metadata).
/// For HTTP this streams the needed blob into a temp file in the output dir and
/// reports download progress; then extraction reports per-operation progress.
/// For local files there is no download phase (fast mmap re-open).
#[unsafe(no_mangle)]
pub extern "system" fn Java_native_PayloadExtractNative_extractPartition(
    mut unowned_env: EnvUnowned,
    _class: JClass,
    input: JString,
    output_dir: JString,
    partition_name: JString,
    threads: jint,
    verify: bool,
    token: jlong,
) {
    unowned_env
        .with_env(|env| -> Result<(), jni::errors::Error> {
            let input_str = match get_string(env, &input) {
                Ok(s) => s,
                Err(e) => {
                    let _ = env.throw_new(
                        jni_str!("java/lang/RuntimeException"),
                        JNIString::from(e.as_str()),
                    );
                    return Ok(());
                }
            };

            let output_str = match get_string(env, &output_dir) {
                Ok(s) => s,
                Err(e) => {
                    let _ = env.throw_new(
                        jni_str!("java/lang/RuntimeException"),
                        JNIString::from(e.as_str()),
                    );
                    return Ok(());
                }
            };

            let part_name = match get_string(env, &partition_name) {
                Ok(s) => s,
                Err(e) => {
                    let _ = env.throw_new(
                        jni_str!("java/lang/RuntimeException"),
                        JNIString::from(e.as_str()),
                    );
                    return Ok(());
                }
            };

            // Register a progress cell; the guard removes it on every return path.
            let cell = Arc::new(ProgressCell {
                phase: AtomicU8::new(PHASE_DOWNLOAD),
                current: AtomicU64::new(0),
                total: AtomicU64::new(0),
            });
            if let Ok(mut map) = EXTRACT_PROGRESS.lock() {
                map.insert(token, cell.clone());
            }
            let _guard = ProgressGuard(token);

            let output_path = Path::new(&output_str);
            // The HTTP temp file is written into the output dir, so ensure it exists.
            let _ = std::fs::create_dir_all(output_path);

            // Re-open for extraction (downloads blob data for HTTP), reporting
            // download bytes into the cell. temp_dir = output dir avoids tmpfs and
            // guarantees a writable location (required on Android).
            let partition_names = vec![part_name.clone()];
            let open_opts = OpenOptions {
                insecure: false,
                user_agent: None,
                download_progress: Some(progress_sink(cell.clone(), PHASE_DOWNLOAD)),
                temp_dir: Some(PathBuf::from(&output_str)),
            };
            let payload =
                match input::open_for_extract_with(&input_str, &partition_names, &open_opts) {
                    Ok(p) => p,
                    Err(e) => {
                        let _ = env.throw_new(
                            jni_str!("java/lang/RuntimeException"),
                            JNIString::from(
                                format!("Failed to open payload for extraction: {e}").as_str(),
                            ),
                        );
                        return Ok(());
                    }
                };

            // Download done; switch the cell to the extraction phase.
            cell.phase.store(PHASE_EXTRACT, Ordering::Relaxed);
            cell.current.store(0, Ordering::Relaxed);

            let config = ExtractConfig {
                verify_ops: verify,
                threads: threads as usize,
                quiet: true,
                source_dir: None,
                out_config: None,
                progress: Some(progress_sink(cell.clone(), PHASE_EXTRACT)),
            };

            if let Err(e) =
                extract::extract_partitions(&payload, output_path, &partition_names, &config)
            {
                let _ = env.throw_new(
                    jni_str!("java/lang/RuntimeException"),
                    JNIString::from(format!("Extraction failed: {e}").as_str()),
                );
            }

            Ok(())
        })
        .resolve::<ThrowRuntimeExAndDefault>();
}

/// Poll two-phase extraction progress for `token`.
///
/// Returns `-1` when there is no active extraction (not started / finished).
/// Otherwise returns `(phase << 16) | permille`, where `phase` is 0 (downloading)
/// or 1 (extracting) and `permille` is 0..=1000. Does no JNI calls, so (like
/// `close`) it takes the env/class directly without `with_env`.
#[unsafe(no_mangle)]
pub extern "system" fn Java_native_PayloadExtractNative_getExtractProgress(
    _env: EnvUnowned,
    _class: JClass,
    token: jlong,
) -> jlong {
    let cell = {
        let map = match EXTRACT_PROGRESS.lock() {
            Ok(m) => m,
            Err(_) => return -1,
        };
        match map.get(&token) {
            Some(c) => c.clone(),
            None => return -1,
        }
    };
    let phase = cell.phase.load(Ordering::Relaxed) as jlong;
    let total = cell.total.load(Ordering::Relaxed);
    let current = cell.current.load(Ordering::Relaxed);
    let permille = if total > 0 {
        ((current.min(total) * 1000) / total) as jlong
    } else {
        0
    };
    (phase << 16) | permille
}

/// Close and free the PayloadView handle.
#[unsafe(no_mangle)]
pub extern "system" fn Java_native_PayloadExtractNative_close(
    _env: EnvUnowned,
    _class: JClass,
    handle: jlong,
) {
    if handle != 0 {
        unsafe {
            let _ = Box::from_raw(handle as *mut PayloadView);
        }
    }
}

/// Apply the Windows immersive dark/light title bar via DWM.
///
/// Replaces the former JNA `DwmSetWindowAttribute` call. The window's native HWND is
/// resolved on the Java side and passed here as a `jlong`. Does no JNI calls, so (like
/// `close`) it takes the env/class directly without `with_env`.
#[cfg(target_os = "windows")]
#[unsafe(no_mangle)]
pub extern "system" fn Java_native_PayloadExtractNative_setWindowDarkTitleBar(
    _env: EnvUnowned,
    _class: JClass,
    hwnd: jlong,
    is_dark: bool,
) {
    use std::ffi::c_void;
    use windows_sys::Win32::Foundation::HWND;
    use windows_sys::Win32::Graphics::Dwm::DwmSetWindowAttribute;

    // DWMWA_USE_IMMERSIVE_DARK_MODE; pvAttribute points at a 4-byte Win32 BOOL.
    const DWMWA_USE_IMMERSIVE_DARK_MODE: u32 = 20;

    if hwnd == 0 {
        return;
    }
    let value: i32 = if is_dark { 1 } else { 0 };
    unsafe {
        let _ = DwmSetWindowAttribute(
            hwnd as HWND,
            DWMWA_USE_IMMERSIVE_DARK_MODE,
            &value as *const i32 as *const c_void,
            std::mem::size_of::<i32>() as u32,
        );
    }
}

/// No-op stub on non-Windows targets so the JNI symbol still resolves if ever referenced.
#[cfg(not(target_os = "windows"))]
#[unsafe(no_mangle)]
pub extern "system" fn Java_native_PayloadExtractNative_setWindowDarkTitleBar(
    _env: EnvUnowned,
    _class: JClass,
    _hwnd: jlong,
    _is_dark: bool,
) {
}

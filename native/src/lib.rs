use std::path::Path;
use std::ptr;

use jni::errors::ThrowRuntimeExAndDefault;
use jni::objects::{JClass, JString};
use jni::strings::JNIString;
use jni::sys::{jint, jlong, jstring};
use jni::{Env, EnvUnowned, jni_str};
use serde::Serialize;

use payload_extract::extract::{self, ExtractConfig};
use payload_extract::input;
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

            match input::open(&input_str, false) {
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

/// Extract a single partition.
///
/// Re-opens the payload with `open_for_extract` to ensure blob data is available,
/// which is necessary for HTTP URLs (where `open` only downloads metadata).
/// For local files this is a fast mmap re-open.
#[unsafe(no_mangle)]
pub extern "system" fn Java_native_PayloadExtractNative_extractPartition(
    mut unowned_env: EnvUnowned,
    _class: JClass,
    input: JString,
    output_dir: JString,
    partition_name: JString,
    threads: jint,
    verify: bool,
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

            // Re-open with open_for_extract to get blob data for this partition
            let partition_names = vec![part_name.clone()];
            let payload = match input::open_for_extract(&input_str, &partition_names, false) {
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

            let config = ExtractConfig {
                verify_ops: verify,
                threads: threads as usize,
                quiet: true,
                source_dir: None,
                out_config: None,
            };

            let output_path = Path::new(&output_str);

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

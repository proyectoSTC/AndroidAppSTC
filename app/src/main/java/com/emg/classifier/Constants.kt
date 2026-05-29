package com.emg.classifier

object Constants {

    // ── BLE UUIDs ──────────────────────────────────────────────────────────────
    const val SERVICE_UUID        = "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
    const val CHARACTERISTIC_UUID = "beb5483e-36e1-4688-b7f5-ea07361b26a8"
    const val CCCD_UUID           = "00002902-0000-1000-8000-00805f9b34fb"
    const val DEVICE_NAME         = "EMG_ESP32"

    // ── Signal processing ──────────────────────────────────────────────────────
    const val SAMPLE_RATE     = 100          // Hz (DB1)
    const val WINDOW_SAMPLES  = 20           // 200 ms @ 100 Hz
    const val STRIDE_SAMPLES  = 10           // 50% overlap
    const val N_CHANNELS      = 3
    const val N_FEATURES      = 12           // 3 ch × 4 features
    const val SCALE_FACTOR    = 10000f       // int16 → float

    // ── ZC threshold ──────────────────────────────────────────────────────────
    const val ZC_THRESHOLD = 1e-4f

    // ── Class labels ──────────────────────────────────────────────────────────
    val LABEL_CLASSES = intArrayOf(5, 6, 13, 14, 15, 16)
    val LABEL_NAMES   = mapOf(
        5  to "Mano Abierta",
        6  to "Mano Cerrada",
        13 to "Flexión",
        14 to "Extensión",
        15 to "Desviación Ulnar",
        16 to "Desviación Radial"
    )

    // ── Risk class ─────────────────────────────────────────────────────────────
    const val RISK_CLASS_INDEX = 1          // index of class 6 in LABEL_CLASSES
    const val RISK_CONSECUTIVE = 2          // consecutive predictions to trigger alert

    // ── Normalization params (from norm_mu.npy / norm_sigma.npy) ──────────────
    // Replace these values with your actual trained model params from ver_norm.py
    val NORM_MU = floatArrayOf(
        0.22603667f,
        0.22171485f,
        0.00041654f,
        0.20424049f,
        0.46515116f,
        0.45532036f,
        0.00041654f,
        0.42357719f,
        0.61573923f,
        0.60705954f,
        0.00000000f,
        0.47610217f,
    )

    val NORM_SIGMA = floatArrayOf(
        0.41369107f,
        0.41014269f,
        0.02885775f,
        0.30369264f,
        0.70977831f,
        0.70420402f,
        0.02885775f,
        0.52797669f,
        0.75206643f,
        0.74799675f,
        0.00000001f,
        0.49030954f,
    )
}

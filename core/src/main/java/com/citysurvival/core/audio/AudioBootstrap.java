package com.citysurvival.core.audio;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;

/**
 * Generates simple fallback WAV assets into local storage when packaged audio assets are missing.
 *
 * This avoids "silent game" when users haven't added audio files under assets/audio/ yet.
 */
public final class AudioBootstrap {
    private static final String TAG = "AudioBootstrap";

    private static final int SAMPLE_RATE = 44100;

    private AudioBootstrap() {
    }

    public static FileHandle ensureFallbackBgm() {
        // Keep in local storage so it works even when running from a jar.
        return ensureWav(Gdx.files.local("audio_generated/bgm.wav"), WavPreset.BGM);
    }

    public static FileHandle ensureFallbackHit() {
        return ensureWav(Gdx.files.local("audio_generated/hit.wav"), WavPreset.HIT);
    }

    public static FileHandle ensureFallbackAttack() {
        return ensureWav(Gdx.files.local("audio_generated/attack.wav"), WavPreset.ATTACK);
    }

    public static FileHandle ensureFallbackSuccess() {
        return ensureWav(Gdx.files.local("audio_generated/success.wav"), WavPreset.SUCCESS);
    }

    private static FileHandle ensureWav(FileHandle handle, WavPreset preset) {
        try {
            if (handle.exists() && handle.length() > 64) return handle;
            handle.parent().mkdirs();

            byte[] wavBytes = buildWavPcm16MonoBytes(preset);
            // Overwrite to avoid partial/corrupt files.
            handle.writeBytes(wavBytes, false);
            Gdx.app.log(TAG, "Generated fallback audio: " + handle.path());
            return handle;
        } catch (RuntimeException e) {
            // Avoid crashing the game if filesystem/audio isn't available.
            try {
                Gdx.app.error(TAG, "Failed to generate fallback audio: " + handle.path(), e);
            } catch (RuntimeException ignored) {
            }
            return null;
        }
    }

    private enum WavPreset {
        BGM,
        HIT,
        ATTACK,
        SUCCESS
    }

    private static byte[] buildWavPcm16MonoBytes(WavPreset preset) {
        int lengthMs;
        float baseVolume;
        float[] freqs;

        switch (preset) {
            case BGM -> {
                // ~4 seconds of a simple arpeggio-ish loop.
                lengthMs = 4000;
                baseVolume = 0.18f;
                freqs = new float[]{220f, 277.18f, 329.63f, 440f};
            }
            case HIT -> {
                lengthMs = 120;
                baseVolume = 0.55f;
                freqs = new float[]{180f, 120f};
            }
            case ATTACK -> {
                lengthMs = 90;
                baseVolume = 0.65f;
                freqs = new float[]{880f, 660f};
            }
            case SUCCESS -> {
                lengthMs = 650;
                baseVolume = 0.35f;
                freqs = new float[]{523.25f, 659.25f, 783.99f}; // C5, E5, G5
            }
            default -> throw new IllegalStateException("Unexpected preset: " + preset);
        }

        int sampleCount = (int) ((long) SAMPLE_RATE * lengthMs / 1000L);
        if (sampleCount <= 1) sampleCount = 1;

        ByteArrayOutputStream baos = new ByteArrayOutputStream(44 + sampleCount * 2);
        try {
            // Write WAV header, fill later with sizes.
            writeAscii(baos, "RIFF");
            writeLEInt(baos, 36 + sampleCount * 2);
            writeAscii(baos, "WAVE");

            // fmt chunk
            writeAscii(baos, "fmt ");
            writeLEInt(baos, 16); // PCM
            writeLEShort(baos, (short) 1); // PCM
            writeLEShort(baos, (short) 1); // mono
            writeLEInt(baos, SAMPLE_RATE);
            writeLEInt(baos, SAMPLE_RATE * 2); // byte rate
            writeLEShort(baos, (short) 2); // block align
            writeLEShort(baos, (short) 16); // bits

            // data chunk
            writeAscii(baos, "data");
            writeLEInt(baos, sampleCount * 2);

            // Samples
            for (int i = 0; i < sampleCount; i++) {
                float t = i / (float) SAMPLE_RATE;

                float env;
                switch (preset) {
                    case BGM -> env = 1.0f;
                    default -> {
                        // Simple fast decay for SFX.
                        float k = i / (float) sampleCount;
                        env = (1.0f - k);
                        env = env * env;
                    }
                }

                float sample;
                switch (preset) {
                    case BGM -> {
                        // Switch note every 0.5s.
                        int note = (int) (t / 0.5f) % freqs.length;
                        float f = freqs[note];
                        sample = (float) Math.sin(2.0 * Math.PI * f * t);

                        // Add a subtle second harmonic for texture.
                        sample = (sample * 0.75f) + (float) Math.sin(2.0 * Math.PI * (f * 2f) * t) * 0.25f;

                        // Gentle fade to reduce clicks on looping.
                        float fade = 1f;
                        float edge = 0.02f; // 20ms
                        if (t < edge) fade = t / edge;
                        float remaining = (lengthMs / 1000f) - t;
                        if (remaining < edge) fade = Math.min(fade, remaining / edge);
                        env *= clamp01(fade);
                    }
                    case SUCCESS -> {
                        // Simple 3-note rising chime.
                        float totalSec = lengthMs / 1000f;
                        float noteLen = totalSec / 3f;
                        int note = Math.min(2, (int) (t / noteLen));
                        float f = freqs[note];
                        float tt = t - (note * noteLen);

                        float localEnv = 1f;
                        float edge = 0.01f;
                        if (tt < edge) localEnv = tt / edge;
                        float remaining = noteLen - tt;
                        if (remaining < edge) localEnv = Math.min(localEnv, remaining / edge);

                        // A bit less decay so the "chime" reads clearly.
                        env = clamp01(localEnv) * (0.85f + 0.15f * env);
                        sample = (float) Math.sin(2.0 * Math.PI * f * t);
                    }
                    default -> {
                        // A short two-tone blip.
                        float f = (i < sampleCount / 2) ? freqs[0] : freqs[1];
                        sample = (float) Math.sin(2.0 * Math.PI * f * t);
                    }
                }

                float v = baseVolume * env;
                int pcm = (int) (clamp(sample, -1f, 1f) * v * 32767);
                writeLEShort(baos, (short) pcm);
            }
        } catch (IOException impossible) {
            // ByteArrayOutputStream shouldn't throw.
        }

        return baos.toByteArray();
    }

    private static float clamp01(float v) {
        return clamp(v, 0f, 1f);
    }

    private static float clamp(float v, float min, float max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    private static void writeAscii(OutputStream os, String s) throws IOException {
        os.write(s.getBytes());
    }

    private static void writeLEInt(OutputStream os, int v) throws IOException {
        os.write(v & 0xFF);
        os.write((v >>> 8) & 0xFF);
        os.write((v >>> 16) & 0xFF);
        os.write((v >>> 24) & 0xFF);
    }

    private static void writeLEShort(OutputStream os, short v) throws IOException {
        os.write(v & 0xFF);
        os.write((v >>> 8) & 0xFF);
    }
}

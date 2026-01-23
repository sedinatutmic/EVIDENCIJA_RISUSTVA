package org.example.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Minimal TTS helper that uses PowerShell/System.Speech on Windows.
 * It runs speech in a single background thread so calls to speak() are non-blocking
 * and will be spoken sequentially. stop() will cancel queued messages and destroy
 * any ongoing speech process.
 */
public class Tts {
    private ExecutorService executor;
    private final AtomicReference<Process> currentProcess = new AtomicReference<>();

    private synchronized void ensureExecutor() {
        if (executor == null || executor.isShutdown()) {
            executor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "TTS-Thread");
                t.setDaemon(true);
                return t;
            });
        }
    }

    public synchronized void start() {
        ensureExecutor();
    }

    public synchronized void stop() {
        try {
            if (executor != null) {
                executor.shutdownNow();
                executor = null;
            }
        } finally {
            Process p = currentProcess.getAndSet(null);
            if (p != null) {
                try { p.destroyForcibly(); } catch (Exception ignored) {}
            }
        }
    }

    public void speak(String text) {
        if (text == null || text.isBlank()) return;
        ensureExecutor();
        if (executor == null || executor.isShutdown()) return;
        // submit a blocking speak task to the single-threaded executor to guarantee sequential speech
        executor.submit(() -> speakBlocking(text));
    }

    /**
     * Interrupt current and queued speech, then speak text immediately (non-blocking).
     */
    public synchronized void speakNow(String text) {
        if (text == null || text.isBlank()) return;
        // stop current executor and its queued tasks
        try {
            if (executor != null) {
                executor.shutdownNow();
            }
        } catch (Exception ignored) {}
        // destroy current process if any
        Process p = currentProcess.getAndSet(null);
        if (p != null) {
            try { p.destroyForcibly(); } catch (Exception ignored) {}
        }
        // create a fresh executor and submit the speak task
        ensureExecutor();
        if (executor != null) executor.submit(() -> speakBlocking(text));
    }

    private void speakBlocking(String text) {
        try {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (os.contains("win")) {
                // use PowerShell and System.Speech
                String escaped = text.replace("'", "''"); // double single quotes for PowerShell single-quoted string literal
                String psCommand = "Add-Type -AssemblyName System.Speech; $s = New-Object System.Speech.Synthesis.SpeechSynthesizer; $s.Speak('" + escaped + "');";
                ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-Command", psCommand);
                pb.redirectErrorStream(true);
                Process proc = pb.start();
                currentProcess.set(proc);
                // consume output to prevent blocking
                try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        // discard output
                    }
                } catch (Exception ignored) {}
                try {
                    proc.waitFor();
                } catch (InterruptedException ie) {
                    // interrupted: destroy process
                    try { proc.destroyForcibly(); } catch (Exception ignored) {}
                    Thread.currentThread().interrupt();
                } finally {
                    currentProcess.compareAndSet(proc, null);
                }
            } else {
                // Non-Windows: try to use 'say' on macOS; otherwise no-op
                if (os.contains("mac") || os.contains("darwin")) {
                    ProcessBuilder pb = new ProcessBuilder("say", text);
                    pb.redirectErrorStream(true);
                    Process proc = pb.start();
                    currentProcess.set(proc);
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                        while (br.readLine() != null) {
                            // discard
                        }
                    } catch (Exception ignored) {}
                    try { proc.waitFor(); } catch (InterruptedException ie) { try { proc.destroyForcibly(); } catch (Exception ignored) {} Thread.currentThread().interrupt(); }
                    currentProcess.compareAndSet(proc, null);
                } else {
                    // unsupported OS: no-op but keep non-blocking behavior
                }
            }
        } catch (Exception e) {
            // swallow TTS errors to avoid breaking app
            e.printStackTrace();
        }
    }
}

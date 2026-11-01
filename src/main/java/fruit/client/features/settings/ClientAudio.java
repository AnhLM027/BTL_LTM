package fruit.client.features.settings;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;

public final class ClientAudio implements AutoCloseable {
    static final String[] CUES = {
        "click",
        "confirm",
        "deny",
        "match-found",
        "countdown",
        "go",
        "catch-1",
        "catch-2",
        "catch-3",
        "wrong",
        "bomb-warning",
        "bomb-hit",
        "combo",
        "freeze",
        "freeze-end",
        "magnet",
        "magnet-end",
        "target",
        "time-warning",
        "win",
        "lose",
        "draw"
    };
    final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(
                    r -> {
                        Thread t = new Thread(r, "orchard-audio");
                        t.setDaemon(true);
                        return t;
                    });
    final Map<String, Clip> clips = new ConcurrentHashMap<>();
    final Map<String, Double> levels = new ConcurrentHashMap<>();
    final Properties properties = new Properties();
    final Path preferenceFile =
            Path.of(System.getProperty("fruit.settings", "data/client-settings.properties"));
    public volatile double master = .7, musicVolume = .3, sfxVolume = .8;
    public volatile boolean muted, reducedMotion, available, ready, closed;
    volatile String desiredTheme = "";
    String activeTheme = "";
    int fadeToken, duckToken;
    double musicDuck = 1;
    long played;

    public ClientAudio() {
        try (InputStream input = Files.newInputStream(preferenceFile)) {
            properties.load(input);
            master = level("master", .7);
            musicVolume = level("music", .3);
            sfxVolume = level("sfx", .8);
            muted = Boolean.parseBoolean(properties.getProperty("muted", "false"));
            reducedMotion = Boolean.parseBoolean(properties.getProperty("reducedMotion", "false"));
        } catch (Exception ignored) {
        }
    }

    private double level(String key, double fallback) {
        try {
            double value = Double.parseDouble(properties.getProperty(key, ""));
            return Double.isFinite(value) ? Math.max(0, Math.min(1, value)) : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    public void preload() {
        executor.execute(
                () -> {
                    for (String cue : CUES) load(cue, "sfx/" + cue + ".wav");
                    load("music-menu", "music/menu.wav");
                    load("music-game", "music/game.wav");
                    ready = true;
                    available = !clips.isEmpty();
                    switchTheme(desiredTheme);
                });
    }

    private void load(String key, String path) {
        if (Boolean.getBoolean("fruit.audio.disabled")) return;
        Clip clip = null;
        try (InputStream resource = ClientAudio.class.getResourceAsStream("/fruit/" + path)) {
            if (resource == null) return;
            try (AudioInputStream input =
                    AudioSystem.getAudioInputStream(new BufferedInputStream(resource))) {
                clip = AudioSystem.getClip();
                clip.open(input);
                clips.put(key, clip);
                levels.put(key, 1.0);
            }
        } catch (Exception unavailable) {
            if (clip != null) clip.close();
        }
    }

    public synchronized int openClips() {
        return clips.size();
    }

    public void play(String key, double volume) {
        if (closed) return;
        executor.execute(
                () -> {
                    Clip clip = clips.get(key);
                    if (clip == null || muted || master <= 0 || sfxVolume <= 0) return;
                    if (clip.isRunning() && volume < .5) return;
                    // Tối đa tám SFX đồng thời; cue của mình được ưu tiên hơn đối thủ.
                    List<Map.Entry<String, Clip>> voices =
                            clips.entrySet().stream()
                                    .filter(
                                            e ->
                                                    !e.getKey().startsWith("music-")
                                                            && e.getValue().isRunning())
                                    .toList();
                    if (!clip.isRunning() && voices.size() >= 8) {
                        if (volume < .5) return;
                        voices.stream()
                                .min(
                                        Comparator.comparingDouble(
                                                e -> levels.getOrDefault(e.getKey(), 1.0)))
                                .ifPresent(e -> e.getValue().stop());
                    }
                    clip.stop();
                    clip.setFramePosition(0);
                    levels.put(key, volume);
                    gain(clip, master * sfxVolume * volume);
                    clip.start();
                    played++;
                });
    }

    public void theme(String value) {
        desiredTheme = value;
        if (!closed)
            executor.execute(
                    () -> {
                        if (ready) switchTheme(value);
                    });
    }

    private void switchTheme(String value) {
        if (value.equals(activeTheme)) return;
        String old = activeTheme;
        activeTheme = value;
        int token = ++fadeToken;
        Clip next = clips.get("music-" + value), previous = clips.get("music-" + old);
        // Khi đổi màn hình nhanh, fade cũ bị hủy; dừng cả loop đã mất chủ.
        for (var entry : clips.entrySet()) {
            if (entry.getKey().startsWith("music-")
                    && entry.getValue() != next
                    && entry.getValue() != previous) entry.getValue().stop();
        }
        if (next != null && !muted && musicVolume > 0) {
            gain(next, 0);
            next.setFramePosition(0);
            next.loop(Clip.LOOP_CONTINUOUSLY);
        }
        for (int i = 1; i <= 10; i++) {
            final double fraction = i / 10.0;
            executor.schedule(
                    () -> {
                        if (token != fadeToken || closed) return;
                        if (previous != null) {
                            gain(previous, master * musicVolume * (1 - fraction) * musicDuck);
                            if (fraction == 1) previous.stop();
                        }
                        if (next != null) gain(next, master * musicVolume * fraction * musicDuck);
                    },
                    i * 35L,
                    TimeUnit.MILLISECONDS);
        }
    }

    public void duckMusic() {
        if (!closed)
            executor.execute(
                    () -> {
                        musicDuck = .4;
                        int token = ++duckToken;
                        applyVolumes();
                        executor.schedule(
                                () -> {
                                    if (token == duckToken) {
                                        musicDuck = 1;
                                        applyVolumes();
                                    }
                                },
                                650,
                                TimeUnit.MILLISECONDS);
                    });
    }

    public void changed() {
        if (closed) return;
        executor.execute(
                () -> {
                    applyVolumes();
                    properties.setProperty("master", Double.toString(master));
                    properties.setProperty("music", Double.toString(musicVolume));
                    properties.setProperty("sfx", Double.toString(sfxVolume));
                    properties.setProperty("muted", Boolean.toString(muted));
                    properties.setProperty("reducedMotion", Boolean.toString(reducedMotion));
                    try {
                        Path parent = preferenceFile.toAbsolutePath().getParent();
                        if (parent != null) Files.createDirectories(parent);
                        try (OutputStream out = Files.newOutputStream(preferenceFile)) {
                            properties.store(out, "Fruit Battle settings");
                        }
                    } catch (IOException ignored) {
                    }
                });
    }

    private void applyVolumes() {
        for (var entry : clips.entrySet()) {
            boolean music = entry.getKey().startsWith("music-");
            Clip clip = entry.getValue();
            double v =
                    master
                            * (music
                                    ? musicVolume * musicDuck
                                    : sfxVolume * levels.getOrDefault(entry.getKey(), 1.0));
            gain(clip, v);
            if (music) {
                boolean selected = entry.getKey().equals("music-" + activeTheme);
                if (muted || v <= 0 || !selected) clip.stop();
                else if (!clip.isRunning()) clip.loop(Clip.LOOP_CONTINUOUSLY);
            } else if (muted || v <= 0) clip.stop();
        }
    }

    private void gain(Clip clip, double value) {
        double v = muted ? 0 : Math.max(0, Math.min(1, value));
        if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            FloatControl control = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            float db = v <= 0 ? control.getMinimum() : (float) (20 * Math.log10(v));
            control.setValue(Math.max(control.getMinimum(), Math.min(control.getMaximum(), db)));
        } else if (v == 0) clip.stop();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        executor.execute(
                () -> {
                    for (Clip clip : clips.values()) {
                        clip.stop();
                        clip.close();
                    }
                    clips.clear();
                });
        executor.shutdown();
    }
}

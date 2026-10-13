package Client.fx.util;

import javafx.scene.media.AudioClip;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Singleton sound manager for client UI audio effects and background music.
 * Supports playing short clips (.mp3, .wav) with caching and safe fallback.
 */
public final class SoundManager {
    private static final Logger LOGGER = Logger.getLogger(SoundManager.class.getName());
    private static final SoundManager INSTANCE = new SoundManager();

    public static final String SOUND_SHOP_SWITCH = "/Client/assets/sounds/shop-switch.mp3";
    public static final String SOUND_BUTTON_CLICK = "/Client/assets/sounds/button-click.mp3";
    public static final String SOUND_ACTION_SUCCESS = "/Client/assets/sounds/action-success.mp3";
    public static final String SOUND_ACTION_ERROR = "/Client/assets/sounds/action-error.mp3";
    public static final String SOUND_LOGOUT = "/Client/assets/sounds/logout.mp3";
    public static final String SOUND_ADMIN_BGM = "/Client/assets/sounds/admin-background-song.mp3";

    private final Map<String, AudioClip> clipCache = new ConcurrentHashMap<>();
    private final Map<String, MediaPlayer> loopPlayers = new ConcurrentHashMap<>();
    private boolean muted = false;

    private SoundManager() {
    }

    public static SoundManager getInstance() {
        return INSTANCE;
    }

    /**
     * Resolve resource path to URL.
     * Supports both absolute classpath (starts with '/') and simple filename.
     */
    private URL resolveUrl(String resourcePath) {
        if (resourcePath == null || resourcePath.isBlank()) return null;
        String normalized = resourcePath;
        if (!normalized.startsWith("/")) {
            normalized = "/Client/assets/sounds/" + normalized;
        }
        return getClass().getResource(normalized);
    }

    /**
     * Play a short sound effect. Uses cached AudioClip for low latency.
     */
    public void play(String resourcePath) {
        if (muted) return;
        try {
            AudioClip clip = clipCache.computeIfAbsent(resourcePath, path -> {
                URL url = resolveUrl(path);
                if (url == null) {
                    LOGGER.log(Level.FINE, "Sound resource not found: " + path);
                    return null;
                }
                try {
                    return new AudioClip(url.toExternalForm());
                } catch (Throwable t) {
                    LOGGER.log(Level.WARNING, "Failed to load audio clip: " + path, t);
                    return null;
                }
            });

            if (clip != null) {
                clip.play();
            }
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "Error playing sound: " + resourcePath, t);
        }
    }

    /**
     * Play a sound in infinite loop (e.g. background ambiance/music) at default volume.
     */
    public void playLoop(String resourcePath) {
        playLoop(resourcePath, 0.5);
    }

    /**
     * Play a sound in infinite loop with custom volume (0.0 to 1.0).
     */
    public void playLoop(String resourcePath, double volume) {
        if (muted) return;
        try {
            stopLoop(resourcePath);
            URL url = resolveUrl(resourcePath);
            if (url == null) {
                LOGGER.log(Level.FINE, "Sound loop resource not found: " + resourcePath);
                return;
            }
            Media media = new Media(url.toExternalForm());
            MediaPlayer player = new MediaPlayer(media);
            player.setVolume(Math.max(0.0, Math.min(1.0, volume)));
            player.setCycleCount(MediaPlayer.INDEFINITE);
            loopPlayers.put(resourcePath, player);
            player.play();
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "Error playing looped sound: " + resourcePath, t);
        }
    }

    /**
     * Stop a specific looped sound player.
     */
    public void stopLoop(String resourcePath) {
        MediaPlayer player = loopPlayers.remove(resourcePath);
        if (player != null) {
            try {
                player.stop();
                player.dispose();
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Stop all active loop players and sounds.
     */
    public void stopAll() {
        for (Map.Entry<String, MediaPlayer> entry : loopPlayers.entrySet()) {
            try {
                entry.getValue().stop();
                entry.getValue().dispose();
            } catch (Throwable ignored) {
            }
        }
        loopPlayers.clear();
        for (AudioClip clip : clipCache.values()) {
            if (clip != null) {
                try {
                    clip.stop();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    // Convenience sound effect triggers
    public void playButtonClick() {
        play(SOUND_BUTTON_CLICK);
    }

    public void playShopSwitch() {
        play(SOUND_SHOP_SWITCH);
    }

    public void playActionSuccess() {
        play(SOUND_ACTION_SUCCESS);
    }

    public void playActionError() {
        play(SOUND_ACTION_ERROR);
    }

    public void playLogout() {
        play(SOUND_LOGOUT);
    }

    public void playAdminBgm() {
        playLoop(SOUND_ADMIN_BGM, 0.45);
    }

    public void stopAdminBgm() {
        stopLoop(SOUND_ADMIN_BGM);
    }

    public boolean isMuted() {
        return muted;
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
        if (muted) {
            stopAll();
        }
    }
}

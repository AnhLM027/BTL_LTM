package fruit.client.features.settings;

import static fruit.common.Protocol.obj;

import fruit.client.application.ClientState;
import fruit.client.features.gameplay.ClientFeedback;

import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;

/** Resource integrity, event replay, saved settings and optional physical audio playback. */
public final class PresentationTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("fruit-presentation-");
        System.setProperty("fruit.settings", directory.resolve("settings.properties").toString());
        resources();
        settings();
        eventReplay();
        notices();
        check(
                fruit.client.shared.ui.DisplayFormats.matchTime(
                                "2026-10-31T19:05:00Z", java.time.ZoneId.of("Asia/Ho_Chi_Minh"))
                        .equals("01/11/2026 02:05"),
                "History time uses local date, including midnight rollover");
        check(
                fruit.client.shared.ui.DisplayFormats.matchTime("invalid").equals("—")
                        && fruit.client.shared.ui.DisplayFormats.matchTime("").equals("—"),
                "Missing or invalid timestamps have a readable fallback");
        if (Arrays.asList(args).contains("--device")) device();
        System.out.println("PASS PRESENTATION: " + checks + " checks");
    }

    private static void notices() {
        var notices = new fruit.client.features.gameplay.GameplayNotice();
        long now = 1_000_000_000L;
        check(notices.current(true, now) == null, "Gameplay footer is silent by default");
        notices.error("Kỹ năng đang hồi chiêu.", now);
        check(
                notices.current(true, now).text().contains("hồi chiêu"),
                "Rejected actions remain visible");
        check(
                notices.current(false, now).text().contains("Mất kết nối"),
                "Connection loss takes priority");
        check(
                notices.current(true, now + 3_000_000_001L) == null,
                "Action errors expire after three seconds");
        check(
                notices.current(false, now + 10_000_000_000L) != null,
                "Connection warning persists until reconnection");
        notices.clear();
        check(notices.current(true, now) == null, "New matches clear old errors");
    }

    private static void resources() throws Exception {
        for (String image : List.of("orchard.png", "sprites.png", "ui.png", "basket-frozen.png")) {
            try (InputStream in = resource(image)) {
                var decoded = ImageIO.read(in);
                check(decoded != null && decoded.getWidth() >= 32, "Image decodes: " + image);
                if (image.equals("basket-frozen.png")) {
                    int transparent = 0, visible = 0;
                    for (int y = 0; y < decoded.getHeight(); y++)
                        for (int x = 0; x < decoded.getWidth(); x++) {
                            int alpha = decoded.getRGB(x, y) >>> 24;
                            if (alpha == 0) transparent++;
                            if (alpha > 18) visible++;
                        }
                    check(
                            transparent > decoded.getWidth() * decoded.getHeight() / 5
                                    && visible > 1000,
                            "Frozen basket has real transparency and visible sprite content");
                }
                if (image.equals("sprites.png"))
                    check(
                            decoded.getWidth() == 9 * 32 && decoded.getHeight() == 32,
                            "Atlas has nine complete cells");
            }
        }
        referenceArt();
        for (String weight : List.of("Regular", "Bold", "Black")) {
            try (InputStream in = resource("fonts/NotoSans-" + weight + ".ttf")) {
                Font face = Font.createFont(Font.TRUETYPE_FONT, in);
                check(
                        face.canDisplayUpTo(
                                        "Đối thủ · Trái cây · Hứng đúng · Giỏ · Thắng · Nam châm ·"
                                                + " Ă Â Ê Ô Ơ Ư ă â ê ô ơ ư")
                                == -1,
                        "Bundled " + weight + " font covers Vietnamese glyphs");
                if (!weight.equals("Regular"))
                    check(
                            face.getFontName(Locale.ROOT).contains(weight),
                            "Bundled font is a real " + weight + " face");
            }
        }
        List<String> paths = new ArrayList<>();
        for (String cue : ClientAudio.CUES) paths.add("sfx/" + cue + ".wav");
        paths.addAll(List.of("music/menu.wav", "music/game.wav"));
        for (String path : paths) {
            try (InputStream file = resource(path);
                    AudioInputStream wave =
                            AudioSystem.getAudioInputStream(new BufferedInputStream(file))) {
                AudioFormat format = wave.getFormat();
                check(
                        format.getEncoding().equals(AudioFormat.Encoding.PCM_SIGNED)
                                && format.getSampleSizeInBits() == 16
                                && format.getChannels() == 1,
                        "Portable PCM16 mono: " + path);
                byte[] data = wave.readAllBytes();
                int peak = 0;
                double power = 0;
                for (int i = 0; i + 1 < data.length; i += 2) {
                    int value = (short) ((data[i] & 255) | (data[i + 1] << 8));
                    peak = Math.max(peak, Math.abs(value));
                    power += (double) value * value;
                }
                check(
                        peak > 1000 && peak < 30000 && power > 0,
                        "Audible non-clipping waveform: " + path);
                double duration = wave.getFrameLength() / format.getFrameRate();
                check(
                        path.startsWith("music") ? duration > 20 : duration < 2,
                        "Expected loop/SFX duration: " + path);
                if (path.startsWith("music")) {
                    int first = (short) ((data[0] & 255) | (data[1] << 8));
                    int last =
                            (short) ((data[data.length - 2] & 255) | (data[data.length - 1] << 8));
                    check(
                            Math.abs(first - last) < 600,
                            "Loop boundary has no large PCM discontinuity: " + path);
                }
            }
        }
    }

    private static void referenceArt() throws Exception {
        BufferedImage orchard = image("orchard-reference.png");
        check(
                orchard.getWidth() >= 800
                        && orchard.getHeight() >= 600
                        && orchard.getWidth() > orchard.getHeight(),
                "Playfield background has enough landscape resolution");
        BufferedImage frame = image("skin-frame.png");
        check(frame.getColorModel().hasAlpha(), "Wood frame retains its alpha channel");
        boolean clearCenter = true;
        for (int row = 2; row <= 8; row++)
            for (int col = 2; col <= 8; col++)
                clearCenter &=
                        (frame.getRGB(frame.getWidth() * col / 10, frame.getHeight() * row / 10)
                                        >>> 24)
                                < 8;
        check(clearCenter, "Wood frame center leaves live game content visible");
        atlas("portraits-reference.png", 2, 1);
        atlas("items-reference-v2.png", 4, 2);
    }

    private static BufferedImage image(String path) throws Exception {
        try (InputStream in = resource(path)) {
            BufferedImage image = ImageIO.read(in);
            check(image != null, "Reference image decodes: " + path);
            return image;
        }
    }

    private static void atlas(String path, int columns, int rows) throws Exception {
        BufferedImage image = image(path);
        check(image.getColorModel().hasAlpha(), "Sprite sheet retains transparency: " + path);
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                int left = (int) Math.round((double) image.getWidth() * col / columns);
                int right = (int) Math.round((double) image.getWidth() * (col + 1) / columns);
                int top = (int) Math.round((double) image.getHeight() * row / rows);
                int bottom = (int) Math.round((double) image.getHeight() * (row + 1) / rows);
                int visible = 0;
                for (int y = top; y < bottom; y++)
                    for (int x = left; x < right; x++)
                        if ((image.getRGB(x, y) >>> 24) > 32) visible++;
                int area = (right - left) * (bottom - top);
                check(
                        visible > area / 100 && visible < area * .95,
                        "Atlas cell has visible art and clear padding: "
                                + path
                                + " ["
                                + col
                                + ","
                                + row
                                + "]");
            }
        }
        for (int col = 1; col < columns; col++) {
            int boundary = (int) Math.round((double) image.getWidth() * col / columns);
            int solid = 0;
            for (int y = 0; y < image.getHeight(); y++)
                if ((image.getRGB(boundary, y) >>> 24) > 32) solid++;
            check(
                    solid < image.getHeight() / 100,
                    "Vertical cell separator is clear: " + path + " column " + col);
        }
        for (int row = 1; row < rows; row++) {
            int boundary = (int) Math.round((double) image.getHeight() * row / rows);
            int solid = 0;
            for (int x = 0; x < image.getWidth(); x++)
                if ((image.getRGB(x, boundary) >>> 24) > 32) solid++;
            check(
                    solid < image.getWidth() / 100,
                    "Horizontal cell separator is clear: " + path + " row " + row);
        }
    }

    private static InputStream resource(String path) {
        InputStream in = PresentationTest.class.getResourceAsStream("/fruit/" + path);
        if (in == null) throw new AssertionError("Missing packaged asset: " + path);
        return in;
    }

    private static void settings() throws Exception {
        System.setProperty("fruit.audio.disabled", "true");
        ClientAudio audio = new ClientAudio();
        audio.preload();
        barrier(audio);
        check(
                audio.ready && !audio.available,
                "No-device mode completes loading without breaking the game");
        audio.master = .42;
        audio.musicVolume = .23;
        audio.sfxVolume = .64;
        audio.muted = true;
        audio.reducedMotion = true;
        audio.changed();
        barrier(audio);
        audio.close();
        audio.executor.awaitTermination(3, TimeUnit.SECONDS);
        ClientAudio restored = new ClientAudio();
        check(
                restored.master == .42 && restored.musicVolume == .23 && restored.sfxVolume == .64,
                "Independent volume levels persist");
        check(restored.muted && restored.reducedMotion, "Mute and reduced-motion persist");
        restored.close();
    }

    @SuppressWarnings("unchecked")
    private static void eventReplay() throws Exception {
        ClientState model = new ClientState();
        ClientFeedback client = new ClientFeedback(model);
        model.user = obj("id", 1);
        Map<String, Object> player =
                obj("id", 1, "freeze", 0, "magnet", 0, "x", 400, "objects", List.of());
        Map<String, Object> snapshot =
                obj(
                        "matchId",
                        "a",
                        "phase",
                        "PLAYING",
                        "remaining",
                        48,
                        "players",
                        List.of(player));
        model.state = snapshot;
        Map<String, Object> catchEvent =
                obj(
                        "matchId",
                        "a",
                        "playerId",
                        1,
                        "objectId",
                        17,
                        "kind",
                        "APPLE",
                        "delta",
                        30,
                        "combo",
                        5,
                        "x",
                        400,
                        "y",
                        550);
        client.catchFeedback(catchEvent);
        client.catchFeedback(catchEvent);
        check(
                client.effects.size() == 1,
                "Duplicate server catch creates one effect and one sound request");
        check(
                client.effects.get(0).delta == 30,
                "Combo uses authoritative server delta, including bonus");
        Map<String, Object> unrelated = new LinkedHashMap<>(catchEvent);
        unrelated.put("matchId", "older");
        client.catchFeedback(unrelated);
        check(client.effects.size() == 1, "Stale match catch is ignored");
        for (int i = 20; i < 120; i++) {
            Map<String, Object> event = new LinkedHashMap<>(catchEvent);
            event.put("objectId", i);
            client.catchFeedback(event);
        }
        check(client.effects.size() == 48, "Burst of events respects effect budget");
        int before = client.effects.size();
        for (int i = 0; i < 30; i++) client.receiveState(snapshot);
        check(client.effects.size() == before, "Repeated GAME_STATE never replays catch effects");
        client.restoringMatch = true;
        client.lastSecond = 61;
        Map<String, Object> rejoin = new LinkedHashMap<>(snapshot);
        rejoin.put("remaining", 4.8);
        client.receiveState(rejoin);
        check(
                client.lastSecond == 5 && !client.restoringMatch,
                "Rejoin initializes warning clock without replaying old countdown");
        client.audio.close();
        client.audio.executor.awaitTermination(3, TimeUnit.SECONDS);
    }

    private static void device() throws Exception {
        System.clearProperty("fruit.audio.disabled");
        ClientAudio audio = new ClientAudio();
        audio.master = .6;
        audio.musicVolume = .25;
        audio.sfxVolume = .7;
        audio.muted = false;
        audio.preload();
        barrier(audio);
        check(audio.available, "Physical Java Sound mixer opens");
        check(
                audio.openClips() == ClientAudio.CUES.length + 2,
                "Every cue and both music clips are preloaded");
        for (Clip clip : audio.clips.values())
            check(
                    clip.isControlSupported(FloatControl.Type.MASTER_GAIN),
                    "Mixer supports independent gain control");
        audio.theme("menu");
        await(
                () -> audio.clips.get("music-menu").getLongFramePosition() > 0,
                "Menu loop advances on device");
        audio.play("catch-1", 1);
        barrier(audio);
        check(audio.played > 0, "SFX starts on audio executor");
        audio.theme("game");
        await(
                () ->
                        audio.clips.get("music-game").getLongFramePosition() > 0
                                && !audio.clips.get("music-menu").isRunning(),
                "Music crossfade retires previous loop");
        int count = audio.openClips();
        for (int i = 0; i < 80; i++) audio.play(ClientAudio.CUES[i % ClientAudio.CUES.length], .3);
        barrier(audio);
        check(audio.openClips() == count, "Many events do not allocate additional clips");
        check(
                audio.clips.entrySet().stream()
                                .filter(
                                        e ->
                                                !e.getKey().startsWith("music-")
                                                        && e.getValue().isRunning())
                                .count()
                        <= 8,
                "SFX concurrency is capped at eight voices");
        audio.theme("menu");
        audio.theme("game");
        audio.theme("");
        await(
                () ->
                        audio.clips.entrySet().stream()
                                .filter(e -> e.getKey().startsWith("music-"))
                                .noneMatch(e -> e.getValue().isRunning()),
                "Rapid scene transitions leave no orphan music loop");
        audio.muted = true;
        audio.changed();
        barrier(audio);
        check(
                audio.clips.values().stream().noneMatch(Clip::isRunning),
                "Mute stops music and SFX immediately");
        audio.close();
        audio.executor.awaitTermination(5, TimeUnit.SECONDS);
        check(audio.openClips() == 0, "All sound lines are released on exit");
        System.out.println(
                "Audio mixer playback, crossfade, mute, gain and fixed clip budget verified.");
    }

    private static void barrier(ClientAudio audio) throws Exception {
        audio.executor.submit(() -> {}).get(20, TimeUnit.SECONDS);
    }

    private static void await(java.util.function.BooleanSupplier ready, String message)
            throws Exception {
        long until = System.nanoTime() + 3_000_000_000L;
        while (!ready.getAsBoolean() && System.nanoTime() < until) Thread.sleep(20);
        check(ready.getAsBoolean(), message);
    }

    private static void check(boolean success, String message) {
        checks++;
        if (!success) throw new AssertionError(message);
    }
}

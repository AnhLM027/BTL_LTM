package fruit.client.features.settings;

import static fruit.client.shared.ui.UiComponents.button;
import static fruit.client.shared.ui.UiComponents.label;
import static fruit.client.shared.ui.UiComponents.panel;
import static fruit.client.shared.ui.UiComponents.spacer;
import static fruit.client.shared.ui.UiComponents.text;
import static fruit.client.shared.ui.UiTheme.INK;
import static fruit.client.shared.ui.UiTheme.MUTED;

import fruit.client.application.ClientScreens;
import fruit.client.features.gameplay.ClientFeedback;

import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** JavaFX dialog presentation. */
public final class AudioSettingsDialog {
    private AudioSettingsDialog() {}

    public static void show(ClientScreens ui) {
        ClientFeedback feedback = ui.feedback;

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(ui.stage);
        dialog.setTitle("Âm thanh & chuyển động");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        VBox content = panel(22);
        content.setPrefWidth(430);
        content.getChildren().add(label("THIẾT LẬP VƯỜN ĐẤU", 20, INK, true));
        content.getChildren()
                .addAll(
                        volumeRow(ui, "Âm lượng chung", "master", feedback.audio.master),
                        volumeRow(ui, "Nhạc nền", "music", feedback.audio.musicVolume),
                        volumeRow(ui, "Hiệu ứng", "sfx", feedback.audio.sfxVolume));
        CheckBox mute = new CheckBox("Tắt toàn bộ âm thanh (F8)");
        mute.setSelected(feedback.audio.muted);
        mute.setId("audio-mute");
        mute.selectedProperty()
                .addListener(
                        (o, a, b) -> {
                            feedback.audio.muted = b;
                            feedback.audio.changed();
                            if (ui.muteButton != null)
                                ui.muteButton.setText(b ? "Bật tiếng" : "Tắt tiếng");
                        });
        CheckBox motion = new CheckBox("Giảm chuyển động và tắt rung sân");
        motion.setSelected(feedback.audio.reducedMotion);
        motion.setId("reduced-motion");
        motion.selectedProperty()
                .addListener(
                        (o, a, b) -> {
                            feedback.audio.reducedMotion = b;
                            feedback.audio.changed();
                        });
        Label status =
                text(
                        feedback.audio.available
                                ? "Âm thanh sẵn sàng. Cài đặt được lưu tự động."
                                : "Đang tải hoặc máy không có thiết bị âm thanh. Bạn vẫn chơi bình"
                                        + " thường.",
                        12,
                        MUTED);
        content.getChildren()
                .addAll(
                        mute,
                        motion,
                        status,
                        button("Nghe thử", false, () -> feedback.audio.play("combo", 1)));
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    private static VBox volumeRow(ClientScreens ui, String title, String key, double value) {
        ClientFeedback feedback = ui.feedback;

        Slider slider = new Slider(0, 100, value * 100);
        slider.setId("volume-" + key);
        slider.setBlockIncrement(5);
        Label percentage = label(Math.round(value * 100) + "%", 12, MUTED, true);
        slider.valueProperty()
                .addListener(
                        (o, a, b) -> {
                            double v = b.doubleValue() / 100;
                            switch (key) {
                                case "master" -> feedback.audio.master = v;
                                case "music" -> feedback.audio.musicVolume = v;
                                default -> feedback.audio.sfxVolume = v;
                            }
                            percentage.setText(Math.round(v * 100) + "%");
                            feedback.audio.changed();
                        });
        return new VBox(8, new HBox(10, label(title, 14, INK, true), spacer(), percentage), slider);
    }
}

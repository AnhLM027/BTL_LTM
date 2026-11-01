package fruit.client.features.help;

import fruit.client.application.ClientScreens;
import fruit.client.shared.ui.GameArt;
import static fruit.client.shared.ui.UiComponents.divider;
import static fruit.client.shared.ui.UiComponents.label;
import static fruit.client.shared.ui.UiComponents.tag;
import static fruit.client.shared.ui.UiComponents.text;
import static fruit.client.shared.ui.UiTheme.CORAL;
import static fruit.client.shared.ui.UiTheme.INK;
import static fruit.client.shared.ui.UiTheme.LIME;
import static fruit.client.shared.ui.UiTheme.MUTED;
import static fruit.client.shared.ui.UiTheme.PANEL;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Illustrated rulebook using the fruit, basket and skill art from the match. */
public final class HelpDialog {
    private HelpDialog() {}

    public static void show(ClientScreens ui) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(ui.stage);
        dialog.setTitle("Cách chơi • Fruit Battle Online");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        ((Button) dialog.getDialogPane().lookupButton(ButtonType.CLOSE)).setText("Đóng");
        Canvas apple = new Canvas(64, 64);
        GameArt.sprite(apple.getGraphicsContext2D(), "APPLE", 32, 32, 3);
        HBox heading =
                new HBox(
                        16,
                        apple,
                        new VBox(
                                6,
                                label("SỔ TAY NHÀ VƯỜN", 11, LIME, true),
                                label("Hứng quả ngon. Giành điểm lớn.", 24, INK, true),
                                text(
                                        "1 vs 1  ·  60 giây  ·  Điểm cao hơn chiến thắng",
                                        13,
                                        MUTED)));
        heading.setAlignment(Pos.CENTER_LEFT);

        VBox basics =
                section(
                        "01 / VÀO VƯỜN ĐẤU",
                        "Bấm Tìm trận ở sảnh. Khi có đối thủ, chuẩn bị sau 3 giây đếm ngược.");
        basics.getChildren().add(illustration());
        basics.getChildren()
                .addAll(
                        label("←  →    DI CHUYỂN GIỎ", 18, LIME, true),
                        text(
                                "Giữ phím mũi tên trái / phải để đón quả. Nhìn ô MỤC TIÊU trước khi"
                                    + " hứng: mục tiêu đổi mỗi 15 giây; loại quả được tính ngay lúc"
                                    + " chạm giỏ.",
                                14,
                                INK),
                        text(
                                "MẸO NHỎ / Bỏ lỡ quả không bị trừ điểm và không mất combo.",
                                13,
                                LIME));
        VBox scoring =
                section(
                        "02 / THU HOẠCH & COMBO",
                        "Chọn đúng quả mục tiêu. Điểm có thể âm; hai người bằng điểm sẽ hòa.");
        scoring.getChildren()
                .addAll(
                        rule("APPLE", "ĐÚNG MỤC TIÊU", "+10 điểm · +10 năng lượng", LIME),
                        rule("BANANA", "SAI MỤC TIÊU", "−5 điểm · Combo về 0", CORAL),
                        rule("BOMB", "HỨNG BOM", "−10 điểm · Combo về 0", CORAL),
                        divider(),
                        label("5 quả đúng liên tiếp = +20 điểm", 20, LIME, true),
                        text(
                                "Mỗi mốc 5 combo thưởng thêm 20 điểm. Năng lượng tối đa 100."
                                        + " Nhịp rơi khó hơn từ giây 20 và 40 — hãy canh giỏ sớm!",
                                14,
                                INK));
        VBox skills =
                section(
                        "03 / TUNG CHIÊU ĐÚNG LÚC",
                        "Nhấn phím số 1, 2 hoặc 3 khi đủ năng lượng để tấn công hoặc hỗ trợ giỏ của"
                            + " mình.");
        skills.getChildren()
                .addAll(
                        skill(
                                "BOMB",
                                "1",
                                "Gửi bom",
                                "30 năng lượng",
                                "Gửi một quả bom có cảnh báo sang sân đối thủ."),
                        skill(
                                "FREEZE",
                                "2",
                                "Làm chậm",
                                "50 năng lượng",
                                "Giỏ đối thủ chậm 40% trong 3 giây."),
                        skill(
                                "MAGNET",
                                "3",
                                "Giỏ nam châm",
                                "90 năng lượng",
                                "Trong 5 giây, nam châm hút những quả đúng gần giỏ"),
                        text(
                                "HỒI CHIÊU CHUNG / 6 giây · Tấn công không cộng dồn; nam châm hỗ"
                                    + " trợ giỏ của bạn",
                                13,
                                LIME),
                        text(
                                "Mất kết nối? Đăng nhập lại để tiếp tục trận đang chạy. Đồng hồ"
                                        + " trận vẫn tiếp tục.",
                                12,
                                MUTED));
        TabPane tabs =
                new TabPane(
                        tab("Bắt đầu", basics), tab("Ghi điểm", scoring), tab("Kỹ năng", skills));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.setPrefHeight(Math.min(480, Math.max(300, ui.stage.getHeight() - 300)));
        VBox content = new VBox(16, heading, divider(), tabs);
        content.setPadding(new Insets(20));
        content.setPrefWidth(Math.min(800, ui.stage.getWidth() - 80));
        GameArt.decorate(content, 14);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setStyle("-fx-background-color:" + PANEL + ";-fx-font-size:14px;");
        dialog.setResizable(true);
        dialog.showAndWait();
    }

    private static VBox section(String title, String description) {
        VBox box = new VBox(16, label(title, 13, LIME, true), text(description, 14, INK));
        box.setPadding(new Insets(20));
        return box;
    }

    private static Tab tab(String title, VBox content) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background:" + PANEL + ";-fx-background-color:" + PANEL + ";");
        return new Tab(title, scroll);
    }

    private static Canvas icon(String kind) {
        Canvas canvas = new Canvas(56, 56);
        if (kind.equals("FREEZE") || kind.equals("MAGNET"))
            GameArt.drawIcon(canvas.getGraphicsContext2D(), kind, 28, 28, 48);
        else GameArt.sprite(canvas.getGraphicsContext2D(), kind, 28, 28, 2.8);
        canvas.setAccessibleText(kind);
        return canvas;
    }

    private static HBox rule(String kind, String title, String description, String color) {
        HBox row =
                new HBox(
                        18,
                        icon(kind),
                        new VBox(
                                6,
                                label(title, 12, color, true),
                                label(description, 17, INK, true)));
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static HBox skill(
            String kind, String key, String title, String cost, String description) {
        VBox words =
                new VBox(
                        6,
                        label(title + " / " + cost, 16, INK, true),
                        text(description, 13, MUTED));
        words.setMinWidth(0);
        HBox.setHgrow(words, Priority.ALWAYS);
        HBox row = new HBox(16, tag("PHÍM " + key), icon(kind), words);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10));
        row.setStyle("-fx-background-color:#fff6dc;-fx-border-color:#c5a66b;");
        return row;
    }

    private static StackPane illustration() {
        Canvas canvas = new Canvas(680, 190);
        StackPane frame = new StackPane(canvas);
        frame.setMinSize(0, 190);
        frame.setPrefHeight(190);
        canvas.widthProperty().bind(frame.widthProperty());
        Runnable paint =
                () -> {
                    GraphicsContext g = canvas.getGraphicsContext2D();
                    double w = canvas.getWidth();
                    g.clearRect(0, 0, w, 190);
                    if (w <= 32) return;
                    g.save();
                    g.beginPath();
                    g.rect(16, 16, w - 32, 158);
                    g.clip();
                    g.save();
                    g.translate(16, 16);
                    GameArt.orchard(g, w - 32, 158, true, false);
                    g.restore();
                    g.save();
                    g.translate(w * .5, 115);
                    g.scale(.9, .9);
                    GameArt.basket(g, 0, 0, true, false);
                    g.restore();
                    GameArt.sprite(g, "APPLE", w * .5, 55, 2.6);
                    GameArt.sprite(g, "BANANA", w * .24, 70, 2.2);
                    GameArt.sprite(g, "GRAPE", w * .76, 70, 2.2);
                    g.restore();
                    GameArt.woodFrame(g, w, 190);
                };
        canvas.widthProperty().addListener((o, a, b) -> paint.run());
        paint.run();
        return frame;
    }
}

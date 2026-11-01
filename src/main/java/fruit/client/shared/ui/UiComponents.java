package fruit.client.shared.ui;

import static fruit.client.shared.ui.GameArt.decorate;
import static fruit.client.shared.ui.UiTheme.INK;
import static fruit.client.shared.ui.UiTheme.LIME;
import static fruit.client.shared.ui.UiTheme.LINE;
import static fruit.client.shared.ui.UiTheme.MUTED;
import static fruit.client.shared.ui.UiTheme.PANEL;
import static fruit.client.shared.ui.UiTheme.bodyFont;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.List;
import java.util.Map;

/** Reusable JavaFX controls and panel factories. */
public final class UiComponents {
    private UiComponents() {}

    public static Label label(String value, double size, String color, boolean bold) {
        Label l = new Label(value);
        l.setTextFill(Color.web(color));
        l.setFont(Font.font(bodyFont, bold ? FontWeight.BOLD : FontWeight.NORMAL, size));
        l.setMinHeight(Region.USE_PREF_SIZE);
        return l;
    }

    public static Label text(String value, double size, String color) {
        Label l = label(value, size, color, false);
        l.setWrapText(true);
        return l;
    }

    public static Label tag(String value) {
        Label l = label(value, 10, LIME, true);
        l.setPadding(new Insets(7, 10, 7, 10));
        l.setStyle("-fx-background-color:#e6d7b0;-fx-border-color:#b59864;");
        return l;
    }

    public static Button button(String title, boolean primary, Runnable action) {
        Button b = new Button(title);
        b.getStyleClass().add(primary ? "primary" : "secondary");
        b.setOnAction(e -> action.run());
        b.setFocusTraversable(false);
        decorate(b, primary ? 8 : 6);
        return b;
    }

    public static VBox panel(double padding) {
        VBox box = new VBox(15);
        box.setPadding(new Insets(padding));
        box.getStyleClass().add("parchment-panel");
        box.setBackground(
                new Background(
                        new BackgroundFill(Color.web(PANEL), CornerRadii.EMPTY, new Insets(6))));
        decorate(box, 13);
        return box;
    }

    public static Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    public static Region divider() {
        Region r = new Region();
        r.setMinHeight(1);
        r.setMaxHeight(1);
        r.setStyle("-fx-background-color:" + LINE + ";");
        return r;
    }

    public static VBox formRow(String title, Node control) {
        return new VBox(7, label(title, 10, MUTED, true), control);
    }

    public static TextField field(String placeholder, String value) {
        TextField f = new TextField(value);
        f.setPromptText(placeholder);
        return f;
    }

    public static VBox stat(String value, String title, String color) {
        return new VBox(5, label(value, 24, color, true), label(title, 9, MUTED, true));
    }

    public static HBox metric(String name, int value) {
        HBox r =
                new HBox(
                        12,
                        label(name, 13, MUTED, false),
                        spacer(),
                        label(String.valueOf(value), 16, INK, true));
        r.setAlignment(Pos.CENTER_LEFT);
        return r;
    }

    public static HBox feature(String glyph, String title, String description, String color) {
        HBox box = new HBox(15);
        box.setPadding(new Insets(15, 18, 15, 18));
        box.setAlignment(Pos.CENTER_LEFT);
        box.setBackground(
                new Background(
                        new BackgroundFill(Color.web(PANEL), CornerRadii.EMPTY, new Insets(4))));
        decorate(box, 9);
        HBox.setHgrow(box, Priority.ALWAYS);
        box.getChildren()
                .addAll(
                        label(glyph, 20, color, true),
                        new VBox(5, label(title, 10, INK, true), text(description, 11, MUTED)));
        return box;
    }

    public static TableView<Map<String, Object>> table(List<Map<String, Object>> rows) {
        TableView<Map<String, Object>> table = new TableView<>();
        table.getItems().setAll(rows);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(label("Chưa có dữ liệu để hiển thị.", 14, MUTED, false));
        table.setFixedCellSize(48);
        return table;
    }

    public static void column(
            TableView<Map<String, Object>> table,
            String name,
            double width,
            java.util.function.Function<Map<String, Object>, String> value) {
        TableColumn<Map<String, Object>, String> col = new TableColumn<>(name);
        col.setPrefWidth(width);
        col.setMinWidth(Math.min(width, 65));
        col.setCellValueFactory(c -> new ReadOnlyStringWrapper(value.apply(c.getValue())));
        table.getColumns().add(col);
    }
}

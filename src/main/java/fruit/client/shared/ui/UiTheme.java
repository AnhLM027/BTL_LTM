package fruit.client.shared.ui;

import javafx.scene.paint.Color;

/** Shared colors, font names and stylesheet. */
public final class UiTheme {
    private UiTheme() {}

    public static final String NAVY = "#8f4c28";
    public static final String PANEL = "#ffedc4";
    public static final String LINE = "#925326";
    public static final String INK = "#42230f";
    public static final String MUTED = "#775334";
    public static final String LIME = "#285b26";
    public static final String CORAL = "#b63129";
    public static final String CYAN = "#17608a";
    public static final Color C_INK = Color.web(INK);
    public static final Color C_MUTED = Color.web(MUTED);
    public static final Color C_LIME = Color.web(LIME);
    public static final Color C_CORAL = Color.web(CORAL);
    public static final Color C_CYAN = Color.web(CYAN);
    public static String bodyFont = "Noto Sans";
    public static String pixelFont = "Pixelify Sans";
    public static final String CSS =
            """

.button { -fx-font-family:'Noto Sans'; -fx-font-size:12px; -fx-font-weight:bold; -fx-cursor:hand; -fx-padding:8 12; -fx-background-insets:3; -fx-background-radius:1; -fx-border-radius:0; -fx-effect:dropshadow(gaussian,#55280966,1,0,0,2); }
.primary { -fx-text-fill:#fff6d9; -fx-background-color:linear-gradient(to bottom,#668e38,#2b5d29); -fx-padding:12 25; }
.primary:hover { -fx-background-color:linear-gradient(to bottom,#7da545,#376f31); } .button:pressed { -fx-translate-y:2; }
.secondary { -fx-text-fill:#42230f; -fx-background-color:linear-gradient(to bottom,#fff4d4,#f2cf8e); }
.secondary:hover,.active { -fx-text-fill:#42230f; -fx-background-color:linear-gradient(to bottom,#ffe2a1,#dfad60); }
.skill-button { -fx-padding:7 18; -fx-background-insets:6; }
.button:disabled { -fx-opacity:.64; }
.compact .top-bar .button { -fx-font-size:10px; -fx-padding:7 8; }
.compact .brand-name { -fx-font-size:23px; } .compact .brand-subtitle { -fx-font-size:8px; }
.compact .skill-button { -fx-padding:6 12; }
.text-field { -fx-background-color:#fff8e4; -fx-text-fill:#42230f; -fx-prompt-text-fill:#9b7754; -fx-font-size:14px; -fx-padding:10 12; -fx-background-radius:0; -fx-border-color:#b58044 #d5ab6a #895428 #d5ab6a; -fx-border-width:2 2 3 2; }
.text-field:focused { -fx-border-color:#6c8c3b; }
.table-view { -fx-background-color:#ffedc4; -fx-border-color:#925326; -fx-border-width:3; -fx-padding:2; }
.table-view .column-header-background,.table-view .column-header,.table-view .filler { -fx-background-color:#eac78b; }
.table-view .column-header { -fx-size:38; -fx-border-color:transparent; }
.table-view .column-header .label { -fx-text-fill:#563312; -fx-font-size:10; -fx-font-weight:bold; -fx-alignment:CENTER_LEFT; -fx-padding:0 12; }
.table-row-cell { -fx-background-color:#fff2d2; -fx-border-color:transparent transparent #e4ca96 transparent; -fx-table-cell-border-color:transparent; }
.table-row-cell:odd { -fx-background-color:#f7e4ba; } .table-row-cell:selected { -fx-background-color:#dbe1ac; }
.table-cell { -fx-text-fill:#42230f; -fx-font-size:13; -fx-alignment:CENTER_LEFT; -fx-padding:8 12; }
.scroll-bar { -fx-background-color:#ecd3a2; } .scroll-bar .thumb { -fx-background-color:#a57b44; -fx-background-radius:0; }
.scroll-pane,.scroll-pane>.viewport { -fx-background-color:#ffedc4; } .tab-pane .tab-header-background { -fx-background-color:transparent; }
.screen-scroll,.screen-scroll>.viewport { -fx-background-color:transparent; -fx-padding:0; }
.tab { -fx-background-color:#e5c48c; -fx-background-radius:0; -fx-padding:9 18; } .tab .tab-label { -fx-text-fill:#563312; } .tab:selected { -fx-background-color:#537337; } .tab:selected .tab-label { -fx-text-fill:#fff4d9; }
.dialog-pane { -fx-font-family:'Noto Sans'; -fx-background-color:#ffedc4; } .dialog-pane .label { -fx-text-fill:#42230f; }
.tooltip { -fx-background-color:#573219; -fx-text-fill:#fff4d9; }
.slider .track { -fx-background-color:#d9b678; } .slider .thumb { -fx-background-color:#3e6d2c; }
.check-box .box { -fx-background-color:#f9e6bd; -fx-border-color:#9b713e; } .check-box:selected .mark { -fx-background-color:#356b45; }

""";
}

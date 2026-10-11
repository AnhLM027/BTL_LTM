package Client.view;

import Client.ui.AssetLoader;
import Client.ui.ScaledBackgroundPanel;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;

/** Result card displayed inside MainClientFrame. */
public final class EndGameScreen extends JPanel implements ClientScreen {
    private final Timer returnTimer;
    private int secondsRemaining = 5;
    private boolean returned;

    public EndGameScreen(String result, String myScore, String opponentScore, Runnable onReturn) {
        setLayout(new BorderLayout());
        ScaledBackgroundPanel background = new ScaledBackgroundPanel(
                AssetLoader.icon("backgrounds/lobby-orchard-background.png"));
        background.setLayout(new BorderLayout());
        add(background, BorderLayout.CENTER);

        String assetPath = switch (result) {
            case "WIN" -> "results/result-win.png";
            case "LOSE" -> "results/result-lose.png";
            default -> "results/result-draw.png";
        };
        var resultIcon = AssetLoader.icon(assetPath);
        JLabel resultLabel = resultIcon == null ? new JLabel(result, SwingConstants.CENTER)
                : new JLabel(AssetLoader.scaleToFit(resultIcon, 340, 136), SwingConstants.CENTER);
        if (resultIcon == null) {
            resultLabel.setFont(new Font("Arial", Font.BOLD, 56));
            resultLabel.setForeground(Color.WHITE);
        }
        resultLabel.setBorder(BorderFactory.createEmptyBorder(40, 0, 20, 0));

        JPanel scores = new JPanel(new GridLayout(1, 3, 20, 0));
        scores.setOpaque(false);
        scores.setBorder(BorderFactory.createEmptyBorder(10, 60, 10, 60));
        scores.add(score("Bạn: " + myScore, new Color(80, 200, 120)));
        scores.add(score("VS", new Color(255, 220, 100)));
        scores.add(score("Đối thủ: " + opponentScore, new Color(255, 120, 100)));

        JPanel center = new JPanel(new BorderLayout(0, 10));
        center.setOpaque(false);
        center.add(resultLabel, BorderLayout.CENTER);
        center.add(scores, BorderLayout.SOUTH);
        background.add(center, BorderLayout.CENTER);

        JLabel countdown = new JLabel("Tự động quay về phòng cũ sau 5 giây", SwingConstants.CENTER);
        countdown.setForeground(Color.WHITE);
        countdown.setFont(new Font("Arial", Font.BOLD, 15));

        JButton back = new JButton("Trở về phòng cũ");
        back.setFont(new Font("Arial", Font.BOLD, 16));
        back.setPreferredSize(new Dimension(180, 44));
        back.setForeground(Color.WHITE);
        back.setBackground(new Color(0, 102, 204));
        back.setFocusPainted(false);
        back.setBorderPainted(false);
        back.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        returnTimer = new Timer(1_000, null);
        Runnable goBack = () -> {
            if (returned) return;
            returned = true;
            returnTimer.stop();
            onReturn.run();
        };
        back.addActionListener(event -> goBack.run());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 16));
        actions.setOpaque(false);
        actions.add(countdown);
        actions.add(back);
        background.add(actions, BorderLayout.SOUTH);

        returnTimer.addActionListener(event -> {
            secondsRemaining--;
            if (secondsRemaining <= 0) {
                countdown.setText("Đang mở phòng cũ...");
                goBack.run();
            } else {
                countdown.setText("Tự động quay về phòng cũ sau " + secondsRemaining + " giây");
            }
        });
        returnTimer.start();
    }

    @Override public void onHide() { returnTimer.stop(); }

    private JLabel score(String text, Color color) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setFont(new Font("Arial", Font.BOLD, 26));
        label.setForeground(color);
        return label;
    }
}

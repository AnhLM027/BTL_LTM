package Client.view;

import Client.ui.AssetLoader;
import Client.ui.ScaledBackgroundPanel;

import javax.swing.*;
import java.awt.*;

/**
 * Screen displayed after a match ends. Shows the result image (WIN / LOSE / DRAW)
 * and both players' scores before returning the user to the Lobby.
 */
public class EndGameScreen extends JFrame {

    /**
     * @param result        "WIN", "LOSE", or "DRAW" (from the server)
     * @param myScore       this player's final score
     * @param opponentScore the opponent's final score
     * @param onReturn      callback run when the user clicks "Trở về Lobby"
     */
    public EndGameScreen(String result, String myScore, String opponentScore, Runnable onReturn) {
        setTitle("Kết thúc trận");
        setSize(810, 540);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);

        // --- Background ---
        ImageIcon bg = AssetLoader.icon("backgrounds/lobby-orchard-background.png");
        ScaledBackgroundPanel bgPanel = new ScaledBackgroundPanel(bg);
        bgPanel.setLayout(new BorderLayout(0, 0));
        setContentPane(bgPanel);

        // --- Result image (WIN / LOSE / DRAW) ---
        String assetPath = switch (result) {
            case "WIN"  -> "results/result-win.png";
            case "LOSE" -> "results/result-lose.png";
            default     -> "results/result-draw.png";
        };
        ImageIcon resultIcon = AssetLoader.icon(assetPath);
        JLabel resultLabel;
        if (resultIcon != null) {
            resultLabel = new JLabel(AssetLoader.scaleToFit(resultIcon, 340, 136), SwingConstants.CENTER);
        } else {
            resultLabel = new JLabel(result, SwingConstants.CENTER);
            resultLabel.setFont(new Font("Arial", Font.BOLD, 56));
            resultLabel.setForeground(Color.WHITE);
        }
        resultLabel.setBorder(BorderFactory.createEmptyBorder(40, 0, 20, 0));

        // --- Score display ---
        JPanel scorePanel = new JPanel(new GridLayout(1, 3, 20, 0));
        scorePanel.setOpaque(false);
        scorePanel.setBorder(BorderFactory.createEmptyBorder(10, 60, 10, 60));

        JLabel myScoreLbl = makeScoreLabel("Bạn: " + myScore, new Color(80, 200, 120));
        JLabel vsLbl      = makeScoreLabel("VS", new Color(255, 220, 100));
        JLabel oppLbl     = makeScoreLabel("Đối thủ: " + opponentScore, new Color(255, 120, 100));
        scorePanel.add(myScoreLbl);
        scorePanel.add(vsLbl);
        scorePanel.add(oppLbl);

        // --- Center panel ---
        JPanel center = new JPanel(new BorderLayout(0, 10));
        center.setOpaque(false);
        center.add(resultLabel, BorderLayout.CENTER);
        center.add(scorePanel, BorderLayout.SOUTH);

        // --- Return button ---
        JButton returnBtn = new JButton("Trở về Lobby");
        returnBtn.setFont(new Font("Arial", Font.BOLD, 16));
        returnBtn.setPreferredSize(new Dimension(180, 44));
        returnBtn.setForeground(Color.WHITE);
        returnBtn.setBackground(new Color(0, 102, 204));
        returnBtn.setFocusPainted(false);
        returnBtn.setBorderPainted(false);
        returnBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        returnBtn.addActionListener(e -> {
            dispose();
            onReturn.run();
        });

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 16));
        bottomPanel.setOpaque(false);
        bottomPanel.add(returnBtn);

        bgPanel.add(center, BorderLayout.CENTER);
        bgPanel.add(bottomPanel, BorderLayout.SOUTH);

        setVisible(true);
    }

    private JLabel makeScoreLabel(String text, Color color) {
        JLabel lbl = new JLabel(text, SwingConstants.CENTER);
        lbl.setFont(new Font("Arial", Font.BOLD, 26));
        lbl.setForeground(color);
        return lbl;
    }
}

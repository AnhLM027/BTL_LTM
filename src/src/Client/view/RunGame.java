package Client.view;

import Client.network.TcpGameClient;
import Client.ui.AssetLoader;
import Server.network.ProtocolMessage;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.File;
import javax.swing.ImageIcon;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * TCP-driven fruit-catching screen. The server remains the sole score authority.
 */
public final class RunGame extends JFrame implements TcpGameClient.MessageListener {
    /** Coordinates exchanged with the server are always in this logical space. */
    private static final int LOGICAL_WIDTH = 800;
    private static final int LOGICAL_HEIGHT = 600;
    private static final int WINDOW_WIDTH = 900;
    private static final int WINDOW_HEIGHT = 700;
    private static final int BASKET_Y = 505;
    private static final int BASKET_WIDTH = 120;
    private static final int BASKET_HEIGHT = 87;

    private final TcpGameClient client;
    private final long localPlayerId;
    private final Map<Integer, FruitDefinition> fruits = new HashMap<>();
    private BasketDefinition basket;
    private final List<FruitInstance> scheduledSpawns = new ArrayList<>();
    private final List<FruitInstance> activeFruits = new ArrayList<>();
    private final Set<Long> submittedCatches = new HashSet<>();
    private final GamePanel gamePanel = new GamePanel();
    private final ImageIcon gameBackground = AssetLoader.icon("backgrounds/game-orchard-background.png");
    private final JLabel myScoreLabel = new JLabel("Điểm của bạn: 0");
    private final JLabel opponentScoreLabel = new JLabel("Đối thủ: 0", SwingConstants.RIGHT);
    private final JLabel timerLabel = new JLabel("Chờ trận đấu...", SwingConstants.CENTER);
    private final JLabel objectiveLabel = new JLabel("", SwingConstants.CENTER);
    private final JLabel controlsLabel = new JLabel("", SwingConstants.CENTER);
    private final JLabel statusLabel = new JLabel("Đang chờ cấu hình trận...", SwingConstants.CENTER);
    private final Timer renderTimer;

    // Opponent status icons (loaded once)
    private final ImageIcon iconOpponentDisconnected = AssetLoader.icon("icons/opponent-disconnected.png");
    private final ImageIcon iconOpponentReconnected  = AssetLoader.icon("icons/opponent-reconnected.png");
    private final JLabel opponentStatusIcon = new JLabel();

    private long matchId = -1;
    private int durationSeconds = 30;
    private long startedAtMillis = -1;
    private boolean orderMode;
    private final List<Integer> targetFruitIds = new ArrayList<>();
    private int comboCount;
    private int basketX = LOGICAL_WIDTH / 2 - BASKET_WIDTH / 2;
    private boolean resultShown;
    private boolean awaitingResult;

    public RunGame(TcpGameClient client, long localPlayerId) {
        this.client = client;
        this.localPlayerId = localPlayerId;
        client.addListener(this);
        setTitle("Fruit Battle Online");
        setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        setMinimumSize(new Dimension(640, 500));
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        add(createTopPanel(), BorderLayout.NORTH);
        add(gamePanel, BorderLayout.CENTER);
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                handleKey(event.getKeyCode());
            }
        });
        setFocusable(true);
        renderTimer = new Timer(25, event -> tick());
        renderTimer.start();
    }

    /**
     * Kept only so legacy UDP screens still compile; they must be migrated to TcpGameClient.
     */
    @Deprecated
    public RunGame(String roomId, String playerId) {
        throw new IllegalStateException("RunGame now requires an authenticated TcpGameClient");
    }

    private JPanel createTopPanel() {
        JPanel top = new JPanel(new BorderLayout(8, 4));
        top.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        top.add(myScoreLabel, BorderLayout.WEST);
        top.add(timerLabel, BorderLayout.CENTER);
        // Opponent score + status icon on the right
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        rightPanel.setOpaque(false);
        rightPanel.add(opponentStatusIcon);
        rightPanel.add(opponentScoreLabel);
        top.add(rightPanel, BorderLayout.EAST);
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(top, BorderLayout.NORTH);
        JPanel info = new JPanel(new GridLayout(2, 1));
        info.setOpaque(false);
        info.add(objectiveLabel);
        wrapper.add(info, BorderLayout.SOUTH);
        wrapper.add(statusLabel, BorderLayout.CENTER);
        return wrapper;
    }

    @Override
    public void onMessage(ProtocolMessage message) {
        SwingUtilities.invokeLater(() -> handleMessage(message));
    }

    private void handleMessage(ProtocolMessage message) {
        try {
            switch (message.type()) {
                case "MATCH_PREPARE" -> prepare(message);
                case "MATCH_REJOIN" -> {
                    matchId = longValue(message, "matchId");
                    statusLabel.setText("Đã kết nối lại trận đấu...");
                }
                case "FRUIT_CONFIG" -> fruits.put(integer(message, "fruitId"), new FruitDefinition(
                        integer(message, "fruitId"), integer(message, "groupId"), message.requiredField("fruitCode"),
                        message.requiredField("fruitName"), message.fields().getOrDefault("assetPath", "")));
                case "BASKET_CONFIG" -> basket = new BasketDefinition(integer(message, "basketId"),
                        message.requiredField("basketName"), message.fields().getOrDefault("assetPath", ""));
                case "FRUIT_SPAWN" -> scheduledSpawns.add(new FruitInstance(
                        longValue(message, "fruitInstanceId"), message.fields().containsKey("fruitId") ? integer(message, "fruitId") : null,
                        Boolean.parseBoolean(message.fields().getOrDefault("isBomb", "false")),
                        longValue(message, "spawnOffsetMs"), integer(message, "xPosition"),
                        integer(message, "fallDurationMs")));
                case "MATCH_PREPARE_END" -> sendReady(message);
                case "MATCH_START" -> start(message);
                case "SCORE_UPDATE" -> updateScore(message);
                case "CATCH_ACK" -> {
                }
                case "OPPONENT_DISCONNECTED" -> {
                        if (iconOpponentDisconnected != null) {
                            Image s = iconOpponentDisconnected.getImage().getScaledInstance(20, 20, Image.SCALE_SMOOTH);
                            opponentStatusIcon.setIcon(new ImageIcon(s));
                        }
                        statusLabel.setText("Đối thủ mất kết nối — chờ tối đa " + message.fields().getOrDefault("graceSeconds", "30") + " giây");
                }
                case "OPPONENT_RECONNECTED" -> {
                        if (iconOpponentReconnected != null) {
                            Image s = iconOpponentReconnected.getImage().getScaledInstance(20, 20, Image.SCALE_SMOOTH);
                            opponentStatusIcon.setIcon(new ImageIcon(s));
                        }
                        statusLabel.setText("Đối thủ đã kết nối lại");
                }
                case "ERROR" ->
                        statusLabel.setText("Catch bị từ chối: " + message.fields().getOrDefault("message", "không hợp lệ"));
                case "MATCH_RESULT" -> showResult(message);
                default -> {
                }
            }
        } catch (Exception exception) {
            JOptionPane.showMessageDialog(this, "Không thể xử lý dữ liệu trận: " + exception.getMessage(),
                    "Lỗi giao thức", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void prepare(ProtocolMessage message) throws Exception {
        matchId = longValue(message, "matchId");
        durationSeconds = integer(message, "durationSeconds");
        fruits.clear();
        basket = null;
        scheduledSpawns.clear();
        activeFruits.clear();
        submittedCatches.clear();
        resultShown = false;
        awaitingResult = false;
        comboCount = 0;
        startedAtMillis = -1;
        basketX = LOGICAL_WIDTH / 2 - BASKET_WIDTH / 2;
        orderMode = "ORDER".equals(message.fields().getOrDefault("modeCode", "CLASSIC"));
        targetFruitIds.clear();
        for (int index = 1; index <= 3; index++) {
            String target = message.fields().get("targetFruitId_" + index);
            if (target != null && !target.isBlank()) targetFruitIds.add(Integer.parseInt(target));
        }
        objectiveLabel.setText(orderMode ? "Đơn hàng: hứng đúng 3 loại quả mục tiêu" : "CLASSIC: hứng quả, né bom");
        controlsLabel.setText("<html><center><b>Điều khiển</b><br>← &nbsp;&nbsp; →<br><small>← / →: Di chuyển giỏ</small></center></html>");
        controlsLabel.setFont(new Font("Arial", Font.PLAIN, 13));
        controlsLabel.setForeground(new Color(35, 80, 55));
        timerLabel.setText("Đang chuẩn bị...");
        statusLabel.setText("Đang chờ hai người chơi sẵn sàng...");
    }

    private void start(ProtocolMessage message) throws Exception {
        if (longValue(message, "matchId") != matchId) return;
        startedAtMillis = LocalDateTime.parse(message.requiredField("startedAt"))
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        statusLabel.setText("Trận đấu đang diễn ra");
        requestFocusInWindow();
    }

    private void sendReady(ProtocolMessage message) throws Exception {
        if (longValue(message, "matchId") != matchId) return;
        client.send(new ProtocolMessage("MATCH_READY", Map.of("matchId", Long.toString(matchId))));
        statusLabel.setText("Đã sẵn sàng, đang chờ đối thủ...");
    }

    private void updateScore(ProtocolMessage message) throws Exception {
        if (longValue(message, "matchId") != matchId) return;
        long playerId = longValue(message, "playerId");
        String score = message.requiredField("score");
        if (playerId == localPlayerId) {
            myScoreLabel.setText("Điểm của bạn: " + score);
            comboCount = integer(message, "comboCount");
            int bonus = integer(message, "comboBonus");
            if (bonus > 0) statusLabel.setText("COMBO! Thưởng +" + bonus);
        }
        else opponentScoreLabel.setText("Đối thủ: " + score);
    }

    private void tick() {
        if (startedAtMillis < 0 || resultShown) return;
        long elapsed = Math.max(0, System.currentTimeMillis() - startedAtMillis);
        long remaining = Math.max(0, durationSeconds * 1_000L - elapsed);
        if (remaining == 0) {
            awaitingResult = true;
            statusLabel.setText("Hết giờ — đang chốt kết quả từ server...");
            gamePanel.repaint();
            return;
        }
        timerLabel.setText("Thời gian: " + ((remaining + 999) / 1_000));
        scheduledSpawns.removeIf(fruit -> {
            if (fruit.spawnOffsetMs() <= elapsed) {
                activeFruits.add(fruit);
                return true;
            }
            return false;
        });
        activeFruits.removeIf(fruit -> elapsed - fruit.spawnOffsetMs() > fruit.fallDurationMs() + 500L);
        gamePanel.repaint();
    }

    private void handleKey(int keyCode) {
        if (startedAtMillis < 0 || resultShown || awaitingResult || basket == null) return;
        if (keyCode == KeyEvent.VK_LEFT) basketX = Math.max(0, basketX - 25);
        else if (keyCode == KeyEvent.VK_RIGHT) basketX = Math.min(LOGICAL_WIDTH - BASKET_WIDTH, basketX + 25);
        gamePanel.repaint();
    }

    private void submitCatch(FruitInstance fruit) {
        if (awaitingResult || !submittedCatches.add(fruit.instanceId()) || basket == null) return;
        activeFruits.remove(fruit);
        try {
            client.send(new ProtocolMessage("CATCH_EVENT", Map.of("matchId", Long.toString(matchId),
                    "fruitInstanceId", Long.toString(fruit.instanceId()),
                    "basketId", Integer.toString(basket.basketId()))));
        } catch (IOException exception) {
            submittedCatches.remove(fruit.instanceId());
            JOptionPane.showMessageDialog(this, "Không gửi được CatchEvent: " + exception.getMessage());
        }
    }

    private void showResult(ProtocolMessage message) throws Exception {
        if (longValue(message, "matchId") != matchId || resultShown) return;
        resultShown = true;
        renderTimer.stop();
        String result       = message.requiredField("result");
        String myScore      = message.requiredField("score");
        String opponentScore = message.requiredField("opponentScore");
        setVisible(false); // hide game screen while result is showing
        new EndGameScreen(result, myScore, opponentScore, () -> {
            dispose();
            new LobbyScreen(Long.toString(localPlayerId), "");
        });
    }

    @Override
    public void dispose() {
        renderTimer.stop();
        client.removeListener(this);
        super.dispose();
    }

    private long longValue(ProtocolMessage message, String name) throws Exception {
        return Long.parseLong(message.requiredField(name));
    }

    private int integer(ProtocolMessage message, String name) throws Exception {
        return Integer.parseInt(message.requiredField(name));
    }

    private final class GamePanel extends JPanel {
        GamePanel() {
            setBackground(new Color(219, 245, 255));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double scaleX = getWidth() / (double) LOGICAL_WIDTH;
            double scaleY = getHeight() / (double) LOGICAL_HEIGHT;
            g.scale(scaleX, scaleY);
            if (gameBackground != null) drawAspectCover(g, gameBackground.getImage(), LOGICAL_WIDTH, LOGICAL_HEIGHT);
            drawControlsOverlay(g);
            if (orderMode) drawOrderOverlay(g);
            drawComboBar(g);
            long elapsed = startedAtMillis < 0 ? 0 : Math.max(0, System.currentTimeMillis() - startedAtMillis);
            List<FruitInstance> collisions = new ArrayList<>();
            for (FruitInstance fruit : activeFruits) {
                FruitDefinition definition = fruit.fruitId() == null ? null : fruits.get(fruit.fruitId());
                int y = 80 + (int) Math.min(425, (elapsed - fruit.spawnOffsetMs()) * 425L / fruit.fallDurationMs());
                int x = Math.max(0, Math.min(LOGICAL_WIDTH - 42, fruit.xPosition()));
                if (fruit.isBomb()) {
                    drawBomb(g, x, y);
                } else {
                    ImageIcon icon = definition == null ? null : asset(definition.assetPath());
                    if (icon != null) g.drawImage(icon.getImage(), x, y, 38, 38, null);
                    else {
                        g.setColor(colorFor(definition == null ? 0 : definition.groupId()));
                        g.fillOval(x, y, 38, 38);
                    }
                    g.setColor(Color.DARK_GRAY);
                    g.drawOval(x, y, 38, 38);
                    g.drawString(definition == null ? "?" : definition.fruitName(), x - 8, y - 3);
                }
                if (y + 38 >= BASKET_Y && x + 38 >= basketX && x <= basketX + BASKET_WIDTH) collisions.add(fruit);
            }
            if (basket != null) {
                ImageIcon icon = asset(basket.assetPath());
                if (icon != null) g.drawImage(icon.getImage(), basketX, BASKET_Y, BASKET_WIDTH, BASKET_HEIGHT, null);
                else {
                    g.setColor(new Color(185, 123, 59));
                    g.fillRoundRect(basketX, BASKET_Y, BASKET_WIDTH, BASKET_HEIGHT, 12, 12);
                }
                g.setColor(Color.BLACK);
                g.drawRoundRect(basketX, BASKET_Y, BASKET_WIDTH, BASKET_HEIGHT, 12, 12);
                g.setColor(new Color(20, 80, 50));
                g.drawString("Bạn", basketX + 10, BASKET_Y - 8);
                g.drawString(basket.basketName(), basketX + 7, BASKET_Y + 29);
            }
            g.dispose();
            for (FruitInstance fruit : collisions) submitCatch(fruit);
        }

        private void drawControlsOverlay(Graphics2D g) {
            int width = 190;
            int height = 58;
            g.setColor(new Color(255, 255, 255, 205));
            g.fillRoundRect(14, 14, width, height, 14, 14);
            g.setColor(new Color(22, 82, 55));
            g.setStroke(new BasicStroke(1.5f));
            g.drawRoundRect(14, 14, width, height, 14, 14);
            g.setFont(new Font("Arial", Font.BOLD, 14));
            g.drawString("Điều khiển", 26, 34);
            g.setFont(new Font("Arial", Font.PLAIN, 13));
            g.drawString("← / →  Di chuyển giỏ", 26, 55);
        }

        private void drawBomb(Graphics2D g, int x, int y) {
            g.setColor(new Color(45, 45, 50));
            g.fillOval(x, y, 38, 38);
            g.setColor(Color.BLACK);
            g.drawOval(x, y, 38, 38);
            g.setColor(new Color(255, 190, 0));
            g.setStroke(new BasicStroke(3f));
            g.drawLine(x + 27, y + 8, x + 37, y - 3);
            g.setColor(Color.WHITE);
            g.setFont(new Font("Arial", Font.BOLD, 17));
            g.drawString("!", x + 16, y + 26);
        }

        private void drawOrderOverlay(Graphics2D g) {
            g.setColor(new Color(255, 252, 220, 220));
            g.fillRoundRect(560, 14, 225, 60, 12, 12);
            g.setColor(new Color(22, 82, 55));
            g.drawRoundRect(560, 14, 225, 60, 12, 12);
            g.setFont(new Font("Arial", Font.BOLD, 13));
            g.drawString("Đơn hàng", 570, 34);
            for (int index = 0; index < targetFruitIds.size(); index++) {
                FruitDefinition fruit = fruits.get(targetFruitIds.get(index));
                ImageIcon icon = fruit == null ? null : asset(fruit.assetPath());
                int x = 640 + index * 45;
                if (icon != null) g.drawImage(icon.getImage(), x, 24, 34, 34, null);
                else g.drawString("?", x + 12, 47);
            }
        }

        private void drawComboBar(Graphics2D g) {
            g.setFont(new Font("Arial", Font.BOLD, 12));
            g.setColor(new Color(22, 82, 55));
            g.drawString("Combo", 670, 560);
            for (int index = 0; index < 5; index++) {
                g.setColor(index < comboCount ? new Color(255, 195, 0) : new Color(170, 170, 170, 140));
                g.fillOval(720 + index * 14, 550, 10, 10);
            }
        }

        private void drawAspectCover(Graphics2D g, Image image, int width, int height) {
            double scale = Math.max(width / (double) gameBackground.getIconWidth(),
                    height / (double) gameBackground.getIconHeight());
            int drawWidth = (int) Math.ceil(gameBackground.getIconWidth() * scale);
            int drawHeight = (int) Math.ceil(gameBackground.getIconHeight() * scale);
            g.drawImage(image, (width - drawWidth) / 2, (height - drawHeight) / 2, drawWidth, drawHeight, null);
        }
    }

    private Color colorFor(int groupId) {
        return Color.getHSBColor((groupId * 0.137f) % 1f, .55f, .95f);
    }

    private ImageIcon asset(String path) {
        if (path == null || path.isBlank()) return null;
        String relative = path.replace('\\', '/');
        int marker = relative.indexOf("Client/assets/");
        if (marker >= 0) relative = relative.substring(marker + "Client/assets/".length());
        return AssetLoader.icon(relative);
    }

    @Override
    public void onConnectionError(Exception exception) {
        SwingUtilities.invokeLater(() -> statusLabel.setText("Mất kết nối: " + exception.getMessage()));
    }

    private record FruitDefinition(int fruitId, int groupId, String fruitCode, String fruitName, String assetPath) {
    }

    private record BasketDefinition(int basketId, String basketName, String assetPath) {
    }

    private record FruitInstance(long instanceId, Integer fruitId, boolean isBomb, long spawnOffsetMs,
                                 int xPosition, int fallDurationMs) {
    }
}

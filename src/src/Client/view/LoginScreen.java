package Client.view;

import Client.controller.LoginController;
import Client.view.admin.AdminDashboardScreen;
import Client.session.GameClientSession;
import Client.ui.AssetLoader;
import Client.ui.ScaledBackgroundPanel;

import javax.swing.*;
import java.awt.*;
import java.io.InputStream;

public class LoginScreen {
    private final LoginController loginController;

    public LoginScreen() {
        loginController = new LoginController();

        Font customFont = loadCustomFont();

        JFrame loginFrame = new JFrame("Đăng nhập");
        loginFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        loginFrame.setSize(810, 540);
        loginFrame.setLocationRelativeTo(null);

        // Background
        ImageIcon backgroundIcon = AssetLoader.icon("backgrounds/login-orchard-background.png");
        ScaledBackgroundPanel backgroundLabel = new ScaledBackgroundPanel(backgroundIcon);
        backgroundLabel.setLayout(new GridBagLayout());
        backgroundLabel.setPreferredSize(new Dimension(810, 540));

        // Semi-transparent form panel
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // --- Title row (logo + text) ---
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 3;
        JLabel titleLabel;
        ImageIcon logoIcon = AssetLoader.icon("logo/logo-fruit-basket.png");
        if (logoIcon != null) {
            Image scaled = logoIcon.getImage().getScaledInstance(38, 38, Image.SCALE_SMOOTH);
            titleLabel = new JLabel("Đăng nhập", new ImageIcon(scaled), JLabel.CENTER);
            titleLabel.setIconTextGap(10);
        } else {
            titleLabel = new JLabel("Đăng nhập", JLabel.CENTER);
        }
        titleLabel.setFont(customFont.deriveFont(Font.BOLD, 22f));
        titleLabel.setForeground(new Color(17, 83, 54));
        panel.add(titleLabel, gbc);

        // --- Username row: [icon] [label] [field] ---
        gbc.gridwidth = 1;
        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(iconLabel("icons/icon-user.png"), gbc);

        gbc.gridx = 1;
        JLabel usernameLabel = new JLabel("Tên đăng nhập:");
        usernameLabel.setFont(customFont.deriveFont(Font.PLAIN, 15f));
        usernameLabel.setForeground(new Color(17, 83, 54));
        panel.add(usernameLabel, gbc);

        gbc.gridx = 2;
        JTextField usernameField = new JTextField(15);
        usernameField.setFont(customFont.deriveFont(Font.PLAIN, 14f));
        usernameField.setBorder(BorderFactory.createLineBorder(Color.GRAY, 2));
        panel.add(usernameField, gbc);

        // --- Password row: [icon] [label] [passwordPanel + eye] ---
        gbc.gridx = 0; gbc.gridy = 2;
        panel.add(iconLabel("icons/icon-lock.png"), gbc);

        gbc.gridx = 1;
        JLabel passwordLabel = new JLabel("Mật khẩu:");
        passwordLabel.setFont(customFont.deriveFont(Font.PLAIN, 15f));
        passwordLabel.setForeground(new Color(17, 83, 54));
        panel.add(passwordLabel, gbc);

        gbc.gridx = 2;
        JPasswordField passwordField = new JPasswordField(15);
        passwordField.setFont(customFont.deriveFont(Font.PLAIN, 14f));
        passwordField.setBorder(BorderFactory.createLineBorder(Color.GRAY, 2));
        panel.add(buildPasswordWrapper(passwordField), gbc);

        // --- Buttons ---
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 1;
        gbc.insets = new Insets(18, 8, 8, 8);
        JButton btnBack = createButton("Trở về", customFont, new Color(204, 0, 0));
        panel.add(btnBack, gbc);

        gbc.gridx = 1; gbc.gridwidth = 2;
        JButton btnSubmit = createButton("Đăng nhập", customFont, new Color(0, 102, 204));
        panel.add(btnSubmit, gbc);

        backgroundLabel.add(panel);
        loginFrame.add(backgroundLabel);

        // --- Button actions ---
        btnBack.addActionListener(e -> {
            loginFrame.dispose();
            new MainScreen();
        });

        btnSubmit.addActionListener(e -> {
            String username = usernameField.getText();
            String password = new String(passwordField.getPassword());

            if (username.isEmpty() || password.isEmpty()) {
                JOptionPane.showMessageDialog(loginFrame,
                        "Vui lòng nhập tên đăng nhập và mật khẩu", "Lỗi", JOptionPane.WARNING_MESSAGE);
                return;
            }

            try {
                if (loginController.authenticate(username, password)) {
                    GameClientSession.Identity identity = GameClientSession.instance().identity();
                    if (identity.role().equals("ADMIN")) {
                        new AdminDashboardScreen(Long.toString(identity.playerId()));
                        loginFrame.dispose();
                    } else {
                        new LobbyScreen(Long.toString(identity.playerId()), identity.displayName());
                        loginFrame.dispose();
                    }
                } else {
                    JOptionPane.showMessageDialog(loginFrame,
                            "Đăng nhập thất bại: " + loginController.lastError(), "Lỗi đăng nhập", JOptionPane.ERROR_MESSAGE);
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(loginFrame,
                        "Đã xảy ra lỗi kết nối. Vui lòng thử lại sau.", "Lỗi", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        });

        loginFrame.setVisible(true);
    }

    // --- Helpers ---

    /** Loads an icon asset and returns a 22×22 JLabel. Returns an empty label if asset is missing. */
    private JLabel iconLabel(String assetPath) {
        JLabel lbl = new JLabel();
        ImageIcon icon = AssetLoader.icon(assetPath);
        if (icon != null) {
            Image scaled = icon.getImage().getScaledInstance(22, 22, Image.SCALE_SMOOTH);
            lbl.setIcon(new ImageIcon(scaled));
        }
        return lbl;
    }

    /**
     * Wraps a JPasswordField in a BorderLayout panel with an eye-toggle button on the right
     * (uses icon-eye / icon-eye-off assets). Falls back to plain field if icons are missing.
     */
    private JPanel buildPasswordWrapper(JPasswordField passwordField) {
        JPanel wrapper = new JPanel(new BorderLayout(2, 0));
        wrapper.setOpaque(false);
        wrapper.add(passwordField, BorderLayout.CENTER);

        ImageIcon eyeIcon    = AssetLoader.icon("icons/icon-eye.png");
        ImageIcon eyeOffIcon = AssetLoader.icon("icons/icon-eye-off.png");
        if (eyeIcon != null && eyeOffIcon != null) {
            ImageIcon eyeSmall    = scale(eyeIcon,    20, 20);
            ImageIcon eyeOffSmall = scale(eyeOffIcon, 20, 20);

            JButton eyeBtn = new JButton(eyeOffSmall);
            eyeBtn.setPreferredSize(new Dimension(28, 28));
            eyeBtn.setFocusPainted(false);
            eyeBtn.setBorderPainted(false);
            eyeBtn.setContentAreaFilled(false);
            eyeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            final boolean[] visible = {false};
            eyeBtn.addActionListener(e -> {
                visible[0] = !visible[0];
                passwordField.setEchoChar(visible[0] ? (char) 0 : '•');
                eyeBtn.setIcon(visible[0] ? eyeSmall : eyeOffSmall);
            });
            wrapper.add(eyeBtn, BorderLayout.EAST);
        }
        return wrapper;
    }

    private ImageIcon scale(ImageIcon icon, int w, int h) {
        return new ImageIcon(icon.getImage().getScaledInstance(w, h, Image.SCALE_SMOOTH));
    }

    private JButton createButton(String text, Font font, Color backgroundColor) {
        JButton button = new JButton(text);
        button.setFont(font.deriveFont(Font.BOLD, 16f));
        button.setForeground(Color.WHITE);
        button.setBackground(backgroundColor);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension(120, 40));
        return button;
    }

    /**
     * Loads FVF.ttf from classpath (works in both IDE and exported JAR).
     * Falls back to Serif if the font cannot be found.
     */
    private Font loadCustomFont() {
        String[] candidates = {"/Client/assets/FVF.ttf", "/client/assets/FVF.ttf", "../assets/FVF.ttf"};
        for (String path : candidates) {
            try (InputStream is = getClass().getResourceAsStream(path)) {
                if (is != null) {
                    Font font = Font.createFont(Font.TRUETYPE_FONT, is);
                    GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
                    return font;
                }
            } catch (Exception ignored) {
            }
        }
        return new Font("Serif", Font.PLAIN, 18);
    }
}

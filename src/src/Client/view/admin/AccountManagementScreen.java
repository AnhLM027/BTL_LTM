package Client.view.admin;

import Client.network.TcpQuery;
import Client.session.GameClientSession;
import Server.network.ProtocolMessage;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.Map;

/**
 * Admin-only TCP CRUD. Passwords are never fetched, displayed, or prefilled.
 */
public final class AccountManagementScreen extends JFrame {
    private final DefaultTableModel accounts = new DefaultTableModel(new String[]{"Account ID", "Username", "Role", "Status"}, 0) {
        public boolean isCellEditable(int r, int c) {
            return false;
        }
    };
    private final JTable table = new JTable(accounts);

    public AccountManagementScreen() {
        setTitle("Quản lý tài khoản");
        setSize(650, 420);
        setLocationRelativeTo(null);
        add(new JScrollPane(table), BorderLayout.CENTER);
        JPanel buttons = new JPanel();
        JButton add = new JButton("Thêm");
        JButton edit = new JButton("Sửa");
        JButton delete = new JButton("Xóa");
        JButton refresh = new JButton("Làm mới");
        JButton back = new JButton("Trở về");
        buttons.add(add);
        buttons.add(edit);
        buttons.add(delete);
        buttons.add(refresh);
        buttons.add(back);
        add(buttons, BorderLayout.SOUTH);
        add.addActionListener(e -> create());
        edit.addActionListener(e -> edit());
        delete.addActionListener(e -> delete());
        refresh.addActionListener(e -> load());
        back.addActionListener(e -> dispose());
        setVisible(true);
        load();
    }

    private void create() {
        AccountForm form = new AccountForm(this, null, null, "PLAYER", "ACTIVE", true);
        if (!form.showForm()) return;
        send(new ProtocolMessage("ADMIN_CREATE_ACCOUNT", Map.of("username", form.username(), "password", form.password(), "role", form.role())), "ADMIN_ACCOUNT_CREATED");
    }

    private void edit() {
        int r = table.getSelectedRow();
        if (r < 0) {
            notice("Chọn tài khoản");
            return;
        }
        String id = value(r, 0);
        AccountForm form = new AccountForm(this, id, value(r, 1), value(r, 2), value(r, 3), false);
        if (!form.showForm()) return;
        java.util.LinkedHashMap<String, String> fields = new java.util.LinkedHashMap<>();
        fields.put("accountId", id);
        fields.put("username", form.username());
        fields.put("role", form.role());
        fields.put("status", form.status());
        if (!form.password().isBlank()) fields.put("password", form.password());
        send(new ProtocolMessage("ADMIN_UPDATE_ACCOUNT", fields), "ADMIN_ACCOUNT_UPDATED");
    }

    private void delete() {
        int r = table.getSelectedRow();
        if (r < 0) {
            notice("Chọn tài khoản");
            return;
        }
        if (JOptionPane.showConfirmDialog(this, "Xóa tài khoản đã chọn?", "Xác nhận", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION)
            return;
        send(new ProtocolMessage("ADMIN_DELETE_ACCOUNT", Map.of("accountId", value(r, 0))), "ADMIN_ACCOUNT_DELETED");
    }

    private String value(int row, int col) {
        return table.getValueAt(row, col).toString();
    }

    private void send(ProtocolMessage request, String response) {
        try {
            TcpQuery.single(GameClientSession.instance().client(), request, response).thenRun(() -> SwingUtilities.invokeLater(this::load)).exceptionally(e -> {
                SwingUtilities.invokeLater(() -> notice(e.getMessage()));
                return null;
            });
        } catch (Exception e) {
            notice(e.getMessage());
        }
    }

    private void load() {
        try {
            TcpQuery.collect(GameClientSession.instance().client(), ProtocolMessage.of("ADMIN_LIST_ACCOUNTS"), "ADMIN_ACCOUNT", "ADMIN_ACCOUNTS_END").thenAccept(this::showAccounts);
        } catch (Exception e) {
            notice(e.getMessage());
        }
    }

    private void showAccounts(List<ProtocolMessage> list) {
        SwingUtilities.invokeLater(() -> {
            accounts.setRowCount(0);
            for (var a : list)
                accounts.addRow(new Object[]{a.fields().get("accountId"), a.fields().get("username"), a.fields().get("role"), a.fields().get("status")});
        });
    }

    private void notice(String text) {
        JOptionPane.showMessageDialog(this, text, "Admin", JOptionPane.INFORMATION_MESSAGE);
    }

    private static final class AccountForm {
        private final JTextField username = new JTextField();
        private final JPasswordField password = new JPasswordField();
        private final JComboBox<String> role = new JComboBox<>(new String[]{"PLAYER", "ADMIN"});
        private final JComboBox<String> status = new JComboBox<>(new String[]{"ACTIVE", "DISABLED", "LOCKED"});
        private final JPanel panel = new JPanel(new GridLayout(0, 2, 6, 6));
        private final boolean create;

        AccountForm(Component parent, String id, String name, String currentRole, String currentStatus, boolean create) {
            this.create = create;
            username.setText(name == null ? "" : name);
            role.setSelectedItem(currentRole);
            status.setSelectedItem(currentStatus);
            panel.add(new JLabel("Username"));
            panel.add(username);
            panel.add(new JLabel(create ? "Password" : "New password (optional)"));
            panel.add(password);
            panel.add(new JLabel("Role"));
            panel.add(role);
            if (!create) {
                panel.add(new JLabel("Status"));
                panel.add(status);
            }
        }

        boolean showForm() {
            return JOptionPane.showConfirmDialog(null, panel, create ? "Tạo tài khoản" : "Sửa tài khoản", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION && !username.getText().isBlank() && (!create || password.getPassword().length > 0);
        }

        String username() {
            return username.getText().trim();
        }

        String password() {
            return new String(password.getPassword());
        }

        String role() {
            return role.getSelectedItem().toString();
        }

        String status() {
            return create ? "ACTIVE" : status.getSelectedItem().toString();
        }
    }
}

package Client.view.admin;

import Client.network.TcpQuery;
import Client.session.GameClientSession;
import Server.network.ProtocolMessage;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * Admin view of the fruit/item catalog.  The catalog is loaded through the
 * authenticated TCP session; it is never hard-coded in the admin client.
 */
public final class ItemManagementScreen extends JFrame {
    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"ID", "Group ID", "Mã", "Hoa quả", "Nhóm", "Dinh dưỡng", "Mô tả", "Asset"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(model);

    public ItemManagementScreen() {
        setTitle("Quản lý vật phẩm");
        setSize(980, 520);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(8, 8));

        table.setAutoCreateRowSorter(true);
        table.setRowHeight(28);
        table.getColumnModel().getColumn(5).setPreferredWidth(360);
        table.getColumnModel().getColumn(6).setPreferredWidth(220);
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton add = new JButton("Thêm");
        JButton edit = new JButton("Sửa");
        JButton delete = new JButton("Xóa");
        JButton refresh = new JButton("Làm mới");
        JButton close = new JButton("Đóng");
        add.addActionListener(event -> create());
        edit.addActionListener(event -> edit());
        delete.addActionListener(event -> delete());
        refresh.addActionListener(event -> load());
        close.addActionListener(event -> dispose());
        actions.add(add); actions.add(edit); actions.add(delete);
        actions.add(refresh);
        actions.add(close);
        add(actions, BorderLayout.SOUTH);

        setVisible(true);
        load();
    }

    private void create() {
        FruitForm form = new FruitForm(this, null, "", "", "", "", "");
        if (form.showForm()) send(new ProtocolMessage("ADMIN_CREATE_FRUIT", form.fields()), "ADMIN_FRUIT_CREATED");
    }

    private void edit() {
        int row = table.getSelectedRow(); if (row < 0) { notice("Chọn vật phẩm cần sửa"); return; }
        int modelRow = table.convertRowIndexToModel(row);
        FruitForm form = new FruitForm(this, value(modelRow, 0), value(modelRow, 1), value(modelRow, 3), value(modelRow, 6), value(modelRow, 7), value(modelRow, 2));
        if (form.showForm()) send(new ProtocolMessage("ADMIN_UPDATE_FRUIT", form.fields()), "ADMIN_FRUIT_UPDATED");
    }

    private void delete() {
        int row = table.getSelectedRow(); if (row < 0) { notice("Chọn vật phẩm cần xóa"); return; }
        int modelRow = table.convertRowIndexToModel(row);
        if (JOptionPane.showConfirmDialog(this, "Ẩn vật phẩm đã chọn?", "Xác nhận", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION)
            send(new ProtocolMessage("ADMIN_DELETE_FRUIT", java.util.Map.of("fruitId", value(modelRow, 0))), "ADMIN_FRUIT_DELETED");
    }

    private String value(int row, int column) { return model.getValueAt(row, column).toString(); }

    private void send(ProtocolMessage request, String response) {
        try {
            TcpQuery.single(GameClientSession.instance().client(), request, response).thenRun(this::load)
                    .exceptionally(error -> { SwingUtilities.invokeLater(() -> notice(error.getMessage())); return null; });
        } catch (Exception error) { notice(error.getMessage()); }
    }

    private void load() {
        try {
            TcpQuery.collect(GameClientSession.instance().client(),
                    ProtocolMessage.of("GET_FRUIT_CATALOG"),
                    "FRUIT_CATALOG_ITEM", "FRUIT_CATALOG_END")
                    .thenAccept(this::showItems)
                    .exceptionally(error -> {
                        SwingUtilities.invokeLater(() -> notice(error.getMessage()));
                        return null;
                    });
        } catch (Exception error) {
            notice(error.getMessage());
        }
    }

    private void showItems(List<ProtocolMessage> items) {
        SwingUtilities.invokeLater(() -> {
            model.setRowCount(0);
            for (ProtocolMessage item : items) {
                model.addRow(new Object[]{
                        item.fields().getOrDefault("fruitId", ""),
                        item.fields().getOrDefault("groupId", ""),
                        item.fields().getOrDefault("fruitCode", ""),
                        item.fields().getOrDefault("fruitName", ""),
                        item.fields().getOrDefault("groupName", ""),
                        item.fields().getOrDefault("nutritionLabels", "").replace("|", ", "),
                        item.fields().getOrDefault("description", ""),
                        item.fields().getOrDefault("assetPath", "")
                });
            }
        });
    }

    private void notice(String message) {
        JOptionPane.showMessageDialog(this,
                message == null || message.isBlank() ? "Không thể tải danh mục vật phẩm" : message,
                "Quản lý vật phẩm", JOptionPane.INFORMATION_MESSAGE);
    }

    private static final class FruitForm {
        private final JTextField groupId = new JTextField();
        private final JTextField code = new JTextField();
        private final JTextField name = new JTextField();
        private final JTextField asset = new JTextField();
        private final JTextArea description = new JTextArea(4, 30);
        private final JPanel panel = new JPanel(new GridLayout(0, 2, 6, 6));
        private final String id;
        FruitForm(Component parent, String id, String groupId, String name, String description, String asset, String code) {
            this.id = id;
            this.groupId.setText(groupId); this.name.setText(name); this.description.setText(description); this.asset.setText(asset); this.code.setText(code);
            panel.add(new JLabel("Group ID")); panel.add(this.groupId);
            panel.add(new JLabel("Mã quả")); panel.add(this.code);
            panel.add(new JLabel("Tên quả")); panel.add(this.name);
            panel.add(new JLabel("Asset path")); panel.add(this.asset);
            panel.add(new JLabel("Mô tả")); panel.add(new JScrollPane(this.description));
        }
        boolean showForm() { return JOptionPane.showConfirmDialog(null, panel, id == null ? "Thêm vật phẩm" : "Sửa vật phẩm", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION; }
        java.util.Map<String,String> fields() {
            java.util.LinkedHashMap<String,String> fields = new java.util.LinkedHashMap<>();
            if (id != null) fields.put("fruitId", id);
            fields.put("groupId", groupId.getText().trim()); fields.put("fruitCode", code.getText().trim()); fields.put("fruitName", name.getText().trim());
            fields.put("description", description.getText().trim()); fields.put("assetPath", asset.getText().trim()); return fields;
        }
    }
}

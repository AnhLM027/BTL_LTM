FRUIT BATTLE — MAINCLIENTFRAME V4
Phạm vi: bộ thành phần PHÒNG CHỜ trong ảnh tham chiếu.
23 ảnh thành phần mỗi bộ: default_1080x760 và minimum_810x540.
Đã bổ sung logo và bộ chọn chế độ như mẫu. Khung/nút chưa có chữ động.
preview/lobby_1080x760.png và lobby_810x540.png được ghép từ chính ảnh xuất.
dimensions.csv ghi kích thước từng file và vùng hình thực tế.
layout.json ghi tọa độ mặc định/tối thiểu và quy tắc resize.

QUAN TRỌNG KHI DÙNG SWING
1080×760 / 810×540 là kích thước toàn bộ JFrame; phần vẽ thật thường nhỏ hơn
vì có thanh tiêu đề và viền. Không đoán số pixel phải trừ.
Lấy gamePanel.getWidth() và getHeight() sau khi layout để tính tỷ lệ.
Canvas thiết kế dùng 1080×760. Co đồng đều cả hai chiều:
s = min(panelWidth / 1080.0, panelHeight / 760.0)
offsetX = (panelWidth - 1080*s)/2
offsetY = (panelHeight - 760*s)/2
Vị trí = offset + vị trí thiết kế*s. Kích thước = kích thước gốc*s.
Nền dùng cover, giữ tỷ lệ và cắt phần dư để kín panel.

Với cửa sổ resize liên tục, nạp bộ default_1080x760 MỘT LẦN,
vẽ theo tỷ lệ trên; không đổi sang bộ minimum rồi áp tỷ lệ lần nữa.
Bộ minimum là ảnh xuất sẵn để tham khảo hoặc dùng tại viewport 810×540 cố định.
integration/ResponsiveAssets.java cung cấp biến đổi vị trí, vẽ nền,
tọa độ hit-test và cùng một phép scale cho hình và chữ.
Load BufferedImage bằng ImageIO trước khi vẽ, không đọc file trong paintComponent.
Các màn khác có thể dùng cùng quy tắc viewport; ZIP này chỉ có asset phòng chờ.

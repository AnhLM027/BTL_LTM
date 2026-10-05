Bạn là một AI Software Engineering Agent. Hãy thiết kế và triển khai hệ thống
“GAME HỨNG HOA QUẢ THI ĐẤU ĐỐI KHÁNG ONLINE”
dựa chính xác trên đặc tả dưới đây.

Không tự ý thay đổi luật chơi hoặc thêm chức năng nghiệp vụ ngoài đặc tả nếu chưa được yêu cầu.
Nếu có điểm chưa được đặc tả rõ, hãy thiết kế theo hướng đơn giản, nhất quán với kiến trúc Client–Server và ghi rõ giả định.

==================================================
1. MỤC TIÊU HỆ THỐNG
==================================================

Xây dựng một game hứng hoa quả thi đấu đối kháng trực tuyến 1 vs 1.

Hai người chơi tham gia cùng một trận đấu kéo dài 30 giây.

Trong trận:
- Hoa quả rơi từ phía trên màn hình xuống.
- Mỗi người chơi điều khiển giỏ trên Client của mình.
- Người chơi phải quyết định hứng hoặc né từng loại hoa quả dựa trên kiểu chơi của trận.
- Hứng đúng được cộng điểm.
- Hứng sai bị trừ điểm.
- Điểm của hai người chơi được đồng bộ qua Server.
- Khi hết 30 giây, Server chốt điểm và xác định người thắng.

Game phải hỗ trợ:
- đăng nhập;
- sảnh người chơi online;
- tạo phòng;
- lời mời thi đấu;
- phòng chờ;
- lựa chọn kiểu chơi;
- trận đấu 1v1;
- đồng bộ điểm;
- kết quả trận đấu;
- lịch sử thi đấu;
- bảng xếp hạng;
- hồ sơ người chơi.

==================================================
2. KIẾN TRÚC HỆ THỐNG
==================================================

Sử dụng mô hình Client–Server tập trung.

Luồng kiến trúc:

Client
   ↓ TCP hai chiều
TCP Server
   ↓
Business Service
   ↓
Data Access Layer / JDBC
   ↓
MySQL

Yêu cầu:

- Nhiều Client có thể kết nối đồng thời tới Server.
- Mỗi Client duy trì một kết nối TCP hai chiều trong phiên đăng nhập.
- Các Client KHÔNG giao tiếp trực tiếp với nhau.
- Mọi dữ liệu giữa hai người chơi phải đi qua Server.

Ví dụ:

Client A
   ↓
Server
   ↓
Client B

Client không được truy cập trực tiếp MySQL.

Server là thành phần chịu trách nhiệm duy trì trạng thái chung và kiểm tra các thao tác quan trọng.

==================================================
3. VAI TRÒ CLIENT
==================================================

Client chịu trách nhiệm:

- Hiển thị giao diện.
- Nhận thao tác bàn phím/người dùng.
- Hiển thị danh sách người chơi online.
- Hiển thị phòng chờ.
- Hiển thị hoa quả.
- Hiển thị giỏ.
- Điều khiển giỏ.
- Phát hiện va chạm giữa giỏ và hoa quả.
- Hiển thị điểm của bản thân.
- Hiển thị điểm đối thủ.
- Hiển thị thời gian còn lại.
- Hiển thị kết quả trận.
- Gửi các yêu cầu/sự kiện tới Server.
- Nhận các sự kiện cập nhật từ Server.

Client không được tự quyết định trạng thái chung của trận đấu.

==================================================
4. VAI TRÒ SERVER
==================================================

Server là nguồn trạng thái chính của hệ thống.

Server quản lý:

- kết nối TCP;
- phiên đăng nhập;
- trạng thái online của người chơi;
- phòng;
- lời mời;
- thành viên phòng;
- kiểu chơi;
- trạng thái trận đấu;
- thời điểm bắt đầu trận;
- thời hạn 30 giây;
- điểm số;
- kết quả;
- lưu dữ liệu xuống MySQL.

Server phải kiểm tra tính hợp lệ trước khi chấp nhận các thao tác ảnh hưởng tới trạng thái chung.

==================================================
5. NGƯỜI DÙNG
==================================================

Có hai loại tài khoản:

1. PLAYER
2. ADMIN

PLAYER sử dụng hệ thống để chơi game.

ADMIN quản lý dữ liệu như:
- tài khoản;
- danh mục hoa quả;
- nhóm hoa quả;
- đặc tính dinh dưỡng;
- giỏ/vật phẩm liên quan.

Việc kiểm tra quyền phải thực hiện tại Server.

Không được chỉ dựa vào việc ẩn nút chức năng ở Client để phân quyền.

==================================================
6. TRẠNG THÁI NGƯỜI CHƠI
==================================================

Người chơi có các trạng thái cơ bản:

ONLINE / READY
- Đang ở sảnh.
- Có thể nhận lời mời.

IN_ROOM
- Đang ở phòng chờ.

PLAYING
- Đang tham gia trận đấu.

OFFLINE
- Không còn kết nối với hệ thống.

Một người chơi chỉ được thuộc tối đa một phòng đang hoạt động tại một thời điểm.

Người đã ở phòng hoặc đang thi đấu không được nhận một lời mời thi đấu mới hợp lệ.

==================================================
7. SẢNH CHÍNH
==================================================

Sau khi đăng nhập thành công, PLAYER được chuyển tới sảnh chính.

Sảnh hiển thị danh sách những người chơi đang trực tuyến.

Mỗi người chơi hiển thị ít nhất:

- tên người chơi;
- tổng điểm;
- trạng thái hiện tại.

Ví dụ:

Tên              Tổng điểm       Trạng thái
Player A             1250        Sẵn sàng
Player B              960        Trong phòng
Player C             1430        Đang thi đấu

Chỉ người chơi có trạng thái phù hợp mới có thể được mời thi đấu.

==================================================
8. TẠO PHÒNG
==================================================

Một người chơi ở trạng thái phù hợp có thể tạo phòng.

Khi tạo phòng thành công:

- sinh roomId;
- người tạo trở thành HOST;
- trạng thái người chơi chuyển thành IN_ROOM;
- phòng chuyển vào trạng thái ROOM_WAITING.

Thông tin cơ bản của phòng:

Room {
    roomId
    hostPlayer
    guestPlayer
    gameMode
    roomState
    hostReady
    guestReady
}

guestPlayer có thể null khi chưa có người thứ hai.

==================================================
9. QUYỀN CỦA HOST
==================================================

HOST là người tạo phòng.

HOST có quyền:

- lựa chọn kiểu chơi;
- thay đổi kiểu chơi khi trận chưa bắt đầu;
- chọn một người chơi online để gửi lời mời;
- bắt đầu trận khi điều kiện hợp lệ.

Người chơi được mời không được tự thay đổi kiểu chơi.

Sau khi trận đã bắt đầu, gameMode phải bị khóa.

==================================================
10. LỜI MỜI THI ĐẤU
==================================================

Lời mời là một đối tượng độc lập với phòng.

Vòng đời lời mời:

PENDING
   ├── ACCEPTED
   ├── REJECTED
   ├── EXPIRED
   └── CANCELLED

Khi HOST gửi lời mời:

Server phải kiểm tra người được mời:

- đang online;
- đang ở trạng thái có thể nhận lời mời;
- chưa thuộc phòng khác;
- chưa tham gia trận khác.

Thông báo lời mời phải chứa tối thiểu:

- tên người mời;
- kiểu chơi hiện tại của phòng;
- nút Chấp nhận;
- nút Từ chối.

Ví dụ:

LỜI MỜI THI ĐẤU

Player A mời bạn tham gia trận đấu.

Kiểu chơi:
Phân loại theo đặc tính dinh dưỡng.

[CHẤP NHẬN]     [TỪ CHỐI]

==================================================
11. CHẤP NHẬN LỜI MỜI
==================================================

Khi người chơi nhấn ACCEPT:

Server phải kiểm tra lại:

- lời mời vẫn đang PENDING;
- người mời vẫn hợp lệ;
- người nhận vẫn hợp lệ;
- phòng vẫn còn tồn tại;
- phòng chưa có người chơi thứ hai;
- phòng chưa bắt đầu.

Nếu hợp lệ:

Invite:
PENDING → ACCEPTED

Sau đó:

- thêm người được mời vào phòng hiện có;
- KHÔNG tạo phòng mới;
- cập nhật guestPlayer;
- cập nhật trạng thái phòng;
- cập nhật trạng thái hai người chơi;
- gửi ROOM_UPDATED cho cả hai Client.

==================================================
12. TỪ CHỐI / HẾT HẠN / HỦY LỜI MỜI
==================================================

Nếu lời mời chuyển sang:

REJECTED
EXPIRED
CANCELLED

thì:

- người được mời không vào phòng;
- người được mời tiếp tục ở sảnh nếu trạng thái phù hợp;
- phòng của HOST vẫn tồn tại;
- HOST có thể tiếp tục mời người khác.

Không được hủy phòng chỉ vì một lời mời bị từ chối.

==================================================
13. VÒNG ĐỜI PHÒNG/TRẬN
==================================================

Vòng đời chính:

ROOM_WAITING
     ↓
INVITE_PENDING
     ↓ ACCEPTED
ROOM_FULL
     ↓
PREPARING
     ↓
READY
     ↓
PLAYING
     ↓ hết 30 giây
FINALIZING
     ↓ lưu kết quả thành công
FINISHED

Nhánh hủy:

ROOM_WAITING
     ↓
ROOM_CANCELLED

Phòng và lời mời là hai state machine khác nhau.

Không được gộp InviteState và RoomState thành một trạng thái duy nhất.

==================================================
14. PHÒNG CHỜ
==================================================

Khi hai người đã ở cùng phòng, Client phải hiển thị:

- roomId;
- HOST;
- người chơi thứ hai;
- kiểu chơi hiện tại;
- trạng thái phòng;
- trạng thái sẵn sàng.

Khi HOST thay đổi gameMode:

HOST Client
    ↓ CHANGE_GAME_MODE
Server
    ↓ cập nhật Room
    ↓
ROOM_UPDATED
   ↙     ↘
Host   Guest

Hai Client phải luôn hiển thị cùng một gameMode.

==================================================
15. CÁC KIỂU CHƠI
==================================================

Game có chính xác hai kiểu chơi.

-----------------------------------
MODE 1: FRUIT GROUP
-----------------------------------

Tên:
Phân loại theo nhóm hoa quả.

Mỗi loại hoa quả thuộc một nhóm.

Ví dụ:

Cam → quả có múi
Nho → quả mọng
Đào → quả hạch

Người chơi phải sử dụng loại giỏ phù hợp với nhóm của quả.

Điều kiện hứng đúng:

fruit.groupId == basket.groupId

Ví dụ:

Basket = CITRUS

Cam   → đúng
Chanh → đúng
Nho   → sai
Đào   → sai

-----------------------------------
MODE 2: NUTRITION
-----------------------------------

Tên:
Phân loại theo đặc tính dinh dưỡng.

Mỗi trận có một yêu cầu dinh dưỡng.

Ví dụ:

- Hứng hoa quả giàu Vitamin C.
- Hứng hoa quả giàu Kali.
- Hứng hoa quả giàu chất xơ.

Mỗi loại hoa quả có một tập các đặc tính dinh dưỡng.

Điều kiện hứng đúng:

requiredNutrition ∈ fruit.nutritionLabels

Một loại quả có thể có nhiều nutritionLabel.

Tuy nhiên một lần hứng chỉ được tính điểm một lần.

==================================================
16. GAMEPLAY
==================================================

Mỗi trận đấu kéo dài:

30 giây.

Hoa quả xuất hiện và rơi từ trên xuống.

Người chơi điều khiển giỏ để:

- di chuyển;
- hứng quả phù hợp;
- né quả không phù hợp.

Gameplay chạy riêng trên từng Client nhưng hai người chơi phải nhận được điều kiện trận đấu tương đương.

Server phải cung cấp:

- cùng gameMode;
- cùng luật điểm;
- cùng thời gian;
- cùng cấu hình/lịch sinh hoa quả;
- cùng yêu cầu dinh dưỡng nếu chơi Nutrition Mode.

==================================================
17. SINH HOA QUẢ
==================================================

Hai Client trong cùng một trận phải nhận cùng cấu hình hoặc cùng lịch sinh hoa quả.

Mỗi Fruit Instance phải có một ID duy nhất.

Ví dụ:

FruitSpawn {
    fruitInstanceId
    fruitTypeId
    spawnTime
    xPosition
}

fruitInstanceId được Server sử dụng để tránh một sự kiện hứng bị tính nhiều lần.

==================================================
18. VA CHẠM
==================================================

Client chịu trách nhiệm phát hiện va chạm giữa:

Basket
và
Fruit

Khi Client phát hiện một lần hứng:

Client gửi CatchEvent tới Server.

Ví dụ:

CatchEvent {
    matchId
    playerId
    fruitInstanceId
}

Client KHÔNG được tự ý coi điểm cục bộ là kết quả cuối cùng.

==================================================
19. SERVER KIỂM TRA CATCH EVENT
==================================================

Khi nhận CatchEvent, Server phải kiểm tra:

1. Match có tồn tại không?
2. Match có đang ở trạng thái PLAYING không?
3. Player có thuộc Match không?
4. 30 giây đã hết chưa?
5. fruitInstanceId có hợp lệ không?
6. Fruit đó đã được player xử lý trước đó chưa?
7. Mode hiện tại là gì?
8. Fruit có đáp ứng điều kiện của mode không?

Chỉ sau khi các kiểm tra hợp lệ mới cập nhật điểm.

==================================================
20. LUẬT ĐIỂM
==================================================

Công thức:

matchScore =
    10 × correctCatchCount
    - 5 × wrongCatchCount

Trong đó:

Hứng đúng:
+10 điểm

Hứng sai:
-5 điểm

Không hứng được quả:
0 điểm

Chủ động né quả không phù hợp:
0 điểm

Điểm trận có thể nhỏ hơn 0.

==================================================
21. ĐỒNG BỘ ĐIỂM
==================================================

Sau khi Server xử lý một CatchEvent:

Server cập nhật:

- correctCatchCount;
- wrongCatchCount;
- score.

Sau đó gửi trạng thái điểm mới tới cả hai Client.

Ví dụ:

Player A
   ↓ CATCH_EVENT
Server
   ↓ validate
   ↓ calculateScore
   ↓
SCORE_UPDATE
   ↙       ↘
Client A   Client B

Trong trận, mỗi Client phải thấy:

My Score
Opponent Score

==================================================
22. QUẢN LÝ THỜI GIAN
==================================================

Server là thành phần quản lý thời gian chính thức của trận.

Server lưu:

matchStartTime
matchEndTime

Trong đó:

matchEndTime =
matchStartTime + 30 seconds

Client chỉ sử dụng dữ liệu Server để hiển thị countdown.

Không được để kết quả phụ thuộc hoàn toàn vào timer cục bộ của Client.

==================================================
23. BẮT ĐẦU TRẬN
==================================================

HOST gửi:

START_MATCH

Server kiểm tra:

- phòng tồn tại;
- có đủ hai người;
- phòng chưa bắt đầu;
- trạng thái hai Client phù hợp.

Sau đó:

ROOM_FULL
   ↓
PREPARING

Server gửi cấu hình trận tới hai Client.

Hai Client hoàn tất chuẩn bị và gửi:

MATCH_READY

Khi Server xác nhận cả hai đã ready:

Server gửi:

MATCH_START

Trạng thái:

READY → PLAYING

Timer 30 giây bắt đầu từ mốc Server quy định.

==================================================
24. KẾT THÚC TRẬN
==================================================

Khi hết 30 giây:

Server chuyển:

PLAYING → FINALIZING

Từ thời điểm đó:

- không nhận thêm CatchEvent để tính điểm;
- chốt điểm cuối cùng;
- xác định kết quả.

Quy tắc:

scoreA > scoreB
→ Player A WIN

scoreA < scoreB
→ Player B WIN

scoreA == scoreB
→ DRAW

==================================================
25. LƯU KẾT QUẢ
==================================================

Sau trận, Server lưu ít nhất:

Match {
    matchId
    gameMode
    startTime
    endTime
    duration
}

MatchPlayer {
    matchId
    playerId
    correctCatchCount
    wrongCatchCount
    finalScore
    result
}

result:

WIN
LOSE
DRAW

Chỉ chuyển trận sang FINISHED sau khi quá trình lưu kết quả cần thiết hoàn tất thành công.

==================================================
26. HỒ SƠ NGƯỜI CHƠI
==================================================

Người chơi có thể xem hồ sơ cá nhân.

Thông tin có thể hiển thị:

- username;
- tên;
- trạng thái;
- tổng điểm;
- tổng số trận;
- tổng số trận thắng;
- tỷ lệ thắng;
- thành tích.

==================================================
27. LỊCH SỬ THI ĐẤU
==================================================

Người chơi có thể xem lịch sử trận đấu.

Mỗi dòng hiển thị ít nhất:

- mã trận;
- thời gian;
- đối thủ;
- kiểu chơi;
- điểm;
- kết quả.

Có thể xem chi tiết:

- số lần hứng đúng;
- số lần hứng sai;
- tổng điểm;
- WIN / LOSE / DRAW.

==================================================
28. BẢNG XẾP HẠNG
==================================================

Bảng xếp hạng sử dụng dữ liệu tích lũy của người chơi.

Ưu tiên sắp xếp:

1. Tổng điểm.
2. Tổng số trận thắng.
3. Tổng số trận đã tham gia.

==================================================
29. YÊU CẦU ĐỒNG BỘ VÀ NHẤT QUÁN
==================================================

Server là authoritative source cho:

- PlayerState;
- RoomState;
- InviteState;
- gameMode;
- matchState;
- startTime;
- score;
- result.

Client chỉ giữ bản sao để hiển thị.

Mọi thay đổi trạng thái chung phải đi qua Server.

Ví dụ không hợp lệ:

Client A tự đổi gameMode nhưng không báo Server.

Ví dụ hợp lệ:

Client A
   ↓ CHANGE_GAME_MODE
Server
   ↓ validate HOST
   ↓ update
   ↓
ROOM_UPDATED → A + B

==================================================
30. XỬ LÝ SỰ KIỆN LẶP
==================================================

Server phải tránh xử lý cùng một sự kiện CatchEvent nhiều lần.

Mỗi Fruit Instance có ID riêng.

Nếu:

(playerId, fruitInstanceId)

đã được xử lý thì CatchEvent tiếp theo cho cùng cặp đó không được tính điểm lần nữa.

==================================================
31. MẤT KẾT NỐI
==================================================

Trạng thái kết nối TCP và trạng thái tham gia phòng/trận là hai khái niệm khác nhau.

Ví dụ:

Player có thể mất TCP connection tạm thời trong khi Match vẫn đang PLAYING.

Không tự động đồng nhất:

socket closed
=
room deleted

nếu thiết kế hệ thống hỗ trợ khả năng reconnect.

Thiết kế logic kết nối phải tách biệt với RoomState và MatchState.

==================================================
32. CÁC SERVICE NÊN TÁCH
==================================================

Tổ chức nghiệp vụ theo hướng tách trách nhiệm.

Ví dụ:

AuthService
- login
- logout
- authentication

PlayerService
- profile
- online status
- statistics

RoomService
- createRoom
- updateRoom
- changeGameMode
- joinRoom
- leaveRoom

InviteService
- createInvite
- acceptInvite
- rejectInvite
- expireInvite
- cancelInvite

MatchService
- prepareMatch
- startMatch
- processCatch
- updateScore
- finishMatch
- determineResult

RankingService
- leaderboard

HistoryService
- matchHistory

Data Access Layer
- JDBC
- MySQL CRUD
- transaction

TCP Server
- connection
- session
- protocol routing
- send response
- push event

Đây là gợi ý tổ chức code; có thể điều chỉnh class cụ thể miễn không thay đổi nghiệp vụ.

==================================================
33. CÁC STATE ENUM QUAN TRỌNG
==================================================

Nên định nghĩa rõ các enum.

PlayerState:

ONLINE
IN_ROOM
PLAYING
OFFLINE

InviteState:

PENDING
ACCEPTED
REJECTED
EXPIRED
CANCELLED

RoomState:

WAITING
FULL
PREPARING
READY
PLAYING
FINALIZING
FINISHED
CANCELLED

MatchResult:

WIN
LOSE
DRAW

GameMode:

FRUIT_GROUP
NUTRITION

==================================================
34. CÁC MESSAGE TCP CẦN THIẾT
==================================================

Thiết kế protocol có thể sử dụng các message tương đương:

LOGIN
LOGIN_SUCCESS
LOGIN_FAILED

GET_ONLINE_PLAYERS
ONLINE_PLAYERS

CREATE_ROOM
ROOM_CREATED

CHANGE_GAME_MODE
ROOM_UPDATED

INVITE_PLAYER
INVITE_NOTIFICATION

INVITE_ACCEPT
INVITE_REJECT
INVITE_RESULT

START_MATCH
MATCH_PREPARE
MATCH_READY
MATCH_START

CATCH_EVENT
CATCH_ACK

SCORE_UPDATE
SCORE_SNAPSHOT

MATCH_END
MATCH_RESULT

GET_PROFILE
PROFILE_DATA

GET_MATCH_HISTORY
MATCH_HISTORY

GET_LEADERBOARD
LEADERBOARD_DATA

LOGOUT

Tên message có thể thay đổi nhưng phải giữ đúng ý nghĩa và flow nghiệp vụ.

==================================================
35. FLOW HOÀN CHỈNH CỦA MỘT TRẬN
==================================================

PLAYER A LOGIN
      ↓
PLAYER B LOGIN
      ↓
Hai người xuất hiện tại Lobby
      ↓
A CREATE_ROOM
      ↓
A trở thành HOST
      ↓
A chọn GAME_MODE
      ↓
A chọn B
      ↓
A INVITE B
      ↓
B nhận INVITE_NOTIFICATION
      ↓
B ACCEPT
      ↓
Server kiểm tra hợp lệ
      ↓
B được thêm vào phòng của A
      ↓
ROOM_FULL
      ↓
Server đồng bộ Room cho A và B
      ↓
A START_MATCH
      ↓
MATCH_PREPARE
      ↓
A READY
B READY
      ↓
MATCH_START
      ↓
PLAYING
      ↓
Fruit Spawn
      ↓
Client detect collision
      ↓
CATCH_EVENT
      ↓
Server validate
      ↓
Server calculate score
      ↓
SCORE_UPDATE
      ↓
A và B tiếp tục chơi
      ↓
30 seconds
      ↓
FINALIZING
      ↓
Server chốt điểm
      ↓
WIN / LOSE / DRAW
      ↓
Lưu MySQL
      ↓
MATCH_RESULT
      ↓
FINISHED

==================================================
36. CÁC QUY TẮC KHÔNG ĐƯỢC VI PHẠM
==================================================

1. Game là 1 vs 1.

2. Một trận kéo dài 30 giây.

3. Có đúng hai kiểu chơi:
   - FRUIT_GROUP
   - NUTRITION.

4. Hứng đúng +10.

5. Hứng sai -5.

6. Bỏ qua hoặc né đúng không bị trừ điểm.

7. Server quyết định trạng thái chính thức.

8. Client không giao tiếp trực tiếp với Client khác.

9. Client không kết nối trực tiếp MySQL.

10. Phòng được tạo trước khi gửi lời mời.

11. ACCEPT không tạo phòng mới.
    Người được mời phải được thêm vào phòng hiện có.

12. REJECT / EXPIRED / CANCELLED không làm mất phòng của HOST.

13. Chỉ HOST được đổi kiểu chơi.

14. Không được đổi kiểu chơi sau khi trận bắt đầu.

15. Hai người chơi phải nhận cùng cấu hình trận.

16. Server quản lý thời gian chính thức.

17. Server phải kiểm tra CatchEvent trước khi tính điểm.

18. Một Fruit Event không được tính nhiều lần.

19. Hết 30 giây không tiếp nhận thêm điểm.

20. Kết quả trận phải được lưu để phục vụ lịch sử và bảng xếp hạng.

==================================================
37. TIÊU CHÍ HOÀN THÀNH
==================================================

Hệ thống được coi là đáp ứng đặc tả khi có thể thực hiện thành công kịch bản:

1. Hai Player đăng nhập.
2. Hai Player nhìn thấy nhau trong danh sách online.
3. Player A tạo phòng.
4. A chọn Game Mode.
5. A gửi lời mời cho B.
6. B nhận đúng tên người mời và Game Mode.
7. B Accept.
8. B được thêm vào phòng của A.
9. Hai Client nhìn thấy cùng thông tin phòng.
10. A có thể đổi Game Mode trước trận.
11. B nhận được thay đổi ngay qua Server.
12. A bắt đầu trận.
13. Hai Client nhận cùng cấu hình trận.
14. Hai bên chơi trong 30 giây.
15. CatchEvent được Server kiểm tra.
16. Điểm được cập nhật chính xác.
17. Điểm đối thủ được đồng bộ.
18. Sau 30 giây Server dừng nhận điểm.
19. Server xác định WIN / LOSE / DRAW.
20. Kết quả được lưu vào database.
21. Hai Client nhận kết quả.
22. Người chơi có thể xem lại trận trong lịch sử.
23. Bảng xếp hạng phản ánh dữ liệu đã lưu.

==================================================
38. YÊU CẦU KHI AI AGENT TRIỂN KHAI
==================================================

Khi triển khai, hãy thực hiện theo thứ tự:

1. Phân tích domain model.
2. Thiết kế database.
3. Thiết kế TCP protocol.
4. Thiết kế state machine:
   - Player
   - Invite
   - Room
   - Match.
5. Thiết kế Server architecture.
6. Thiết kế Client architecture.
7. Implement authentication/session.
8. Implement lobby.
9. Implement room.
10. Implement invitation.
11. Implement game mode synchronization.
12. Implement match preparation.
13. Implement gameplay.
14. Implement scoring.
15. Implement synchronization.
16. Implement match ending.
17. Implement persistence.
18. Implement history.
19. Implement leaderboard.
20. Kiểm thử toàn bộ flow 1v1.

Trước khi viết code, hãy trình bày:

- project structure;
- class diagram hoặc danh sách class chính;
- database schema;
- TCP message protocol;
- state machine;
- luồng sequence của một trận.

Sau đó mới tiến hành triển khai từng module.

Không viết toàn bộ hệ thống thành một class duy nhất.
Không đặt toàn bộ logic nghiệp vụ trong UI.
Không để Client quyết định kết quả trận.
Không để Client truy cập trực tiếp database.
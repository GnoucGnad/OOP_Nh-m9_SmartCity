# BÁO CÁO DỰ ÁN OOP - MÔ PHỎNG GIAO THÔNG THÀNH PHỐ THÔNG MINH (SMARTCITYTRAFFICSIM)

Dự án này là sản phẩm Bài tập lớn môn **Lập trình hướng đối tượng (OOP)** tại **Đại học Bách khoa Hà Nội (HUST)**. Dưới đây là mô tả chi tiết về cách mã nguồn giải quyết các yêu cầu kỹ thuật của đề bài, cấu trúc thư mục dự án và hướng dẫn vận hành chi tiết.

---

## 📑 1. Chi Tiết Thực Thi Code Theo Tiêu Chí Đề Bài

Mã nguồn được viết theo tư duy lập trình hướng đối tượng thuần túy, phân rã bài toán giao thông thành các thực thể, hành vi lái xe, và hạ tầng đường bộ tương tác với nhau:

### 1.1. Mô phỏng Đa dạng Phương tiện (5+ loại xe)
*Nằm trong gói `vn.edu.hust.traffic.model.vehicle` và `vn.edu.hust.traffic.behavior`:*
- **`Vehicle.java` (Lớp cha trừu tượng)**: Định nghĩa các thuộc tính vật lý cốt lõi của mọi phương tiện: tọa độ hiện tại `(x, y)`, tọa độ trước đó `(lastX, lastY)` để tính hướng quay đầu xe, vận tốc `speed`, hướng di chuyển `direction` (radian), kích thước vật lý `width` và `height`, trạng thái `passedStopLine` (đã qua vạch dừng), và biến cờ ưu tiên `isPriorityVehicle`.
- **`Car.java`**, **`Motorbike.java`**, **`Bus.java` (Lớp con kế thừa)**: Đại diện cho các loại xe cơ bản. Lớp con ghi đè các tham số vật lý mặc định (ví dụ: Bus có chiều rộng xe lớn hơn để thể hiện độ cồng kềnh, Motorbike có chiều rộng nhỏ hơn để dễ luồn lách).
- **`Ambulance.java`**, **`FireTruck.java` (Xe ưu tiên)**: Được gán cờ `isPriorityVehicle = true`. Khi di chuyển, chúng phát tín hiệu khẩn cấp, kích hoạt còi hú báo động đặc trưng (`SoundPlayer.playAmbulance`) và bật chế độ ép đèn xanh ở các ngã tư tiếp cận.

### 1.2. Trí tuệ lái xe (AI Driving Strategy) & Luật giao thông
*Nằm trong gói `vn.edu.hust.traffic.behavior` (Sử dụng **Strategy Pattern**):*
- **`DrivingStrategy.java` (Giao diện chiến lược)**: Định nghĩa hợp đồng chung cho bộ não điều khiển xe.
- **`NormalDriver.java` (Chiến lược lái xe an toàn)**: Thực thi logic lái xe tuân thủ luật giao thông:
  - **Bám đuôi an toàn (Car-Following)**: Tính toán khoảng cách tới xe phía trước cùng làn. Nếu khoảng cách nhỏ hơn khoảng cách an toàn, xe tự động giảm tốc độ về bằng tốc độ xe trước hoặc dừng hẳn để tránh va chạm.
  - **Chuyển làn vượt xe (`OvertakeManager.java`)**: Khi xe phía trước di chuyển quá chậm dưới 65% tốc độ tối đa, xe thường sẽ quét làn bên cạnh. Nếu làn bên cạnh trống và đủ khoảng cách an toàn, xe tự động đổi làn (`overtakingSlowVehicle = true`) để vượt lên.
  - **Dạt làn nhường xe ưu tiên (Flee Mode)**: Xe thường liên tục quét xem có xe ưu tiên nào đang hú còi từ phía sau không. Nếu có, xe thường chủ động đổi sang làn khác hoặc bò sát lề để nhường đường hoàn toàn cho xe cứu thương/cứu hỏa phóng qua.
  - **Nhường đường ngã tư (`IntersectionNavigator.java`)**: Khi nhiều xe cùng tiếp cận ngã tư, hệ thống sử dụng biến đếm thời gian vào ngã tư (`activeIntersectionEntryOrder`) để nhường đường cho xe đến trước qua trước, hoặc nhường đường cho xe ưu tiên.
  - **Quỹ đạo bo cua ngã rẽ (`TrajectoryController.java`)**: Khi rẽ trái, xe đi sâu vào tâm ngã tư rồi thực hiện ôm cua 90 độ mượt mà. Khi rẽ phải, xe tự động dạt sang làn rẽ tắt chéo góc (`isTurningDiagonally`), bỏ qua tín hiệu đèn giao thông theo đúng luật đường bộ.
- **`ViolatorDriver.java` (Chiến lược xe vi phạm luật)**: Kế thừa `NormalDriver` nhưng ghi đè các phương thức kiểm tra đèn tín hiệu. Xe vi phạm sẽ phớt lờ hoàn toàn đèn đỏ hoặc đèn vàng, chạy thẳng qua giao lộ bất kể trạng thái giao thông.

### 1.3. Mô phỏng Giao lộ & Hạ tầng đường bộ
*Nằm trong gói `vn.edu.hust.traffic.model.map`:*
- **`Intersection.java` (Lớp cha giao lộ)**: Quản lý tọa độ tâm giao lộ `(x, y)`, mã nhận diện `id` và danh sách cột đèn tín hiệu `lights`.
- **`CrossIntersection.java` (Ngã tư chữ thập)**: Quản lý 4 cụm đèn cho 4 hướng đi. Cung cấp API `getLightStateForDirection(direction)` để xe truy vấn màu đèn tương ứng với hướng xe đang chạy tới mà không cần biết chi tiết mảng chỉ mục đèn.
- **`ThreeWayIntersection.java` (Ngã ba chữ T)**: Quản lý 3 cụm đèn cho 3 hướng đi.
- **`RoundaboutIntersection.java` (Vòng xuyến bùng binh)**: Không sử dụng đèn tín hiệu. Thực thi luật bùng binh: xe chuẩn bị vào vòng xuyến (`exitedRoundabout = false`) phải nhường đường cho xe đã ở bên trong bùng binh (`insideRoundabout = true`), di chuyển bám theo bán kính vòng tròn đảo cỏ xanh ở tâm.

### 1.4. Hệ thống pha đèn tín hiệu tự thích ứng (Controllers)
*Nằm trong gói `vn.edu.hust.traffic.controller`:*
- **`TrafficController.java`**: Bộ điều phối trung tâm của toàn bộ chương trình, chứa vòng lặp cập nhật vật lý, quản lý danh sách xe, các giao lộ mặc định và các giao lộ/đoạn đường tự vẽ.
- **`IntersectionPhaseController.java` (12 pha đèn ngã tư)**: Quản lý chu kỳ pha đèn lệch giờ (Xanh sớm cho rẽ trái / Đỏ muộn cho đi thẳng). Có khả năng **ép đèn khẩn cấp (Preemption)**: nếu phát hiện xe cứu thương đang tiếp cận, lập tức khóa đèn các hướng khác thành Đỏ và mở Xanh hoàn toàn cho hướng xe cứu thương đi qua. Sau khi xe đi qua, pha đèn cũ sẽ được khôi phục.
- **`ThreeWayPhaseController.java` (6 pha đèn ngã ba)**: Quản lý chu kỳ chuyển pha cho ngã ba.

### 1.5. Đồ họa vẽ màn hình & Bảng điều khiển (View)
*Nằm trong gói `vn.edu.hust.traffic.view` và `vn.edu.hust.traffic.view.renderer`:*
- **`SimulationWindow.java`**: Sử dụng `AnimationTimer` để duy trì vòng lặp vẽ màn hình ổn định 60fps. Nhận tương tác chuột để đổi màu đèn thủ công hoặc căn chỉnh (Snap) đặt ngã tư khi vẽ bản đồ.
- **`RoadRenderer.java`**: Vẽ nền cỏ thế giới, mặt đường nhựa xám đậm, kẻ vạch phân làn đứt trắng, vạch đôi liền màu vàng ở tim đường, vạch dừng xe và vạch kẻ sọc trắng dành cho người đi bộ qua đường (Zebra Crossing).
- **`VehicleRenderer.java`**: 
  - Chế độ **Basic**: Vẽ hình chữ nhật phẳng có ghi nhãn loại xe.
  - Chế độ **Graphic**: Vẽ xe dạng sprite xoay đầu theo hướng di chuyển thực tế, vẽ hiệu ứng nhấp nháy đèn khẩn cấp của xe ưu tiên.
- **`TrafficLightRenderer.java`**: Vẽ cụm đèn tín hiệu đen bo góc tròn, các thấu kính đèn (Đỏ, Vàng, Xanh) phát sáng dạ quang và bảng đếm ngược số giây đếm ngược màu trắng rõ nét.

---

## 📂 2. Cấu Trúc Cây Thư Mục Dự Án sau khi Refactor

```
SmartCityTrafficSim/
├── pom.xml                         # Cấu hình dependency Maven (JavaFX, JUnit 5)
├── README.md                       # Tài liệu hướng dẫn sử dụng và giới thiệu chung
└── src/
    ├── main/
    │   ├── java/
    │   │   └── vn/edu/hust/traffic/
    │   │       ├── Main.java       # Điểm khởi chạy 
    │   │       │
    │   │       ├── base/           # Interface dùng chung 
    │   │       │   ├── Renderable.java   # Cho đối tượng vẽ lên canvas
    │   │       │   └── Updatable.java    # Cho đối tượng cập nhật logic theo dt
    │   │       │
    │   │       ├── config/         # Cấu hình hệ thống 
    │   │       │   └── AppConfig.java    # Lưu trữ hằng số làn đường, vận tốc
    │   │       │
    │   │       ├── controller/     # Điều phối và vòng lặp logic 
    │   │       │   ├── TrafficController.java         # Quản lý xe, đèn, custom map
    │   │       │   ├── IntersectionPhaseController.java # Pha ngã tư lệch giờ & ép đèn cứu thương
    │   │       │   ├── ThreeWayPhaseController.java    # Pha ngã ba tự động
    │   │       │   └── SimulationMode.java            # Enum định nghĩa các loại bản đồ
    │   │       │
    │   │       ├── model/          # Thực thể tĩnh/động 
    │   │       │   ├── map/        # Các giao lộ, Đèn tín hiệu
    │   │       │   └── vehicle/    # Xe (Car, Motorbike, Bus, Ambulance, FireTruck)
    │   │       │
    │   │       ├── behavior/       # Bộ não lái xe (Strategy Pattern )
    │   │       │   ├── DrivingStrategy.java           # Interface chiến lược lái xe
    │   │       │   ├── NormalDriver.java              # Chấp hành luật, nhường đường, vượt xe
    │   │       │   ├── ViolatorDriver.java            # Vượt đèn đỏ/vàng
    │   │       │   ├── IntersectionNavigator.java     # Nhường đường ngã tư
    │   │       │   ├── OvertakeManager.java           # Chuyển làn vượt xe đi chậm
    │   │       │   ├── RoundaboutNavigator.java       # Định vị quỹ đạo đi bùng binh
    │   │       │   └── TrajectoryController.java      # Nội suy góc ôm cua mềm mại
    │   │       │
    │   │       ├── view/           # Màn hình UI và Đồ họa JavaFX 
    │   │       │   ├── SimulationWindow.java          # Bắt sự kiện chuột/bàn phím, render loop
    │   │       │   ├── ControlPanel.java              # Thanh điều khiển bên phải (Play, Vẽ, Spawn...)
    │   │       │   ├── Renderer.java                  # Giao diện renderer
    │   │       │   ├── TrafficControllerAdapter.java  # Adapter lấy dữ liệu sang View
    │   │       │   ├── SimulationSnapshot.java        # Bản chụp trạng thái để vẽ
    │   │       │   ├── SimulationViewSettings.java    # Các tùy chỉnh hiển thị
    │   │       │   ├── camera/
    │   │       │   │   └── Camera.java                # Di chuyển, phóng to thu nhỏ
    │   │       │   └── renderer/
    │   │       │       ├── DefaultRenderer.java       # Bộ vẽ tổng hợp bản đồ, xe, đèn
    │   │       │       ├── RoadRenderer.java          # Vẽ đường nhựa, vạch kẻ, đường tự vẽ
    │   │       │       ├── VehicleRenderer.java       # Vẽ hình xe (Basic/Graphic)
    │   │       │       └── TrafficLightRenderer.java  # Vẽ đèn và đồng hồ đếm ngược
    │   │       │
    │   │       └── utils/          # Bộ tiện ích hệ thống 
    │   │           ├── ImageLoader.java               # Tải ảnh sprite xe
    │   │           └── SoundPlayer.java               # Phát tiếng còi xe, xi nhan, còi hú cứu thương
    │   │
    │   └── resources/              # Thư mục chứa ảnh, âm thanh 
```

---

## 🛠️ 3. Hướng Dẫn Cài Đặt Môi Trường

### Yêu cầu tối thiểu
- **Java JDK 17** hoặc mới hơn (ví dụ: Eclipse Temurin JDK 17).
- **Apache Maven 3.6+**.

### Các bước cài đặt và build
1. **Clone mã nguồn dự án**:
   ```bash
   git clone https://github.com/GnoucGnad/OOP_Nh-m9_SmartCity.git
   cd SmartCityTrafficSim
   ```
2. **Biên dịch dự án và tải thư viện liên quan**:
   Sử dụng Maven để build dự án:
   ```bash
   mvn clean install
   ```
3. **Chạy ứng dụng**:
   Khởi động giao diện mô phỏng JavaFX:
   ```bash
   mvn javafx:run
   ```
4. **Chạy kiểm thử tự động**:
   Chạy 75 bài test có sẵn để kiểm tra độ ổn định của hệ thống:
   ```bash
   mvn test
   ```

---

## 🎮 4. Hướng Dẫn Sử Dụng & Vận Hành Trên UI

Khi khởi động ứng dụng, bạn sẽ thao tác trực quan với các phần sau:

### 4.1. Bảng Điều Khiển Mô Phỏng (Control Panel)
- **Nút điều khiển Play/Pause/Reset**: Bắt đầu chạy mô phỏng, tạm dừng hoặc thiết lập lại trạng thái ban đầu của hệ thống.
- **Loại Bản Đồ (Loai ban do)**: Lựa chọn giữa Ngã ba (T_INTERSECTION), Ngã tư (CROSS_INTERSECTION), Vòng xuyến ngã năm (FIVE_WAY_INTERSECTION) hoặc Mạng lưới đường kết nối liên hợp (ROAD_NETWORK).
- **Chế độ Vẽ Bản Đồ (Bộ vẽ đường) - [Nâng Cao]**:
  1. Tích chọn **"Chế độ Vẽ Bản Đồ"** (Mô phỏng sẽ tự động tạm dừng để đảm bảo an toàn).
  2. Chọn công cụ trong ComboBox (ví dụ: *Thêm Ngã Tư*, *Thêm Ngã Ba*, *Thêm Vòng Xuyến*, hoặc *Vẽ Đường Nối*).
  3. Click chuột lên canvas để đặt ngã ba/ngã tư tại vị trí mong muốn. Hệ thống sẽ tự động căn gióng (snap) thẳng hàng theo các nút giao lân cận.
  4. Nếu vẽ đường nối: Chọn công cụ *Vẽ Đường Nối*, click vào ngã tư xuất phát (sẽ hiện vòng tròn nét đứt màu xanh dương bao quanh), sau đó click vào ngã tư đích. Hệ thống sẽ tự vẽ một con đường nhựa rộng rãi kết nối hai bên.
  5. Bỏ tích chọn **"Chế độ Vẽ Bản Đồ"** và nhấn **Play**. Xe cộ lưu thông sẽ tự tìm đường đi qua các ngã rẽ và làn đường mới của bạn!
  6. Click nút **"Xóa nét vẽ"** nếu muốn xóa sạch các đường tự vẽ để phục hồi bản đồ mặc định.

### 4.2. Quản Lý Đèn Tín Hiệu & Hiển Thị
- **Chế độ đèn (Den giao thong)**:
  - `AUTO`: Đèn tín hiệu tự động chuyển pha thông minh.
  - `MANUAL`: Bạn có thể click chuột trực tiếp vào các cụm đèn giao thông trên Canvas để chuyển màu theo ý muốn.
- **Hiển thị (Hien thi)**:
  - `BASIC`: Xe cộ hiển thị dưới dạng khối chữ nhật đơn giản ghi tên xe.
  - `GRAPHIC`: Xe cộ hiển thị sống động với hình ảnh sprite xoay đầu, chuyển động bánh xe và đèn chớp.
- **Mật độ xe (Luu luong)**: Kéo thanh trượt để thay đổi lượng xe tối đa xuất hiện trên đường (Thấp: 20 xe / Trung bình: 30 xe / Cao: 40 xe).
- **Phát sinh xe thử nghiệm (Spawn Test)**: Click chọn các nút **Car**, **Moto** (xe máy), **Bus**, **Ambu** (cứu thương), **Fire** (cứu hỏa), hoặc **Viol** (xe vi phạm chạy ẩu) để sinh ngay loại xe đó vào hệ thống.

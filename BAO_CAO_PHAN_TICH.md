# BÁO CÁO PHÂN TÍCH VÀ THIẾT KẾ HỆ THỐNG
## BÀI TẬP 3: CHUYỂN ĐỔI RESTTEMPLATE SANG WEBCLIENT TRONG SPRING WEBFLUX

---

## 1. Bối cảnh bài toán và hiện trạng hệ thống

Hệ thống StoreX phục vụ dịp Flash Sale với lượng truy cập đồng thời lên tới **10.000 người dùng cùng lúc**. Để hiển thị trang chủ hoàn chỉnh, hệ thống cần gửi request nội bộ sang `Promotion Service` nhằm lấy banner khuyến mãi đang kích hoạt (`/api/banners/active`).

Đoạn code ban đầu của hệ thống:

```java
// PromotionService.java -- CODE DANG CO VAN DE
 
@Service
public class PromotionService {
    
    // SAI: Dung RestTemplate trong WebFlux -- RestTemplate la BLOCKING
    private final RestTemplate restTemplate = new RestTemplate();
    
    public Banner getActiveBanner() {
        // SAI: Goi dong bo, block thread
        String url = "http://promotion-service/api/banners/active";
        Banner banner = restTemplate.getForObject(url, Banner.class);
        
        // SAI: Neu service chet, he thong crash
        if (banner == null) {
            throw new RuntimeException("Promotion service unavailable");
        }
        return banner;
    }
}
```

Mặc dù dự án đã được tích hợp thư viện Spring WebFlux, khi tiến hành kiểm thử tải (load testing), hệ thống liên tục xảy ra lỗi **HTTP 500 Internal Server Error**, sập server do **cạn kiệt Thread (Thread Starvation)** và bị treo hoàn toàn.

---

## 2. Phần 1: Phân tích nguyên nhân gốc rễ (Root Cause Analysis)

### 2.1. Lỗi nghiêm trọng phá vỡ kiến trúc Non-blocking của WebFlux
Nguyên tắc cốt lõi của lập trình Reactive và kiến trúc Non-blocking trong Spring WebFlux là: **"Never block the Event Loop thread"** (Tuyệt đối không được chặn Thread của Event Loop).

Trong đoạn mã trên, lập trình viên đã:
1. Sử dụng trực tiếp `RestTemplate` - một HTTP client được thiết kế theo mô hình chặn đồng bộ (Blocking I/O).
2. Gọi phương thức đồng bộ `restTemplate.getForObject(url, Banner.class)` trả về đối tượng `Banner` thay vì một Reactive Publisher (`Mono<Banner>`).
3. Ném trực tiếp ngoại lệ `RuntimeException("Promotion service unavailable")` khi service gặp sự cố mà không có cơ chế timeout hay fallback.

### 2.2. Cơ chế hoạt động của RestTemplate và nguyên nhân gây cạn kiệt Thread (Thread Starvation)
- **Cơ chế Thread-per-request của RestTemplate:**
  - `RestTemplate` hoạt động dựa trên Java `HttpURLConnection` hoặc Apache HttpClient cổ điển, áp dụng I/O đồng bộ (Synchronous Blocking I/O).
  - Khi `restTemplate.getForObject(...)` được gọi, luồng (thread) đang thực thi request sẽ bị đẩy vào trạng thái `WAITING` hoặc `BLOCKED`. Luồng này phải dừng toàn bộ các tác vụ khác và nằm im chờ:
    - Bắt tay mạng (TCP Handshake, DNS Resolution).
    - Remote Promotion Service xử lý truy vấn database, build JSON.
    - Truyền tải từng gói tin HTTP response về qua socket buffer.
  - Chỉ khi toàn bộ dữ liệu phản hồi được tải xong hoặc socket timeout kích hoạt, luồng mới được đánh thức để tiếp tục xử lý.

- **Vì sao gây cạn kiệt Thread:**
  - Trong mô hình servlet truyền thống (Spring MVC + Tomcat), thread pool thường có khoảng 200 luồng. Khi 10.000 request ập đến, 200 luồng nhanh chóng bị khóa (block) trong vài trăm mili-giây, các request sau bị dồn ứ vào queue và dẫn đến timeout hàng loạt.
  - Khi đưa vào Spring WebFlux, tình trạng này trở nên **thảm họa gấp bội**.

### 2.3. Tại sao WebFlux không thể xử lý Non-blocking khi có RestTemplate?
- **Mô hình Thread của Spring WebFlux (Netty Event Loop):**
  - Khác hoàn toàn với Spring MVC, Spring WebFlux mặc định sử dụng máy chủ non-blocking Netty.
  - Netty quản lý request thông qua mô hình **Event Loop** với số lượng thread cực kỳ ít ỏi: mặc định chỉ bằng `2 * Số nhân CPU` (ví dụ máy chủ 4 core chỉ có 8 worker thread).
  - Các thread này được thiết kế để xử lý hàng triệu sự kiện bằng cách: đăng ký callback khi có dữ liệu mạng và ngay lập tức quay lại phục vụ request khác mà không bao giờ chờ đợi.
- **Xung đột chết người khi nhúng RestTemplate vào WebFlux:**
  - Khi một request gọi vào `getActiveBanner()`, RestTemplate chiếm giữ 1 trong 8 Event Loop thread và đưa thread đó vào trạng thái ngủ đông (blocked chờ I/O).
  - Chỉ cần 8 request đồng thời rơi vào tình trạng Promotion Service phản hồi chậm (chẳng hạn mất 500ms - 1s), **toàn bộ 8 thread của Netty Event Loop bị khóa hoàn toàn**.
  - Lúc này, Netty không còn bất kỳ thread nào để:
    - Lắng nghe kết nối TCP mới.
    - Xử lý các request khác của hệ thống StoreX.
    - Thực thi các timer hoặc sự kiện của chính WebFlux.
  - Kết quả là: Hệ thống rơi vào trạng thái tê liệt toàn tập (Thread Starvation), hàng nghìn kết nối của người dùng bị từ chối hoặc trả về HTTP 500.

---

## 3. Phần 2: Thiết kế và giải pháp sửa lỗi theo chuẩn Reactive

### 3.1. Chuyển đổi sang WebClient
- `WebClient` là HTTP client bất đồng bộ, non-blocking được cung cấp bởi Spring WebFlux, hoạt động trên nền tảng Netty HTTP Client.
- Khi gửi request, `WebClient` đăng ký kênh socket với selector của Netty và trả về ngay lập tức một luồng dữ liệu tương lai (`Mono<Banner>`).
- Thread Event Loop không bị giữ lại dù chỉ 1 mili-giây. Khi remote service có kết quả, Netty Event Loop mới nhận tín hiệu và đẩy dữ liệu vào luồng reactive stream.
- Nhờ đó, 8 Netty thread có thể xử lý mượt mà hàng chục nghìn kết nối đồng thời trong dịp Flash Sale.

### 3.2. Cấu hình Timeout đa tầng
Để tránh trường hợp Promotion Service bị treo vô thời hạn làm ứ đọng pipeline reactive, hệ thống thiết lập timeout 2 giây ở 2 tầng:
1. **Tầng Socket / Netty Transport:**
   - `ChannelOption.CONNECT_TIMEOUT_MILLIS = 2000` (timeout thiết lập kết nối TCP).
   - `ReadTimeoutHandler(2000ms)` và `responseTimeout(Duration.ofSeconds(2))` (timeout đọc response từ remote service).
2. **Tầng Reactive Stream Operator:**
   - Toán tử `.timeout(Duration.ofSeconds(2))` trên luồng `Mono`. Nếu sau 2 giây chưa nhận được tín hiệu `onNext`, toán tử sẽ phát tín hiệu `TimeoutException`.

### 3.3. Cơ chế Fallback an toàn (Resilience Pattern)
- Thay vì ném `RuntimeException` gây crash hệ thống và trả mã lỗi HTTP 500 cho người dùng, sử dụng toán tử `.onErrorResume(...)`.
- Khi xảy ra bất kỳ sự cố nào:
  - Timeout sau 2 giây (`TimeoutException`).
  - Remote Promotion Service bị down (Connection Refused, 500 Internal Server Error, 503 Service Unavailable).
  - Dữ liệu trả về bị rỗng (`defaultIfEmpty`).
- Hệ thống sẽ chặn luồng lỗi và trả về ngay một **Banner mặc định**:
  - `title`: *"Khuyến mãi đang được cập nhật"*
  - `content`: *"Khuyến mãi đang được cập nhật"*
  - `status`: *"DEFAULT"*
- Nhờ vậy, trang chủ StoreX vẫn hiển thị trơn tru, trải nghiệm người dùng không bị gián đoạn và hệ thống chịu tải đạt 10.000 user đồng thời một cách an toàn.

---

## 4. Bảng so sánh RestTemplate vs WebClient

| Tiêu chí | Đoạn mã cũ (RestTemplate) | Đoạn mã mới (WebClient) |
| :--- | :--- | :--- |
| **Mô hình I/O** | Synchronous Blocking I/O | Asynchronous Non-blocking I/O |
| **Sử dụng Thread** | Chiếm dụng và khóa cứng thread Netty | Non-blocking, giải phóng thread ngay lập tức |
| **Kiểu trả về** | Đối tượng `Banner` đồng bộ | `Mono<Banner>` phản ứng (Reactive) |
| **Khả năng chịu tải (10.000 user)** | Cạn kiệt thread, sập hệ thống (HTTP 500) | Xử lý mượt mà trên số ít Event Loop thread |
| **Xử lý Timeout & Sự cố** | Không có timeout, ném `RuntimeException` làm crash app | Timeout 2s, fallback Banner mặc định an toàn |

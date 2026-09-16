# StoreX - Promotion Service (WebFlux & WebClient)
## Session 10 - Bai tap 3: Chuyen doi RestTemplate sang WebClient

Du an toi uu hoa API Banner khuyen mai trang chu cho he thong StoreX, dap ung kha nang chiu tai 10.000 nguoi dung dong thoi trong dip Flash Sale bang Spring WebFlux va WebClient.

---

## 1. Yeu cau he thong
- Java: OpenJDK 17 tro len
- Build tool: Gradle (su dung Gradle Wrapper di kem)

---

## 2. Cau truc thu muc du an

```text
Session10_Bai3/
|-- build.gradle
|-- settings.gradle
|-- gradlew
|-- gradlew.bat
|-- gradle/
|   `-- wrapper/
|-- BAO_CAO_PHAN_TICH.md
|-- README.md
`-- src/
    |-- main/
    |   |-- java/com/storex/promotion/
    |   |   |-- PromotionApplication.java
    |   |   |-- config/
    |   |   |   `-- WebClientConfig.java
    |   |   |-- controller/
    |   |   |   `-- PromotionController.java
    |   |   |-- model/
    |   |   |   `-- Banner.java
    |   |   `-- service/
    |   |       `-- PromotionService.java
    |   `-- resources/
    |       `-- application.yml
    `-- test/
        `-- java/com/storex/promotion/
            `-- PromotionServiceTest.java
```

---

## 3. Huong dan bien dich va chay kiem thu

### Kiem thu tu dong (Unit & Reactive Test):
Su dung Gradle Wrapper de chay toan bo cac kich ban kiem thu (Thanh cong, Timeout 2s, Service loi 500, Response rong):

```powershell
.\gradlew.bat test --info
```

### Bien dich du an (Build jar):
```powershell
.\gradlew.bat build
```

### Chay ung dung:
```powershell
.\gradlew.bat bootRun
```

Sau khi ung dung khoi dong tren cong 8080, co the goi API lay banner:
```powershell
curl http://localhost:8080/api/banners/active
```

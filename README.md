# HLSMP-Rtp

Plugin RTP (Random Teleport) cho server HLSMP — chuyển đổi từ DonutRTP, đã:

- Bypass license check (LicenseManager.validateLicenseOnStartup / validateWithAPI luôn trả về true).
- Đổi tên hiển thị plugin / artifactId / plugin.yml → `HLSMP-Rtp`.
- Việt hoá toàn bộ tin nhắn, action bar, title GUI (lang.yml, GUI RTP, GUI Hàng Đợi).
- Sửa lỗi hiển thị thời gian chờ ("kurz") khi đang trong lúc đếm ngược teleport — giờ hiển thị đúng số giây còn lại.
- Sửa lỗi `.replace("1", ...)` trong action bar hàng đợi (gây thay nhầm ký tự "1"), chuyển sang dùng placeholder `%players%`.

## Build

```
mvn package -B
```

Output: `target/HLSMP-Rtp.jar`

## Ghi chú

- Package Java vẫn giữ `de.elivb.donutRTP` (không hiển thị ra ngoài).
- Lệnh: `/rtp`, `/rtpqueue`. Quyền: `rtp.use`, `rtp.admin`, `rtp.bypass` — giữ nguyên.
- PlaceholderAPI identifier vẫn là `donutrtp_...` (vd: `%donutrtp_zone_countdown_<name>%`) — nếu muốn đổi tên placeholder, báo trước vì có thể ảnh hưởng config TAB/Holograms khác đang dùng placeholder này.

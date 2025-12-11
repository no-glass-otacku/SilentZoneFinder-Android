# Logcat 필터 가이드

## 🔍 시스템 에러 필터링

Android Studio의 Logcat에서 시스템 에러를 필터링하여 앱 관련 로그만 보는 방법입니다.

### 1. Logcat 필터 설정

#### 방법 1: 패키지명으로 필터링 (권장)
```
package:com.example.silentzonefinder_android
```

#### 방법 2: 태그로 필터링
앱에서 사용하는 주요 태그들:
```
tag:NewReviewActivity | tag:MainActivity | tag:PlaceDetailActivity | tag:SupabaseManager | tag:MapApplication
```

#### 방법 3: 시스템 에러 제외
```
!tag:BluetoothPowerStatsCollector !tag:PickerSyncController !tag:system_server
```

### 2. 복합 필터 설정

앱 로그만 보기:
```
package:com.example.silentzonefinder_android | tag:SilentZoneFinder
```

에러만 보기 (앱 관련):
```
package:com.example.silentzonefinder_android level:error
```

### 3. 무시해도 되는 시스템 에러

다음 에러들은 Android 시스템 레벨 에러로 앱 기능에 영향을 주지 않습니다:

- `BluetoothPowerStatsCollector` - Bluetooth 통계 수집 관련 시스템 에러
- `PickerSyncController` - 미디어 프로바이더 동기화 관련 시스템 에러
- `system_server` - 시스템 서버 관련 일반적인 에러

이런 에러들은:
- 에뮬레이터나 특정 기기에서 자주 발생
- Android OS 자체의 문제
- 앱 코드와 무관
- 앱 기능에 영향 없음

### 4. Logcat 필터 저장

1. Logcat 창에서 필터 아이콘 클릭
2. "Edit Filter Configuration" 선택
3. 필터 이름 입력 (예: "My App Only")
4. 필터 패턴 입력 (위의 방법 중 선택)
5. "OK" 클릭하여 저장

### 5. 빠른 필터 적용

Logcat 검색창에 직접 입력:
```
package:com.example.silentzonefinder_android
```

또는:
```
-tag:BluetoothPowerStatsCollector -tag:PickerSyncController
```

## 📱 앱 관련 로그 확인

앱에서 사용하는 주요 로그 태그:
- `NewReviewActivity` - 리뷰 작성 관련
- `MainActivity` - 메인 지도 화면
- `PlaceDetailActivity` - 장소 상세 정보
- `SupabaseManager` - Supabase 연결
- `MapApplication` - 앱 초기화

## ⚠️ 주의사항

시스템 에러가 너무 많이 나오면:
1. Logcat 필터를 사용하여 앱 로그만 확인
2. 실제 앱 기능이 정상 작동하는지 확인
3. 앱 관련 에러만 해결


















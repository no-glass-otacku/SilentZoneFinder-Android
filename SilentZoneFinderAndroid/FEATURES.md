# SilentZoneFinder Android - 기능 정리 문서

## 📱 프로젝트 개요
**SilentZoneFinder**는 조용한 장소를 찾아주는 안드로이드 앱입니다. 카카오맵을 활용하여 주변의 조용한 카페, 식당, 도서관 등을 검색하고, 실제 소음 수준을 측정하여 리뷰를 남길 수 있습니다.

---

## 🏗️ 아키텍처 및 기술 스택

### 주요 기술
- **언어**: Kotlin
- **UI**: Android View Binding, Material Design Components
- **지도**: Kakao Map SDK (Vector Map)
- **백엔드**: Supabase (인증, 데이터베이스)
- **네트워크**: Ktor Client
- **비동기 처리**: Kotlin Coroutines

### 주요 라이브러리
- `io.github.jan.supabase` - Supabase 클라이언트
- `com.kakao.vectormap` - 카카오맵 벡터 맵 SDK
- `io.ktor:ktor-client-android` - HTTP 클라이언트
- `kotlinx.serialization` - JSON 직렬화

---

## 🎯 주요 기능

### 1. 지도 화면 (MainActivity)

#### 1.1 카카오맵 통합
- **구현 위치**: `MainActivity.kt`
- **기능**:
  - 카카오맵 벡터 맵 SDK를 사용한 지도 표시
  - 기본 위치: 서울시청 (37.5665, 126.9780)
  - 지도 클릭 시 InfoWindow 닫기
  - 마커 클릭 시 장소 상세 정보 표시

#### 1.2 장소 검색
- **구현 방법**:
  - 카카오 로컬 API 키워드 검색 사용
  - `https://dapi.kakao.com/v2/local/search/keyword.json` 엔드포인트 호출
  - 검색 결과를 RecyclerView로 표시
  - 검색 결과 선택 시 지도 이동 및 장소 상세 화면으로 이동

```kotlin
// 검색 API 호출 예시
private suspend fun searchPlace(query: String) {
    val response: KakaoPlaceResponse = httpClient.get("https://dapi.kakao.com/v2/local/search/keyword.json") {
        url {
            parameters.append("query", query)
            parameters.append("size", "10")
        }
        headers {
            append("Authorization", "KakaoAK $apiKey")
        }
    }.body()
}
```

#### 1.3 카테고리별 장소 검색
- **구현 방법**:
  - ChipGroup을 사용한 카테고리 선택 (Restaurant, Cafe, Bar)
  - 카카오 로컬 API 카테고리 검색 사용
  - 반경 1km 내 장소 검색
  - 검색된 장소를 지도에 Label로 표시

```kotlin
// 카테고리 검색 예시
private suspend fun requestCategoryPlaces(option: CategoryOption, center: LatLng): List<PlaceDocument> {
    val response: KakaoPlaceResponse = httpClient.get("https://dapi.kakao.com/v2/local/search/category.json") {
        url {
            parameters.append("category_group_code", option.code)
            parameters.append("radius", "1000")
            parameters.append("x", center.longitude.toString())
            parameters.append("y", center.latitude.toString())
        }
    }.body()
    return response.documents
}
```

#### 1.4 샘플 마커 표시
- **기능**:
  - 하드코딩된 10개의 샘플 장소 데이터 표시
  - 각 마커에 소음 수준(dB)과 상태 표시
  - 마커 클릭 시 장소 상세 화면으로 이동
- **소음 수준 분류**:
  - Optimal (0-45 dB): Library Quiet
  - Good (45-55 dB): Quiet Conversation
  - Normal (55-65 dB): Lively Chatter
  - Loud (65+ dB): High Traffic

#### 1.5 커스텀 마커 렌더링
- **구현 방법**:
  - XML 레이아웃(`view_noise_marker.xml`)을 Bitmap으로 변환
  - View를 Canvas에 그려서 Bitmap 생성
  - 소음 수준에 따라 색상 변경
  - LabelStyle로 지도에 표시

```kotlin
private fun renderNoiseMarker(place: PlaceUiSample): Bitmap {
    val view = inflater.inflate(R.layout.view_noise_marker, null, false)
    // View를 Bitmap으로 변환
    return Bitmap.createBitmap(view.measuredWidth, view.measuredHeight, Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas = Canvas(bitmap)
        view.draw(canvas)
    }
}
```

#### 1.6 필터 드롭다운
- **기능**:
  - 소음 수준별 필터링 (All, Optimal, Good, Normal, Loud)
  - 각 옵션에 색상 인디케이터 표시
  - 현재는 UI만 구현 (실제 필터링 로직은 미구현)

#### 1.7 하단 네비게이션
- **구현 방법**:
  - Material BottomNavigationView 사용
  - 4개 탭: Map, My Reviews, My Favorites, Profile
  - Activity 재사용을 위한 `FLAG_ACTIVITY_REORDER_TO_FRONT` 플래그 사용
  - 전환 애니메이션 제거 (`overridePendingTransition(0, 0)`)

---

### 2. 로그인/회원가입 (LoginActivity)

#### 2.1 이메일 기반 인증
- **구현 방법**:
  - Supabase Auth 사용
  - 이메일/비밀번호 검증 (정규식, 최소 길이)
  - 회원가입 시 이메일 인증 필요 안내
  - 로그인 성공 시 MainActivity로 이동

```kotlin
// 로그인 예시
private suspend fun login(email: String, password: String) {
    client.auth.signInWith(Email) {
        this.email = email
        this.password = password
    }
    // 로그인 성공 후 MainActivity로 이동
    val intent = Intent(this, MainActivity::class.java)
    startActivity(intent)
    finish()
}
```

#### 2.2 입력 검증
- 이메일 형식 검증 (정규식)
- 비밀번호 최소 길이 검증 (6자 이상)
- 에러 메시지 표시 (Toast)

---

### 3. 장소 상세 화면 (PlaceDetailActivity)

#### 3.1 장소 정보 표시
- 장소명, 주소, 카테고리 표시
- Intent로 전달받은 정보 표시

#### 3.2 리뷰 목록 표시
- **구현 방법**:
  - Supabase Postgrest를 사용하여 리뷰 데이터 조회
  - `reviews` 테이블에서 `kakao_place_id`로 필터링
  - RecyclerView + ReviewAdapter로 표시
  - 최신순 정렬

```kotlin
// 리뷰 로드 예시
private fun loadReviews(placeId: String) {
    lifecycleScope.launch {
        val reviews = withContext(Dispatchers.IO) {
            SupabaseManager.client.postgrest["reviews"]
                .select()
                .decodeList<ReviewDto>()
                .filter { it.kakaoPlaceId == placeId }
                .sortedByDescending { it.createdAt }
        }
        reviewAdapter.submitList(reviews.map { it.toUiModel() })
    }
}
```

#### 3.3 리뷰 요약 정보
- 평균 소음 수준 (dB)
- 평균 평점
- 리뷰 개수
- 소음 상태 배지 (Optimal/Good/Normal/Loud)

---

### 4. 리뷰 작성 (NewReviewActivity)

#### 4.1 실시간 소음 측정
- **구현 방법**:
  - Android `AudioRecord` API 사용
  - 마이크 권한 요청 및 확인
  - 백그라운드 스레드에서 지속적으로 오디오 샘플 읽기
  - 진폭을 데시벨로 변환: `20 * log10(amplitude)`
  - 50ms마다 UI 업데이트

```kotlin
// 소음 측정 예시
private fun startMeasurement() {
    audioRecord = AudioRecord(
        MediaRecorder.AudioSource.MIC,
        RECORDER_SAMPLERATE,
        RECORDER_CHANNELS,
        RECORDER_AUDIO_ENCODING,
        BUFFER_SIZE
    )
    audioRecord.startRecording()
    
    measurementThread = Thread {
        val buffer = ShortArray(BUFFER_SIZE)
        while (isMeasuring) {
            audioRecord.read(buffer, 0, BUFFER_SIZE)
            val maxAmplitude = buffer.maxOrNull()?.toDouble() ?: 0.0
            val db = if (maxAmplitude > 0) {
                20.0 * Math.log10(maxAmplitude)
            } else 0.0
            runOnUiThread { updateDecibelUI(db) }
            Thread.sleep(50)
        }
    }
    measurementThread.start()
}
```

#### 4.2 소음 수준 분류
- 측정된 dB 값에 따라 자동 분류:
  - ≤ 45 dB: Library Quiet
  - ≤ 58 dB: Quiet Conversation
  - ≤ 70 dB: Lively Chatter
  - > 70 dB: High Traffic

#### 4.3 리뷰 작성 UI
- 별점 입력 (RatingBar)
- 리뷰 텍스트 입력
- 편의시설 태그 선택 (ChipGroup)
- 측정된 dB 값 표시
- "Record Again" 버튼으로 재측정 가능

#### 4.4 화면 전환
- 소음 측정 화면 → 리뷰 작성 화면
- View visibility를 통한 화면 전환

---

### 5. 내 리뷰 관리 (MyReviewsActivity)

#### 5.1 리뷰 목록 표시
- **구현 방법**:
  - RecyclerView + MyReviewsAdapter 사용
  - 현재는 더미 데이터 사용
  - 각 리뷰 카드에 장소명, dB, 상태, 평점, 날짜, 리뷰 텍스트 표시

#### 5.2 필터링 기능
- **구현 방법**:
  - PopupMenu를 사용한 필터 선택
  - 필터 옵션: All Reviews, Library Quiet, Quiet Conversation, Lively Chatter, High Traffic
  - 원본 리스트에서 필터링하여 어댑터 업데이트

```kotlin
private fun applyFilter(filterOption: String) {
    val filteredList = if (filterOption == "All Reviews") {
        originalReviewList
    } else {
        originalReviewList.filter { it.status == filterOption }
    }
    reviewAdapter.updateReviews(filteredList)
}
```

#### 5.3 정렬 기능
- **구현 방법**:
  - PopupMenu를 사용한 정렬 선택
  - 정렬 옵션:
    - Most Recent: 날짜 내림차순
    - Highest Rating: 평점 내림차순
    - Optimal to Loud: 소음 수준 오름차순 (조용한 순)

```kotlin
private fun applySort(sortOption: String) {
    val sortedList = when (sortOption) {
        "Most Recent" -> originalReviewList.sortedByDescending { it.date }
        "Highest Rating" -> originalReviewList.sortedByDescending { it.rating }
        "Optimal to Loud" -> {
            val statusOrder = mapOf("Library Quiet" to 0, "Quiet Conversation" to 1, ...)
            originalReviewList.sortedBy { statusOrder[it.status] }
        }
    }
    reviewAdapter.updateReviews(sortedList)
}
```

#### 5.4 새 리뷰 작성 버튼
- "+ New" 버튼 클릭 시 NewReviewActivity로 이동

---

### 6. 프로필 관리 (ProfileActivity)

#### 6.1 로그인 상태 관리
- **구현 방법**:
  - SharedPreferences를 사용한 로그인 상태 저장
  - 로그인/비로그인 상태에 따라 UI 전환
  - 로그인 시 이메일 기반 사용자명 표시

#### 6.2 로그인/회원가입
- ProfileActivity 내에서도 로그인/회원가입 가능
- Supabase Auth 사용

#### 6.3 로그아웃
- Supabase `signOut()` 호출
- SharedPreferences 초기화
- 로그인 화면으로 전환

#### 6.4 알림 설정
- Switch를 사용한 조용한 존 추천 알림 토글
- 현재는 UI만 구현 (실제 알림 로직 미구현)

#### 6.5 설정/알림 히스토리 이동
- SettingsActivity, NotificationHistoryActivity로 이동

---

### 7. 기타 화면

#### 7.1 MyFavoritesActivity
- 현재는 UI만 구현 (기능 미구현)
- 하단 네비게이션 통합

#### 7.2 SettingsActivity
- 언어, 테마, 이용약관, 개인정보처리방침 메뉴
- 현재는 Toast 메시지만 표시 (기능 미구현)

#### 7.3 NotificationHistoryActivity
- 현재는 UI만 구현 (기능 미구현)

---

## 🔧 핵심 컴포넌트

### 1. SupabaseManager
- **역할**: Supabase 클라이언트 싱글톤 관리
- **구현 방법**:
  - `BuildConfig`에서 URL과 API Key 읽기
  - lazy initialization
  - 설정 검증 및 에러 처리

```kotlin
val client: SupabaseClient by lazy {
    val supabaseUrl = BuildConfig.SUPABASE_URL
    val supabaseKey = BuildConfig.SUPABASE_ANON_KEY
    createSupabaseClient(supabaseUrl = supabaseUrl, supabaseKey = supabaseKey) {
        install(Auth)
        install(Postgrest)
        install(Storage)
    }
}
```

### 2. MapApplication
- **역할**: 앱 초기화 및 카카오맵 SDK 설정
- **구현 방법**:
  - `Application` 클래스 상속
  - `onCreate()`에서 카카오맵 SDK 초기화
  - `BuildConfig`에서 네이티브 앱 키 읽기

### 3. 데이터 모델

#### Review (data/Review.kt)
```kotlin
data class Review(
    val placeName: String,
    val decibel: Int,
    val status: String,
    val date: String,
    val reviewText: String,
    val rating: Int,
    val amenities: List<String>
)
```

#### ReviewUiModel (adapter/ReviewAdapter.kt)
- PlaceDetailActivity에서 사용하는 리뷰 UI 모델
- Supabase에서 받은 데이터를 UI에 맞게 변환

### 4. Adapter

#### PlaceSearchAdapter
- MainActivity의 검색 결과 표시
- 클릭 시 지도 이동 및 상세 화면 이동

#### ReviewAdapter
- PlaceDetailActivity의 리뷰 목록 표시
- 별점, 소음 수준, 상태 배지, 편의시설 태그 표시

#### MyReviewsAdapter
- MyReviewsActivity의 내 리뷰 목록 표시
- 상태 배지 색상 동적 변경
- 편의시설 태그 동적 생성

---

## 🔐 권한 관리

### 필요한 권한
1. **INTERNET**: 네트워크 통신
2. **ACCESS_FINE_LOCATION**: 정확한 위치 정보 (현재는 미사용)
3. **ACCESS_COARSE_LOCATION**: 대략적인 위치 정보 (현재는 미사용)
4. **RECORD_AUDIO**: 소음 측정을 위한 마이크 접근

### 권한 요청 구현
- `NewReviewActivity`에서 `RECORD_AUDIO` 권한 요청
- `ActivityCompat.requestPermissions()` 사용
- 권한 거부 시 Activity 종료

---

## 🎨 UI/UX 특징

### 1. Material Design
- Material Components 사용 (Button, Chip, TextField 등)
- Material CardView로 리뷰 카드 표시
- BottomNavigationView로 네비게이션

### 2. View Binding
- 모든 Activity에서 View Binding 사용
- `ActivityXxxBinding` 클래스로 뷰 접근

### 3. 애니메이션
- Activity 전환 애니메이션 제거 (빠른 전환)
- ProgressBar 애니메이션 (소음 측정 시)

### 4. 색상 시스템
- 소음 수준별 색상:
  - Optimal: 초록색 계열
  - Good: 파란색 계열
  - Normal: 노란색 계열
  - Loud: 빨간색 계열

---

## 📊 데이터 흐름

### 1. 리뷰 작성 플로우
```
NewReviewActivity
  → 소음 측정 (AudioRecord)
  → dB 값 계산
  → 리뷰 작성 (별점, 텍스트, 편의시설)
  → (TODO: Supabase에 저장)
  → 이전 화면으로 복귀
```

### 2. 장소 검색 플로우
```
MainActivity
  → 사용자 입력 (키워드 또는 카테고리)
  → 카카오 로컬 API 호출
  → 결과 표시 (RecyclerView 또는 지도 마커)
  → 장소 선택
  → PlaceDetailActivity로 이동
  → Supabase에서 리뷰 조회
  → 리뷰 목록 표시
```

### 3. 인증 플로우
```
LoginActivity / ProfileActivity
  → 이메일/비밀번호 입력
  → Supabase Auth API 호출
  → 성공 시 SharedPreferences에 저장
  → MainActivity로 이동
```

---

## 🚧 미구현 기능

1. **리뷰 저장**: NewReviewActivity에서 작성한 리뷰를 Supabase에 저장하는 로직 미구현
2. **즐겨찾기**: MyFavoritesActivity 기능 미구현
3. **알림**: 조용한 존 추천 알림 로직 미구현
4. **필터링**: MainActivity의 필터 드롭다운 실제 필터링 로직 미구현
5. **위치 기반 검색**: 현재 위치 기반 장소 검색 미구현
6. **이미지 업로드**: 리뷰에 이미지 첨부 기능 미구현
7. **리뷰 수정/삭제**: 리뷰 관리 기능 미구현

---

## 📝 주요 파일 구조

```
app/src/main/
├── java/com/example/silentzonefinder_android/
│   ├── MainActivity.kt              # 지도 화면
│   ├── LoginActivity.kt            # 로그인/회원가입
│   ├── PlaceDetailActivity.kt      # 장소 상세
│   ├── NewReviewActivity.kt        # 리뷰 작성
│   ├── MyReviewsActivity.kt        # 내 리뷰 관리
│   ├── ProfileActivity.kt          # 프로필
│   ├── MyFavoritesActivity.kt      # 즐겨찾기 (미구현)
│   ├── SettingsActivity.kt          # 설정 (부분 구현)
│   ├── NotificationHistoryActivity.kt # 알림 히스토리 (미구현)
│   ├── MapApplication.kt            # 앱 초기화
│   ├── SupabaseManager.kt          # Supabase 클라이언트
│   ├── adapter/
│   │   ├── PlaceSearchAdapter.kt
│   │   ├── ReviewAdapter.kt
│   │   └── MyReviewsAdapter.kt
│   └── data/
│       └── Review.kt
└── res/
    ├── layout/                      # XML 레이아웃 파일들
    ├── drawable/                    # 드로어블 리소스
    ├── values/                      # 문자열, 색상 등
    └── menu/                        # 메뉴 리소스
```

---

## 🔑 환경 설정

### BuildConfig 변수
- `KAKAO_NATIVE_APP_KEY`: 카카오맵 네이티브 앱 키
- `KAKAO_REST_API_KEY`: 카카오 로컬 API 키
- `SUPABASE_URL`: Supabase 프로젝트 URL
- `SUPABASE_ANON_KEY`: Supabase 익명 키

### local.properties 설정 예시
```properties
kakao.native.app.key=YOUR_KAKAO_NATIVE_APP_KEY
kakao.rest.api.key=YOUR_KAKAO_REST_API_KEY
supabase.url=https://your-project.supabase.co
supabase.anon.key=YOUR_SUPABASE_ANON_KEY
```

---

## 📌 참고사항

1. **샘플 데이터**: MainActivity에 하드코딩된 10개의 샘플 장소 데이터가 있습니다.
2. **더미 데이터**: MyReviewsActivity에서 사용하는 리뷰 데이터는 현재 더미 데이터입니다.
3. **에러 처리**: 대부분의 네트워크 요청에 try-catch 블록이 있어 에러 처리가 되어 있습니다.
4. **로깅**: 주요 작업에 Log.d/Log.e를 사용하여 디버깅 정보를 출력합니다.

---

이 문서는 프로젝트의 현재 상태를 기반으로 작성되었으며, 향후 기능 추가 시 업데이트가 필요합니다.









# SilentZoneFinder Android

조용한 장소를 찾아주는 Android 앱입니다. Kakao Map과 Supabase를 사용하여 장소 리뷰 및 즐겨찾기 기능을 제공합니다.

## 🚀 시작하기

### 필수 요구사항

- Android Studio Hedgehog | 2023.1.1 이상
- JDK 11 이상
- Android SDK 33 이상
- Gradle 8.0 이상

### 설치 및 실행

1. **프로젝트 클론**
   ```bash
   git clone <repository-url>
   cd SilentZoneFinderAndroid
   ```

2. **API 키 설정 (필수)**
   
   `local.properties.example` 파일을 `local.properties`로 복사하고 실제 API 키 값으로 수정하세요:
   
   ```bash
   cp local.properties.example local.properties
   ```
   
   `local.properties` 파일을 열어 다음 값들을 설정하세요:
   
   ```properties
   # Android SDK 경로 (자동으로 설정됨)
   sdk.dir=C\:\\Users\\YourUsername\\AppData\\Local\\Android\\Sdk
   
   # Supabase 설정
   supabase.url=https://your-project-id.supabase.co
   supabase.anon.key=your-supabase-anon-key-here
   
   # Kakao 설정
   kakao.rest.api.key=your-kakao-rest-api-key-here
   kakao.native.app.key=your-kakao-native-app-key-here
   ```

3. **API 키 발급 방법**

   **Supabase:**
   - [Supabase 대시보드](https://app.supabase.com)에 로그인
   - 프로젝트 선택 > Settings > API
   - `Project URL`과 `anon public` 키를 복사하여 `local.properties`에 입력
   
   **Kakao:**
   - [Kakao Developers 콘솔](https://developers.kakao.com)에 로그인
   - 내 애플리케이션 > 앱 키
   - `REST API 키`와 `네이티브 앱 키`를 복사하여 `local.properties`에 입력

4. **프로젝트 빌드 및 실행**
   ```bash
   ./gradlew build
   ```
   
   또는 Android Studio에서 프로젝트를 열고 Run 버튼을 클릭하세요.

## ⚠️ 중요 사항

- **`local.properties` 파일은 절대 Git에 커밋하지 마세요!**
  - 이 파일은 `.gitignore`에 포함되어 있어 자동으로 제외됩니다.
  - API 키는 민감한 정보이므로 공개 저장소에 올리면 안 됩니다.

- **API 키가 없으면 앱이 정상 작동하지 않습니다.**
  - Supabase 키가 없으면: 데이터베이스 연결 실패
  - Kakao API 키가 없으면: 지도 표시 및 장소 검색 실패

## 📁 프로젝트 구조

```
SilentZoneFinderAndroid/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/silentzonefinder_android/
│   │   │   ├── MainActivity.kt          # 메인 지도 화면
│   │   │   ├── MyReviewsActivity.kt     # 내 리뷰 목록
│   │   │   ├── MyFavoritesActivity.kt   # 즐겨찾기 목록
│   │   │   ├── PlaceDetailActivity.kt    # 장소 상세 정보
│   │   │   ├── NewReviewActivity.kt      # 리뷰 작성/수정
│   │   │   ├── ProfileActivity.kt        # 프로필
│   │   │   ├── MapApplication.kt        # 앱 초기화 (Kakao Map SDK)
│   │   │   └── SupabaseManager.kt       # Supabase 클라이언트
│   │   └── res/                          # 리소스 파일
│   └── build.gradle.kts                  # 앱 빌드 설정
├── local.properties.example              # API 키 설정 템플릿
├── build.gradle.kts                      # 프로젝트 빌드 설정
└── README.md                             # 이 파일
```

## 🔧 빌드 설정

프로젝트는 `app/build.gradle.kts`에서 `local.properties` 파일을 읽어 BuildConfig 필드로 변환합니다:

- `BuildConfig.SUPABASE_URL`
- `BuildConfig.SUPABASE_ANON_KEY`
- `BuildConfig.KAKAO_REST_API_KEY`
- `BuildConfig.KAKAO_NATIVE_APP_KEY`

이 값들은 런타임에 앱에서 사용됩니다.

## 🐛 문제 해결

### "Supabase 설정이 누락되었습니다" 오류
- `local.properties` 파일이 프로젝트 루트에 있는지 확인
- `supabase.url`과 `supabase.anon.key` 값이 올바르게 설정되었는지 확인
- Android Studio에서 File > Sync Project with Gradle Files 실행

### "카카오맵 SDK 초기화 실패" 오류
- `local.properties`에 `kakao.native.app.key`가 설정되었는지 확인
- Kakao Developers 콘솔에서 앱 패키지명이 올바르게 등록되었는지 확인
- Android Studio에서 File > Sync Project with Gradle Files 실행

### 지도가 표시되지 않음
- `kakao.rest.api.key`와 `kakao.native.app.key`가 모두 설정되었는지 확인
- Kakao Developers 콘솔에서 플랫폼 설정이 올바른지 확인

### "row-level security policy" 오류
- Supabase의 RLS (Row Level Security) 정책이 설정되지 않았을 때 발생
- `SUPABASE_RLS_SETUP.md` 파일을 참고하여 RLS 정책을 설정하세요
- 인증된 사용자가 `places`, `reviews`, `favorites` 테이블에 접근할 수 있도록 정책을 추가해야 합니다

## 📝 라이선스

이 프로젝트는 개인 프로젝트입니다.

## 👥 기여자

프로젝트에 기여하고 싶으시다면 이슈를 생성하거나 Pull Request를 보내주세요.




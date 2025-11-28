# 🚀 Activity → Fragment + Navigation Component 마이그레이션 가이드

## 📊 현재 구조 분석

### 현재 Activity 목록 (총 13개)

**메인 네비게이션 화면 (4개):**
1. `MainActivity` - 지도 화면
2. `MyReviewsActivity` - 내 리뷰 목록
3. `MyFavoritesActivity` - 즐겨찾기 목록
4. `ProfileActivity` - 프로필 화면

**서브 화면 (9개):**
5. `PlaceDetailActivity` - 장소 상세
6. `NewReviewActivity` - 리뷰 작성/수정
7. `SearchHistoryActivity` - 검색 기록
8. `SettingsActivity` - 설정
9. `ContactSupportActivity` - 고객 지원
10. `NotificationHistoryActivity` - 알림 히스토리
11. `NoiseThresholdActivity` - 소음 임계값 설정
12. `EditReviewActivity` - 리뷰 수정 (사용 안 함?)
13. `LoginActivity` - 로그인 (선택사항)

## ⚠️ 마이그레이션 시 필요한 작업

### 1. **의존성 추가**
```kotlin
// build.gradle.kts
implementation("androidx.navigation:navigation-fragment-ktx:2.7.6")
implementation("androidx.navigation:navigation-ui-ktx:2.7.6")
```

### 2. **구조 변경 작업**

#### A. Single Activity 패턴 적용
- **새로 생성**: `MainContainerActivity` (단일 Activity)
- **변환**: 각 Activity → Fragment
  - `MainActivity` → `MapFragment`
  - `MyReviewsActivity` → `MyReviewsFragment`
  - `MyFavoritesActivity` → `MyFavoritesFragment`
  - `ProfileActivity` → `ProfileFragment`
  - `PlaceDetailActivity` → `PlaceDetailFragment`
  - `NewReviewActivity` → `NewReviewFragment`
  - 기타 서브 화면들도 Fragment로 변환

#### B. Navigation Graph 생성
- `res/navigation/nav_graph.xml` 생성
- 모든 Fragment와 화면 전환 경로 정의
- Arguments 정의 (Intent Extra 대체)

#### C. Bottom Navigation 연동
- 현재: 각 Activity마다 `setupBottomNavigation()` 중복
- 변경: `MainContainerActivity`에서 한 번만 설정
- Navigation Component와 `BottomNavigationView` 연동

### 3. **코드 변경 작업**

#### A. Intent → Navigation Arguments
```kotlin
// 현재 (Intent)
val intent = Intent(this, PlaceDetailActivity::class.java).apply {
    putExtra("kakao_place_id", placeId)
    putExtra("place_name", name)
}
startActivity(intent)

// 변경 후 (Navigation)
findNavController().navigate(
    R.id.action_mapFragment_to_placeDetailFragment,
    bundleOf(
        "kakaoPlaceId" to placeId,
        "placeName" to name
    )
)
```

#### B. ActivityResultLauncher → Fragment Result API
```kotlin
// 현재
private val reviewLauncher = registerForActivityResult(...)

// 변경 후
setFragmentResultListener("review_result") { key, bundle ->
    // 결과 처리
}
```

#### C. ViewBinding 변경
- Activity Binding → Fragment Binding
- `ActivityXxxBinding` → `FragmentXxxBinding`

#### D. Lifecycle 관리
- `onCreate()`, `onResume()` 등 Activity 생명주기 → Fragment 생명주기
- `onViewCreated()`, `onDestroyView()` 등 Fragment 생명주기 사용

### 4. **특수 케이스 처리**

#### A. MapView (KakaoMap)
- **문제**: MapView는 Activity 생명주기에 강하게 결합
- **해결**: Fragment에서도 작동하지만 주의 필요
- MapView의 `onCreate()`, `onResume()`, `onPause()`, `onDestroy()` 직접 호출 필요

#### B. Bottom Navigation 공유
- 현재: 각 Activity마다 Bottom Navigation 포함
- 변경: `MainContainerActivity`에 하나만 포함
- Fragment 전환 시 Bottom Navigation 유지

#### C. ActivityResultLauncher
- NewReviewActivity → PlaceDetailActivity 결과 전달
- Fragment Result API로 변경 필요

### 5. **레이아웃 파일 변경**

#### A. Activity 레이아웃 → Fragment 레이아웃
- `activity_main.xml` → `fragment_map.xml`
- `activity_my_reviews.xml` → `fragment_my_reviews.xml`
- 기타 모든 activity_*.xml → fragment_*.xml

#### B. 공통 레이아웃
- Bottom Navigation을 포함한 `activity_main_container.xml` 생성
- `<fragment>` 또는 `<FragmentContainerView>` 사용

### 6. **테스트 필요 항목**

- [ ] 각 화면 전환 테스트
- [ ] Back 버튼 동작 테스트
- [ ] Bottom Navigation 동작 테스트
- [ ] Deep Link 테스트 (선택사항)
- [ ] 화면 회전 시 상태 유지 테스트
- [ ] MapView 생명주기 테스트

## 📈 예상 작업량

### 소요 시간 추정
- **작은 프로젝트**: 2-3일
- **중간 프로젝트**: 1주일
- **현재 프로젝트 (13개 Activity)**: **2-3주일** ⚠️

### 작업 우선순위
1. **High**: 메인 네비게이션 4개 화면 (MainActivity, MyReviewsActivity, MyFavoritesActivity, ProfileActivity)
2. **Medium**: 자주 사용하는 서브 화면 (PlaceDetailActivity, NewReviewActivity)
3. **Low**: 설정/지원 화면들 (SettingsActivity, ContactSupportActivity 등)

## 💡 장점

### 1. **코드 중복 제거**
- Bottom Navigation 설정 코드 중복 제거
- Activity 전환 로직 단순화

### 2. **성능 개선**
- Activity 재생성 비용 제거
- Fragment 전환은 더 가벼움

### 3. **유지보수성 향상**
- 화면 전환 로직이 Navigation Graph에 명시적으로 정의됨
- Deep Link 지원 용이

### 4. **모던 아키텍처**
- Android 권장 패턴
- Material Design 3 가이드라인 준수

## ⚠️ 단점 및 주의사항

### 1. **대규모 리팩토링**
- 거의 모든 Activity 코드 수정 필요
- 테스트 범위가 넓음

### 2. **MapView 복잡성**
- KakaoMap MapView는 Fragment에서 생명주기 관리가 복잡할 수 있음

### 3. **기존 코드와의 호환성**
- 기존 Intent 기반 코드 모두 변경 필요
- ActivityResultLauncher → Fragment Result API 변경

## 🎯 권장 사항

### 옵션 1: 점진적 마이그레이션 (권장)
1. 먼저 메인 네비게이션 4개만 Fragment로 변환
2. 서브 화면은 기존 Activity 유지
3. 점진적으로 서브 화면도 변환

### 옵션 2: 현재 구조 유지
- 현재 구조도 충분히 작동함
- Activity 기반도 나쁜 패턴은 아님
- 리팩토링 비용 대비 이점이 크지 않을 수 있음

### 옵션 3: 하이브리드 접근
- 메인 네비게이션만 Fragment로 변환
- 서브 화면은 Activity 유지
- Navigation Component로 메인 화면만 관리

## 📝 체크리스트

마이그레이션 시 확인할 사항:

- [ ] Navigation Component 의존성 추가
- [ ] Navigation Graph 생성
- [ ] MainContainerActivity 생성
- [ ] 각 Activity → Fragment 변환
- [ ] 레이아웃 파일 변경 (activity_* → fragment_*)
- [ ] Intent → Navigation Arguments 변경
- [ ] ActivityResultLauncher → Fragment Result API 변경
- [ ] Bottom Navigation 연동
- [ ] MapView 생명주기 처리
- [ ] Back 버튼 동작 확인
- [ ] 화면 전환 테스트
- [ ] 기존 기능 회귀 테스트

---

**결론**: 대규모 리팩토링이지만, 장기적으로는 유지보수성과 성능 향상에 도움이 됩니다. 
하지만 현재 구조가 잘 작동한다면, 반드시 필요한 작업은 아닙니다.


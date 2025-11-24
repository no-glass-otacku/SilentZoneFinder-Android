# 📋 SilentZoneFinder Android - 개발 체크리스트

이 문서는 SilentZoneFinder Android 앱의 완성을 위한 구현 체크리스트입니다.

---

## 🎯 우선순위별 구현 항목

### 🔴 **P0 - 핵심 기능 (필수)**

#### 1. 리뷰 저장 기능
- [x] **NewReviewActivity 소음 측정 기능 구현**
  - [x] AudioRecord를 사용한 실시간 소음 측정
  - [x] dB 값 계산 및 UI 업데이트 (50ms마다)
  - [x] 소음 수준 분류 (Library Quiet, Quiet Conversation, Lively Chatter, High Traffic)
  - [x] 리뷰 작성 UI (별점, 텍스트, 편의시설 태그)
  - [x] 마이크 권한 요청 및 처리
  - [x] 개발 모드: 디버그 빌드에서 데시벨 직접 입력 기능 추가

- [x] **NewReviewActivity에서 Supabase로 리뷰 저장 구현**
  - [x] `setupSubmitButton()`에서 Supabase insert 로직 추가
  - [x] 현재 로그인한 사용자 ID 가져오기 (Supabase Auth)
  - [x] PlaceDetailActivity에서 전달받은 `kakao_place_id` 사용
  - [x] 리뷰 저장 전 `places` 테이블 확인: 해당 `kakao_place_id`가 없으면 먼저 생성 (upsert)
    - [x] 장소 정보 (name, address, lat, lng)를 `places` 테이블에 저장
    - [x] PlaceInsertDto 데이터 클래스 사용 (직렬화 오류 해결)
    - [x] PlaceDto로 존재 확인 (Map<String, Any> 대신)
  - [x] 측정된 dB 값 (`finalMeasuredDb`) 저장
  - [x] 별점, 리뷰 텍스트 저장
  - [x] `amenities` 필드는 DB에 없으므로 제외 (저장하지 않음)
  - [x] `images` 필드는 jsonb 타입으로 저장 (현재는 null로 저장)
  - [x] 저장 성공 시 PlaceDetailActivity로 돌아가서 리뷰 목록 갱신 (ActivityResultLauncher 사용)
  - [x] 저장 실패 시 에러 처리 및 Toast 메시지

- [x] **Supabase reviews 테이블 스키마 확인 및 매핑**
  - [x] ReviewDto와 Supabase 스키마 일치 확인 (PlaceDetailActivity에서 사용 중)
  - [x] 필수 필드: `id`, `kakao_place_id`, `rating`, `text`, `noise_level_db`, `user_id`, `created_at`
  - [x] 선택 필드: `images` (jsonb 타입)
  - [x] `amenities` 필드가 데이터베이스에 없음 - 리뷰 저장 시 제외 처리 완료 ✅

- [x] **리뷰 작성 시 Intent로 장소 정보 전달**
  - [x] PlaceDetailActivity에서 NewReviewActivity 호출 시 `kakao_place_id` 전달
  - [x] NewReviewActivity에서 Intent Extra로 받아서 사용
  - [x] 장소 정보 (placeName, address, lat, lng)도 함께 전달
  - [x] DUMMY_PLACE_ID, DUMMY_USER_ID 제거

#### 2. 지도 실시간 소음 표시
- [x] **지도 마커를 리뷰 데이터 기반으로 동적 생성**
  - [x] 카테고리 검색 결과에 대해 Supabase에서 리뷰 조회
  - [x] 각 장소별 평균 dB 계산
  - [x] 평균 dB에 따라 마커 색상/스타일 적용 (Optimal, Good, Normal, Loud)
  - [x] 리뷰가 없는 장소는 기본 마커 표시
- [x] MainActivity의 샘플 데이터 제거 (Supabase 리뷰 기반 마커만 사용)

- [x] **지도 필터링 기능 UI 구현** (Figma: "All Locations" 드롭다운)
  - [x] 필터 드롭다운 UI 구현 (All Locations, Optimal Only, Good Only, Normal Only, Loud Only)
  - [x] 필터 옵션별 색상 인디케이터 표시 (Optimal: 녹색, Good: 노란색, Normal: 주황색, Loud: 빨간색)
  - [x] 선택된 필터에 따라 마커 표시/숨김 (샘플/표시 중인 좌표 기준)
  - [x] 필터 변경 시 지도 갱신 (NoiseLevel별 재렌더링)
  - [x] 현재 선택된 필터 표시 (AutoCompleteTextView 텍스트 반영)

- [x] **지도 페이지 UI 요소 구현**
  - [x] 각 장소별 dB 값 표시 (마커에 표시됨)
  - [x] 검색 입력 필드 구현 (키워드 검색 기능 포함)
  - [x] 필터 드롭다운 구현
  - [x] 카테고리 칩 그룹 (Restaurants, Cafes, Bars)

- [x] **지도 줌/이동 시 주변 리뷰 데이터 자동 로드**
  - [x] 카메라 이동 이벤트 리스너 추가
  - [x] 뷰포트 기반 마커 필터링 (현재 화면 영역 내 장소만 표시)
  - [x] 성능 최적화 (디바운싱 구현, 줌 레벨별 반경 조정)

#### 3. 내 리뷰 관리
- [x] **MyReviewsActivity 기본 기능 구현**
  - [x] 리뷰 목록 표시 (RecyclerView)
  - [x] 필터링 기능 (All Reviews, Library Quiet, Quiet Conversation, Lively Chatter, High Traffic)
  - [x] 정렬 기능 (날짜, 평점 등)
  - [x] 리뷰 항목 UI (장소명, dB, 상태 배지, 평점, 날짜, 리뷰 텍스트)
  - [x] `loadDummyData()` 제거 → Supabase 실데이터 연동
  - [x] 현재 로그인한 사용자의 리뷰만 조회 (`auth.currentSessionOrNull`)
  - [x] Supabase `reviews` + `places` 조합으로 placeName 로드 (user_id 기준 필터)
  - [x] 로딩 상태 표시 (ProgressBar, try/catch 에러 처리)
  - [x] 빈 리뷰/필터 결과 없음 UI 처리 (Figma 스타일 버튼 + 안내 문구)

- [x] **리뷰 수정 기능**
  - [x] 리뷰 항목 클릭 시 수정 화면으로 이동 (NewReviewActivity 재사용)
  - [x] 기존 리뷰 데이터 로드 (별점, 텍스트, dB)
  - [x] 수정 모드에서 소음(dB) 수정 불가 처리
  - [x] Supabase UPDATE 쿼리 구현
  - [x] 수정 후 목록 갱신

- [x] **리뷰 삭제 기능**
  - [x] 리뷰 항목에 삭제 버튼 추가
  - [x] 삭제 확인 다이얼로그
  - [x] Supabase DELETE 쿼리 구현
  - [x] 삭제 후 목록 갱신

---

### 🟡 **P1 - 중요 기능**

#### 4. 즐겨찾기 기능
- [x] **Supabase favorites 테이블 확인**
  - [x] 테이블 스키마 확인: `user_id` (uuid, PK), `kakao_place_id` (text, PK) - 복합 기본키
  - [x] 추가 필드: `alert_threshold_db` (numeric, nullable), `created_at` (timestamptz)
  - [x] RLS (Row Level Security) 정책 설정 (supabase_rls_policies.sql 참고)

- [x] **PlaceDetailActivity에 즐겨찾기 버튼 추가**
  - [x] 하트 아이콘 버튼
  - [x] 현재 장소가 즐겨찾기인지 확인
  - [x] 추가/제거 토글 기능 (Supabase favorites 연동)
  - [x] UI 상태 업데이트 (채워진/빈 하트)
  - [x] profiles 테이블 자동 생성 (즐겨찾기 추가 시)

- [x] **MyFavoritesActivity 구현**
  - [x] 즐겨찾기한 장소 목록 표시
  - [x] Supabase에서 현재 사용자의 favorites 조회
  - [x] 장소 정보와 평균 소음 수준 표시
  - [x] 클릭 시 PlaceDetailActivity로 이동
  - [x] 즐겨찾기 해제 기능

- [ ] **MainActivity에서 즐겨찾기 마커 표시**
  - [ ] 즐겨찾기한 장소는 별도 마커 스타일 적용

#### 5. 현재 위치 기반 검색
- [x] **위치 권한 요청 및 처리**
  - [x] ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION 권한 요청
  - [x] 권한 거부 시 처리 (Toast 메시지)

- [x] **FusedLocationProviderClient로 현재 위치 가져오기**
  - [x] MainActivity에서 현재 위치 획득
  - [x] 지도 초기 위치를 현재 위치로 설정 (moveToCurrentLocation 함수)
  - [x] 카테고리 검색 시 현재 위치 기준 반경 검색 (radius: 1000m)

- [x] **현재 위치 마커 표시**
  - [x] 파란색 점으로 현재 위치 표시
  - [x] 위치 업데이트 시 마커 이동

#### 6. 장소 상세 페이지 UI 구현 (Figma: PlaceDetailPage)
- [x] **장소 정보 표시**
  - [x] 장소 이름 헤더 (툴바에 표시)
  - [x] 주소 표시
  - [x] 현재 평균 dB 값 표시
  - [x] 소음 레벨 배지 표시 (Optimal, Good, Normal, Loud) - 색상별 스타일
  - [x] 뒤로가기 버튼
  - [x] 즐겨찾기 버튼 (하트 아이콘)

- [x] **리뷰 목록 표시**
  - [x] Supabase에서 리뷰 조회
  - [x] 리뷰 목록 RecyclerView 표시
  - [x] 평균 평점 표시
  - [x] 리뷰 개수 표시
  - [x] 빈 리뷰 목록 처리

- [ ] **Noise Trend Today 그래프 구현**
  - [ ] 시간대별 소음 수준 그래프/차트 표시
  - [ ] 시간 라벨 (9:00, 10:00, 11:00, 12:00, 13:00, 14:00 등)
  - [ ] 해당 장소의 시간대별 평균 dB 데이터 조회
  - [ ] 그래프 시각화 (라인 차트 또는 바 차트)

#### 7. 리뷰 이미지 업로드
- [ ] **이미지 선택 기능**
  - [ ] 갤러리에서 이미지 선택 (Intent.ACTION_PICK)
  - [ ] 카메라로 사진 촬영 (선택사항)
  - [ ] 이미지 미리보기 표시

- [ ] **Supabase Storage에 이미지 업로드**
  - [ ] Storage 버킷 생성 (`review-images`)
  - [ ] 이미지 파일 업로드
  - [ ] 업로드된 이미지 URL 받아오기
  - [ ] 리뷰 저장 시 이미지 URL 배열을 JSONB 형식으로 저장: `["url1", "url2"]`

- [ ] **리뷰에서 이미지 표시**
  - [ ] ReviewAdapter에서 이미지 표시
  - [ ] Glide 또는 Coil 라이브러리로 이미지 로드
  - [ ] 이미지 클릭 시 확대 보기

---

### 🟢 **P2 - 개선 기능**

#### 8. 프로필 페이지 구현 (Figma: ProfilePage)
- [x] **프로필 기본 기능 구현**
  - [x] 로그인/회원가입 기능 (Supabase Auth 사용)
  - [x] 로그아웃 기능 (Sign Out)
  - [x] 로그인 상태에 따른 UI 전환
  - [x] 사용자 정보 표시 (이메일, 이름)
  - [x] 하단 네비게이션 바

- [x] **프로필 메뉴 항목 구현**
  - [x] Settings 메뉴 항목 (설정 화면으로 이동)
  - [x] Notification History 메뉴 항목 (알림 히스토리 화면으로 이동)
  - [ ] Reviews 메뉴 항목 (내 리뷰로 이동) - 네비게이션 바로 대체됨
  - [ ] Optimal Spots 메뉴 항목 (최적 장소 목록)

- [ ] **Optimal Spots 화면 구현**
  - [ ] Optimal 레벨(30-45 dB) 장소 목록 표시
  - [ ] Supabase에서 Optimal 레벨 장소 조회
  - [ ] 장소별 평균 dB 표시
  - [ ] 클릭 시 PlaceDetailActivity로 이동

#### 9. 알림 추천 시스템
- [ ] **알림 권한 요청**
  - [ ] Android 13+ POST_NOTIFICATIONS 권한

- [ ] **조용한 장소 추천 알림**
  - [ ] 사용자 위치 기반 주변 조용한 장소 탐지
  - [ ] 백그라운드 작업 (WorkManager)
  - [ ] 알림 발송 로직

- [ ] **Noise Threshold Alert 구현** (Figma 디자인 반영)
  - [ ] 소음 임계값 설정 기능
  - [ ] 임계값 초과 시 알림 발송
  - [ ] 알림 UI 디자인 구현

- [ ] **NotificationHistoryActivity 구현**
  - [ ] 알림 히스토리 목록 표시
  - [ ] 로컬 데이터베이스 또는 Supabase에 알림 기록 저장

#### 10. 검색 기능 개선
- [x] **검색 입력 필드 구현** (Figma: "Search for Place Name")
  - [x] 검색 아이콘 버튼
  - [x] 검색어 입력 기능
  - [x] 키워드 검색 기능 (카카오 로컬 API 사용)
  - [x] 검색 결과 목록 표시 (RecyclerView)
  - [x] 검색 결과 클릭 시 장소 상세 화면으로 이동
  - [x] 카테고리 검색 기능 (Restaurants, Cafes, Bars)

- [ ] **검색 결과 캐싱**
  - [ ] 최근 검색어 저장 (SharedPreferences)
  - [ ] 검색 결과 메모리 캐싱

- [ ] **자동완성 기능**
  - [ ] 검색어 입력 중 자동완성 제안
  - [ ] 최근 검색어 표시

#### 11. 성능 최적화
- [ ] **이미지 로딩 최적화**
  - [ ] 이미지 캐싱 전략
  - [ ] 썸네일 생성 및 사용

- [ ] **네트워크 요청 최적화**
  - [ ] 리뷰 데이터 페이징 (페이지네이션)
  - [ ] 불필요한 API 호출 최소화
  - [ ] 에러 재시도 로직

- [ ] **지도 렌더링 최적화**
  - [ ] 마커 클러스터링 (많은 마커가 있을 때)
  - [ ] 뷰포트 기반 데이터 로딩

#### 12. UI/UX 개선
- [ ] **로딩 상태 개선**
  - [ ] Skeleton UI 적용
  - [ ] Pull-to-refresh 기능

- [ ] **에러 처리 개선**
  - [ ] 네트워크 에러 시 재시도 버튼
  - [ ] 사용자 친화적인 에러 메시지
  - [ ] 오프라인 모드 처리

- [ ] **애니메이션 추가**
  - [ ] 화면 전환 애니메이션
  - [ ] 리스트 아이템 애니메이션
  - [ ] 로딩 애니메이션

---

### 🔵 **P3 - 추가 기능**

#### 13. 네비게이션 바 구현 (Figma 디자인 반영)
- [x] **하단 네비게이션 바 UI**
  - [x] Map 탭 (활성 상태: 파란색, 비활성: 회색)
  - [x] My Reviews 탭
  - [x] My Favorite Zones 탭
  - [x] Profile 탭
  - [x] 각 탭 아이콘 및 텍스트 표시
  - [x] 탭 클릭 시 해당 화면으로 이동
  - [x] 모든 Activity에 네비게이션 바 구현 (MainActivity, MyReviewsActivity, MyFavoritesActivity, ProfileActivity)

#### 14. 소음 측정 개선
- [x] **소음 측정 기본 기능**
  - [x] AudioRecord를 사용한 실시간 소음 측정
  - [x] dB 값 계산 (20 * log10(amplitude))
  - [x] 50ms마다 UI 업데이트
  - [x] 측정 시작/중지 기능
  - [x] 측정된 최종 dB 값 저장

- [ ] **dB 측정 정확도 향상**
  - [ ] 마이크 캘리브레이션
  - [ ] A-weighting 필터 적용
  - [ ] 평균값 계산 (단순 최대값 대신)

- [ ] **측정 기록 저장**
  - [ ] 측정 중간값들을 그래프로 표시
  - [ ] 측정 시간대 정보 저장

#### 15. 소셜 기능
- [ ] **리뷰 좋아요 기능**
  - [ ] 리뷰에 좋아요 버튼 추가
  - [ ] 좋아요 수 표시

- [ ] **리뷰 댓글 기능** (선택사항)
  - [ ] 댓글 작성 및 표시

#### 16. 통계 및 분석
- [ ] **사용자 통계 화면**
  - [ ] 작성한 리뷰 수
  - [ ] 방문한 장소 수
  - [ ] 평균 소음 수준 선호도

- [ ] **장소 통계 개선**
  - [ ] 시간대별 소음 수준 그래프
  - [ ] 요일별 소음 수준 비교

#### 17. 설정 기능
- [ ] **SettingsActivity 실제 구현** (Figma: ProfilePage의 Settings)
  - [ ] 언어 변경 (한국어/영어)
  - [ ] 다크 모드 토글
  - [ ] 알림 설정
  - [ ] 소음 임계값 설정 (Noise Threshold)
  - [ ] 약관 및 개인정보처리방침 링크

---

## 🧪 테스트 체크리스트

### 단위 테스트
- [ ] Review 데이터 모델 테스트
- [ ] dB 계산 로직 테스트
- [ ] 소음 수준 분류 로직 테스트

### 통합 테스트
- [ ] Supabase 연동 테스트
- [ ] 리뷰 CRUD 테스트
- [ ] 인증 플로우 테스트

### UI 테스트
- [ ] 주요 화면 전환 테스트
- [ ] 리뷰 작성 플로우 테스트
- [ ] 지도 인터랙션 테스트

---

## 🐛 버그 수정 및 개선

### 알려진 이슈
- [x] NewReviewActivity의 DUMMY_USER_ID, DUMMY_PLACE_ID 제거 ✅
- [x] MyReviewsActivity의 더미 데이터 제거 (Supabase 연동 완료) ✅
- [x] MainActivity의 샘플 데이터 제거 (Supabase 리뷰 기반 마커로 대체) ✅
- [x] 필터 드롭다운 실제 필터링 로직 구현 ✅
- [x] ReviewDto 자료형 통일 (id: Long, amenities 제거) ✅
- [x] FavoriteDto 필드명 수정 (kakao_place_id로 통일) ✅
- [x] PlaceInsertDto, PlaceDto 데이터 클래스 추가 (직렬화 오류 해결) ✅
- [x] RLS 정책 설정 가이드 추가 (supabase_rls_policies.sql) ✅

### 코드 품질
- [ ] 코드 리뷰 및 리팩토링
- [ ] 주석 및 문서화
- [ ] 로그 레벨 정리 (디버그 로그 제거)

---

## 📦 배포 준비

### 빌드 설정
- [ ] ProGuard/R8 규칙 설정
- [ ] 릴리즈 키스토어 생성
- [ ] BuildConfig 변수 검증

### 문서화
- [ ] README.md 업데이트
- [ ] API 문서 작성
- [ ] 사용자 가이드 작성

### 스토어 준비
- [ ] 앱 아이콘 및 스플래시 화면
- [ ] 스크린샷 준비
- [ ] 앱 설명 작성
- [ ] 개인정보처리방침 페이지

---

## 📝 참고사항

### Supabase 테이블 스키마 (실제 구조)

```sql
-- places 테이블 (장소 정보)
CREATE TABLE places (
  kakao_place_id TEXT PRIMARY KEY,
  name TEXT,
  address TEXT,
  lat FLOAT8,
  lng FLOAT8,
  noise_level_db NUMERIC,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

-- reviews 테이블 (리뷰)
CREATE TABLE reviews (
  id BIGSERIAL PRIMARY KEY,
  kakao_place_id TEXT NOT NULL REFERENCES places(kakao_place_id),
  user_id UUID NOT NULL REFERENCES auth.users(id),
  rating SMALLINT NOT NULL,
  text TEXT,
  images JSONB, -- 이미지 URL 배열 (JSON 형식)
  noise_level_db NUMERIC NOT NULL,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

-- favorites 테이블 (즐겨찾기)
CREATE TABLE favorites (
  user_id UUID NOT NULL REFERENCES auth.users(id),
  kakao_place_id TEXT NOT NULL REFERENCES places(kakao_place_id),
  alert_threshold_db NUMERIC,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  PRIMARY KEY (user_id, kakao_place_id)
);

-- profiles 테이블 (사용자 프로필)
CREATE TABLE profiles (
  id UUID PRIMARY KEY REFERENCES auth.users(id),
  nickname TEXT,
  avatar_url TEXT,
  created_at TIMESTAMPTZ DEFAULT NOW()
);
```

### ⚠️ 주의사항

1. **`reviews.amenities` 필드 없음**: 
   - 데이터베이스에 `amenities` 컬럼이 존재하지 않음
   - 리뷰 저장 시 편의시설 정보는 제외됨

2. **`reviews.images` 타입**: 
   - `jsonb` 타입으로 저장됨
   - 향후 이미지 업로드 기능 구현 시 JSON 배열 형식으로 저장: `["url1", "url2"]`

3. **`favorites` 테이블**: 
   - 컬럼명: `kakao_place_id` (수정 완료)
   - `alert_threshold_db`: 알림 임계값 (선택 필드)
   - `created_at`: 생성 시간 (자동 설정)

4. **`places` 테이블**: 
   - 장소 정보를 별도 테이블로 관리
   - 리뷰 작성 시 자동으로 생성됨 (upsert)

---

### 📊 현재 진행 상황 요약

**✅ 완료된 주요 기능:**
- 리뷰 작성/수정/삭제 (Supabase 연동)
- 지도 마커 표시 (리뷰 데이터 기반)
- 필터링 기능 (소음 수준별)
- 즐겨찾기 기능 (추가/제거/목록)
- 검색 기능 (키워드, 카테고리)
- 현재 위치 기반 검색
- 프로필 로그인/회원가입
- 네비게이션 바 (모든 Activity)
- 개발 모드 (데시벨 직접 입력)

**🔄 진행 중/예정:**
- MainActivity에서 즐겨찾기 마커 표시
- Noise Trend Today 그래프
- 리뷰 이미지 업로드
- 알림 추천 시스템

---

**마지막 업데이트**: 2025-11-21
**프로젝트**: SilentZoneFinder Android
**Figma 디자인**: https://www.figma.com/design/F5LHvHsGsOpHQRcn1SabmC/silentZone

---

## 📚 관련 문서

- `README.md` - 프로젝트 설정 및 시작 가이드
- `SUPABASE_RLS_SETUP.md` - RLS 정책 설정 가이드
- `supabase_rls_policies.sql` - RLS 정책 SQL 쿼리
- `LOGCAT_FILTER_GUIDE.md` - Logcat 필터링 가이드

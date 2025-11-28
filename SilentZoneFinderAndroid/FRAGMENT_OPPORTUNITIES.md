# 🎯 Fragment로 분리할 수 있는 요소들

네비게이션 바는 유지하고, Fragment를 추가할만한 부분들을 정리했습니다.

## ✅ 추천: DialogFragment로 변경

### 1. **이미지 확대 보기 Dialog** ⭐⭐⭐ (높은 우선순위)
**현재 위치**: `ReviewAdapter.kt`의 `showZoomDialog()`
**문제점**: 
- Dialog로 구현되어 있어 생명주기 관리가 어려움
- 재사용 불가능

**변경 후**:
```kotlin
class ImageZoomDialogFragment : DialogFragment() {
    companion object {
        fun newInstance(imageUrl: String) = ImageZoomDialogFragment().apply {
            arguments = bundleOf("imageUrl" to imageUrl)
        }
    }
}
```
**장점**: 
- 생명주기 자동 관리
- 재사용 가능
- 화면 회전 시에도 안전

---

### 2. **이미지 소스 선택 Dialog** ⭐⭐⭐ (높은 우선순위)
**현재 위치**: 
- `ProfileActivity.kt`의 `showImageSourceDialog()`
- `NewReviewActivity.kt`의 `showImageSourceDialog()`

**문제점**: 
- 코드 중복 (2곳에서 동일한 로직)
- 재사용 불가능

**변경 후**:
```kotlin
class ImageSourceDialogFragment : DialogFragment() {
    interface OnSourceSelectedListener {
        fun onCameraSelected()
        fun onGallerySelected()
    }
}
```
**장점**: 
- 코드 중복 제거
- 여러 곳에서 재사용 가능
- 테스트 용이

---

### 3. **삭제 확인 Dialog** ⭐⭐ (중간 우선순위)
**현재 위치**: 
- `MyReviewsActivity.kt`의 `confirmDeleteReview()`
- `MyFavoritesActivity.kt`의 삭제 확인
- `SearchHistoryActivity.kt`의 전체 삭제 확인

**변경 후**:
```kotlin
class ConfirmDeleteDialogFragment : DialogFragment() {
    companion object {
        fun newInstance(
            title: String,
            message: String
        ) = ConfirmDeleteDialogFragment().apply {
            arguments = bundleOf(
                "title" to title,
                "message" to message
            )
        }
    }
}
```
**장점**: 
- 일관된 UI/UX
- 코드 중복 제거

---

## ✅ 추천: BottomSheetDialogFragment

### 4. **필터 선택 Bottom Sheet** ⭐⭐ (중간 우선순위)
**현재 위치**: `MyReviewsActivity.kt`의 `showFilterMenu()` (PopupMenu 사용)

**변경 후**:
```kotlin
class ReviewFilterBottomSheetFragment : BottomSheetDialogFragment() {
    // All Reviews, Library Quiet, Quiet Conversation, Lively Chatter, High Traffic
}
```
**장점**: 
- Material Design 가이드라인 준수
- 더 나은 UX (더 큰 터치 영역)
- 애니메이션 지원

---

### 5. **정렬 옵션 Bottom Sheet** ⭐ (낮은 우선순위)
**현재 위치**: `MyReviewsActivity.kt`의 정렬 기능

**변경 후**:
```kotlin
class SortOptionsBottomSheetFragment : BottomSheetDialogFragment() {
    // Most Recent, Highest Rating, Optimal to Loud
}
```

---

## ✅ 추천: 재사용 가능한 Fragment

### 6. **프로필 로그인 Fragment** ⭐⭐ (중간 우선순위)
**현재 위치**: `ProfileActivity.kt`의 로그인 레이아웃

**변경 후**:
```kotlin
class LoginFragment : Fragment() {
    // 로그인/회원가입 UI
    // 다른 Activity에서도 재사용 가능
}
```
**장점**: 
- LoginActivity와 중복 제거 가능
- 재사용 가능

---

### 7. **프로필 로그인된 상태 Fragment** ⭐ (낮은 우선순위)
**현재 위치**: `ProfileActivity.kt`의 `loggedInLayout`

**변경 후**:
```kotlin
class ProfileContentFragment : Fragment() {
    // 프로필 카드, 알림 설정, 메뉴 등
}
```

---

## ✅ 추천: Settings 화면 구성

### 8. **Settings 화면을 여러 Fragment로 구성** ⭐⭐ (중간 우선순위)
**현재 상태**: `SettingsActivity`가 거의 비어있음

**변경 후**:
```kotlin
// SettingsActivity에 ViewPager2 + Fragment
- GeneralSettingsFragment (일반 설정)
- NotificationSettingsFragment (알림 설정)
- AboutFragment (약관, 개인정보처리방침)
```

**장점**: 
- 탭으로 구성 가능
- 각 설정 섹션을 독립적으로 관리
- 확장성 좋음

---

## 📊 우선순위별 정리

### 🔴 High Priority (즉시 적용 가능)
1. **이미지 확대 보기 DialogFragment** - 재사용성 높음
2. **이미지 소스 선택 DialogFragment** - 코드 중복 제거

### 🟡 Medium Priority (점진적 적용)
3. **삭제 확인 DialogFragment** - 일관성 향상
4. **필터 Bottom Sheet** - UX 개선
5. **Settings 화면 Fragment 구성** - 확장성

### 🟢 Low Priority (선택사항)
6. **프로필 Fragment 분리** - 재사용성
7. **정렬 옵션 Bottom Sheet** - UX 개선

---

## 💡 구현 예시

### 가장 간단한 예시: 이미지 확대 보기

```kotlin
class ImageZoomDialogFragment : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val imageUrl = arguments?.getString("imageUrl") ?: return super.onCreateDialog(savedInstanceState)
        
        val imageView = ImageView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
            load(imageUrl) { crossfade(true) }
            setOnClickListener { dismiss() }
        }
        
        return Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen).apply {
            setContentView(imageView)
        }
    }
    
    companion object {
        fun newInstance(imageUrl: String) = ImageZoomDialogFragment().apply {
            arguments = bundleOf("imageUrl" to imageUrl)
        }
    }
}

// 사용법
ImageZoomDialogFragment.newInstance(imageUrl).show(supportFragmentManager, "image_zoom")
```

---

## 🎯 결론

**가장 먼저 추천하는 것들:**
1. **이미지 확대 보기 DialogFragment** - 가장 간단하고 효과적
2. **이미지 소스 선택 DialogFragment** - 코드 중복 제거

이 두 가지만 먼저 적용해도 충분히 Fragment의 이점을 경험할 수 있습니다!


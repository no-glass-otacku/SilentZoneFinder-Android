# Supabase RLS (Row Level Security) 설정 가이드

## 🔒 문제 상황

`places` 테이블에 데이터를 삽입할 때 다음 오류가 발생합니다:
```
new row violates row-level security policy for table "places"
```

이는 Supabase의 Row Level Security (RLS) 정책이 설정되어 있지만, 인증된 사용자가 `places` 테이블에 삽입할 수 있는 정책이 없기 때문입니다.

## ✅ 해결 방법

### 1. Supabase 대시보드 접속

1. [Supabase 대시보드](https://app.supabase.com)에 로그인
2. 프로젝트 선택
3. 왼쪽 메뉴에서 **"Authentication"** > **"Policies"** 클릭
   또는 **"Table Editor"** > `places` 테이블 선택 > **"Policies"** 탭

### 2. `places` 테이블 RLS 정책 설정

#### 방법 1: 인증된 사용자 모두 허용 (개발용 - 간단)

```sql
-- 인증된 사용자는 places 테이블에 INSERT 가능
CREATE POLICY "Allow authenticated users to insert places"
ON places
FOR INSERT
TO authenticated
WITH CHECK (true);

-- 인증된 사용자는 places 테이블을 SELECT 가능
CREATE POLICY "Allow authenticated users to select places"
ON places
FOR SELECT
TO authenticated
USING (true);

-- 인증된 사용자는 places 테이블을 UPDATE 가능
CREATE POLICY "Allow authenticated users to update places"
ON places
FOR UPDATE
TO authenticated
USING (true);
```

#### 방법 2: 모든 사용자 허용 (공개 데이터 - 프로덕션 권장)

`places` 테이블은 공개 데이터이므로 모든 사용자(익명 포함)가 읽을 수 있어야 합니다:

```sql
-- 모든 사용자는 places 테이블을 SELECT 가능
CREATE POLICY "Allow public read access to places"
ON places
FOR SELECT
TO public
USING (true);

-- 인증된 사용자는 places 테이블에 INSERT 가능
CREATE POLICY "Allow authenticated users to insert places"
ON places
FOR INSERT
TO authenticated
WITH CHECK (true);

-- 인증된 사용자는 places 테이블을 UPDATE 가능
CREATE POLICY "Allow authenticated users to update places"
ON places
FOR UPDATE
TO authenticated
USING (true);
```

### 3. SQL Editor에서 직접 실행

1. Supabase 대시보드에서 **"SQL Editor"** 클릭
2. 위의 SQL 문 중 하나를 선택하여 실행
3. **"Run"** 버튼 클릭

### 4. 정책 확인

설정 후 다음을 확인하세요:

1. **"Table Editor"** > `places` 테이블 선택
2. **"Policies"** 탭에서 정책 목록 확인
3. 다음 정책들이 있는지 확인:
   - `Allow authenticated users to insert places` (또는 유사한 이름)
   - `Allow public read access to places` (또는 유사한 이름)

## 📋 다른 테이블 RLS 정책 확인

다음 테이블들도 RLS 정책이 올바르게 설정되어 있는지 확인하세요:

### `reviews` 테이블
```sql
-- 모든 사용자는 리뷰를 읽을 수 있음
CREATE POLICY "Allow public read access to reviews"
ON reviews
FOR SELECT
TO public
USING (true);

-- 인증된 사용자는 자신의 리뷰만 INSERT 가능
CREATE POLICY "Allow users to insert their own reviews"
ON reviews
FOR INSERT
TO authenticated
WITH CHECK (auth.uid() = user_id);

-- 인증된 사용자는 자신의 리뷰만 UPDATE 가능
CREATE POLICY "Allow users to update their own reviews"
ON reviews
FOR UPDATE
TO authenticated
USING (auth.uid()::text = user_id);

-- 인증된 사용자는 자신의 리뷰만 DELETE 가능
CREATE POLICY "Allow users to delete their own reviews"
ON reviews
FOR DELETE
TO authenticated
USING (auth.uid()::text = user_id);
```

### `favorites` 테이블
```sql
-- 인증된 사용자는 자신의 즐겨찾기만 SELECT 가능
CREATE POLICY "Allow users to select their own favorites"
ON favorites
FOR SELECT
TO authenticated
USING (auth.uid()::text = user_id);

-- 인증된 사용자는 자신의 즐겨찾기만 INSERT 가능
CREATE POLICY "Allow users to insert their own favorites"
ON favorites
FOR INSERT
TO authenticated
WITH CHECK (auth.uid()::text = user_id);

-- 인증된 사용자는 자신의 즐겨찾기만 DELETE 가능
CREATE POLICY "Allow users to delete their own favorites"
ON favorites
FOR DELETE
TO authenticated
USING (auth.uid()::text = user_id);
```

### `profiles` 테이블
```sql
-- 모든 사용자는 프로필을 읽을 수 있음
CREATE POLICY "Allow public read access to profiles"
ON profiles
FOR SELECT
TO public
USING (true);

-- 인증된 사용자는 자신의 프로필만 INSERT 가능
CREATE POLICY "Allow users to insert their own profile"
ON profiles
FOR INSERT
TO authenticated
WITH CHECK (auth.uid()::text = id);

-- 인증된 사용자는 자신의 프로필만 UPDATE 가능
CREATE POLICY "Allow users to update their own profile"
ON profiles
FOR UPDATE
TO authenticated
USING (auth.uid()::text = id);
```

## ⚠️ 주의사항

1. **RLS 활성화 확인**: 테이블에 RLS가 활성화되어 있는지 확인하세요
   - Table Editor > 테이블 선택 > Settings > "Enable RLS" 체크

2. **정책 순서**: 정책은 위에서 아래로 평가되므로, 더 구체적인 정책을 먼저 배치하세요

3. **테스트**: 정책 설정 후 앱에서 다시 테스트하여 정상 작동하는지 확인하세요

## 🔍 문제 해결

정책을 설정했는데도 여전히 오류가 발생하면:

1. **정책이 올바르게 생성되었는지 확인**
   - Policies 탭에서 정책 목록 확인
   - 정책 이름과 조건 확인

2. **RLS가 활성화되어 있는지 확인**
   - Settings에서 "Enable RLS" 체크 확인

3. **사용자 인증 상태 확인**
   - 앱에서 로그인이 제대로 되어 있는지 확인
   - JWT 토큰이 유효한지 확인

4. **로그 확인**
   - Supabase 대시보드 > Logs에서 상세 오류 확인





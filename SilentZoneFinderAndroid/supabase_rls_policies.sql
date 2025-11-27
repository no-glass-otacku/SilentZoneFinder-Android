-- ============================================
-- Supabase RLS (Row Level Security) 정책 설정
-- ============================================
-- 이 파일의 SQL 쿼리를 Supabase SQL Editor에서 실행하세요.
-- 실행 순서: 위에서 아래로 순차적으로 실행

-- ============================================
-- 1. places 테이블 정책
-- ============================================

-- 모든 사용자(익명 포함)가 places 테이블을 읽을 수 있음 (공개 데이터)
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

-- ============================================
-- 2. reviews 테이블 정책
-- ============================================

-- 모든 사용자(익명 포함)가 reviews 테이블을 읽을 수 있음 (공개 데이터)
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
USING (auth.uid() = user_id);

-- 인증된 사용자는 자신의 리뷰만 DELETE 가능
CREATE POLICY "Allow users to delete their own reviews"
ON reviews
FOR DELETE
TO authenticated
USING (auth.uid() = user_id);

-- ============================================
-- 3. favorites 테이블 정책
-- ============================================

-- 인증된 사용자는 자신의 즐겨찾기만 SELECT 가능
CREATE POLICY "Allow users to select their own favorites"
ON favorites
FOR SELECT
TO authenticated
USING (auth.uid() = user_id);

-- 인증된 사용자는 자신의 즐겨찾기만 INSERT 가능
CREATE POLICY "Allow users to insert their own favorites"
ON favorites
FOR INSERT
TO authenticated
WITH CHECK (auth.uid() = user_id);

-- 인증된 사용자는 자신의 즐겨찾기만 UPDATE 가능
CREATE POLICY "Allow users to update their own favorites"
ON favorites
FOR UPDATE
TO authenticated
USING (auth.uid() = user_id);

-- 인증된 사용자는 자신의 즐겨찾기만 DELETE 가능
CREATE POLICY "Allow users to delete their own favorites"
ON favorites
FOR DELETE
TO authenticated
USING (auth.uid() = user_id);

-- ============================================
-- 4. profiles 테이블 정책
-- ============================================

-- 모든 사용자(익명 포함)가 profiles 테이블을 읽을 수 있음 (공개 프로필)
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
WITH CHECK (auth.uid() = id);

-- 인증된 사용자는 자신의 프로필만 UPDATE 가능
CREATE POLICY "Allow users to update their own profile"
ON profiles
FOR UPDATE
TO authenticated
USING (auth.uid() = id);

-- ============================================
-- 참고사항
-- ============================================
-- 1. 각 테이블에 RLS가 활성화되어 있어야 합니다.
--    - Table Editor > 테이블 선택 > Settings > "Enable RLS" 체크
--
-- 2. 정책이 이미 존재하는 경우 오류가 발생할 수 있습니다.
--    - 기존 정책을 삭제하거나 DROP POLICY 문을 사용하세요.
--    - 예: DROP POLICY IF EXISTS "Allow public read access to places" ON places;
--
-- 3. 정책을 수정하려면:
--    - DROP POLICY로 기존 정책 삭제
--    - CREATE POLICY로 새 정책 생성
--
-- 4. 정책 확인:
--    - Table Editor > 테이블 선택 > Policies 탭에서 확인 가능


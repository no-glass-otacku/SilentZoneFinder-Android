import { serve } from "https://deno.land/std@0.177.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import * as jose from "https://deno.land/x/jose@v4.11.2/index.ts";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const SERVICE_ACCOUNT_JSON = Deno.env.get("FCM_SERVICE_ACCOUNT_JSON")!;

const supabase = createClient(SUPABASE_URL, SERVICE_ROLE_KEY);

// 서비스 계정 정보
const serviceAccount = JSON.parse(SERVICE_ACCOUNT_JSON);
const FCM_PROJECT_ID: string = serviceAccount.project_id;

// reviews 레코드 타입
interface ReviewRecord {
  id: number;
  kakao_place_id: string;
  user_id: string;
  rating: number;
  text: string | null;
  noise_level_db: number;
  created_at: string;
}

// FCM HTTP v1용 access token 발급
async function getAccessToken(): Promise<string> {
  const now = Math.floor(Date.now() / 1000);

  const privateKey = await jose.importPKCS8(
    serviceAccount.private_key,
    "RS256",
  );

  const jwt = await new jose.SignJWT({
    iss: serviceAccount.client_email,
    sub: serviceAccount.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  })
    .setProtectedHeader({ alg: "RS256", typ: "JWT" })
    .sign(privateKey);

  const body = new URLSearchParams({
    grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
    assertion: jwt,
  });

  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body,
  });

  if (!res.ok) {
    const text = await res.text();
    console.error("FCM token error:", res.status, text);
    throw new Error("Failed to get FCM access token");
  }

  const json = await res.json();
  return json.access_token as string;
}

// FCM v1 호출
async function sendFcmToTokens(
  tokens: string[],
  record: ReviewRecord,
  accessToken: string,
) {
  const url =
    `https://fcm.googleapis.com/v1/projects/${FCM_PROJECT_ID}/messages:send`;

  console.log(
    "sendFcmToTokens: will send to",
    tokens.length,
    "tokens",
  );



  for (const token of tokens) {
    const shortToken = token.slice(0, 16) + "...";
    console.log("sendFcmToTokens: sending to token =", shortToken);

    const payload = {
      message: {
        token,
        ...baseMessage,
        data: {
          review_id: String(record.id),
          kakao_place_id: record.kakao_place_id,
        },
      },
    };

    const res = await fetch(url, {
      method: "POST",
      headers: {
        "Content-Type": "application/json; charset=utf-8",
        Authorization: `Bearer ${accessToken}`,
      },
      body: JSON.stringify(payload),
    });

    const text = await res.text();
    console.log(
      "sendFcmToTokens: FCM response for",
      shortToken,
      "status =",
      res.status,
      "body =",
      text,
    );

    if (!res.ok) {
      console.error(
        "sendFcmToTokens: FCM send error for",
        shortToken,
        "status =",
        res.status,
      );
    }
  }
}

serve(async (req: Request) => {
  if (req.method !== "POST") {
    return new Response("Method Not Allowed", { status: 405 });
  }

  // 1) JSON 파싱
  let body: any;
  try {
    body = await req.json();
  } catch {
    return new Response("Invalid JSON", { status: 400 });
  }

  // 2) review_id 기반으로 DB에서 리뷰 조회
  const rawReviewId = body.review_id ?? null;
  const reviewId = rawReviewId !== null ? Number(rawReviewId) : NaN;

  if (!Number.isFinite(reviewId)) {
    console.error("invalid review_id:", rawReviewId);
    return new Response("review_id required", { status: 400 });
  }

  console.log("notify_quiet_review called with review_id =", reviewId);

  const { data: review, error: reviewError } = await supabase
    .from("reviews")
    .select("*")
    .eq("id", reviewId)
    .single<ReviewRecord>();

  if (reviewError || !review) {
    console.error("review select error", reviewError);
    return new Response("review not found", { status: 404 });
  }

  const record: ReviewRecord = review;

  if (!record.kakao_place_id || typeof record.noise_level_db !== "number") {
    console.error("invalid record payload", record);
    return new Response("invalid record", { status: 400 });
  }

  console.log(
    "review info:",
    "kakao_place_id =",
    record.kakao_place_id,
    "noise_level_db =",
    record.noise_level_db,
  );

  // 3) 즐겨찾기 + threshold 조건 만족 사용자 찾기
  const { data: favorites, error: favErr } = await supabase
    .from("favorites")
    .select("user_id, alert_threshold_db")
    .eq("kakao_place_id", record.kakao_place_id)
    .gte("alert_threshold_db", record.noise_level_db);

  if (favErr) {
    console.error("favorites select error", favErr);
    return new Response("db error", { status: 500 });
  }

  if (!favorites || favorites.length === 0) {
    console.log("no favorites match threshold");
    return new Response("no subscribers", { status: 200 });
  }

  console.log("favorites matched:", favorites.length);

  const favUserIds = Array.from(
    new Set(favorites.map((f: any) => f.user_id as string)),
  );

  // 4) 알림 ON인 사용자만 필터링
  const { data: notifRows, error: notifErr } = await supabase
    .from("place_notifications")
    .select("user_id")
    .eq("kakao_place_id", record.kakao_place_id)
    .eq("is_enabled", true)
    .in("user_id", favUserIds);

  if (notifErr) {
    console.error("place_notifications select error", notifErr);
    return new Response("db error", { status: 500 });
  }

  if (!notifRows || notifRows.length === 0) {
    console.log("no enabled notifications for this place");
    return new Response("no subscribers", { status: 200 });
  }

  console.log("enabled notifications users:", notifRows.length);

  const notifyUserIds = Array.from(
    new Set(notifRows.map((n: any) => n.user_id as string)),
  );

  // 5) 디바이스 토큰 조회
  const { data: devices, error: devErr } = await supabase
    .from("user_devices")
    .select("fcm_token, user_id")
    .in("user_id", notifyUserIds);

  if (devErr) {
    console.error("user_devices select error", devErr);
    return new Response("db error", { status: 500 });
  }

  if (!devices || devices.length === 0) {
    console.log("no device tokens");
    return new Response("no tokens", { status: 200 });
  }

  console.log("user_devices rows:", devices.length);

  const tokens: string[] = Array.from(
    new Set(
      devices
        .map((d: any) => d.fcm_token as string | null)
        .filter((t: string | null): t is string => t !== null && t !== ""),
    ),
  );

  console.log("deduped tokens length:", tokens.length);

  if (tokens.length === 0) {
    console.log("no tokens after dedupe");
    return new Response("no tokens", { status: 200 });
  }

  try {
    const accessToken = await getAccessToken();
    await sendFcmToTokens(tokens, record, accessToken);
  } catch (e) {
    console.error("fcm error:", e);
    return new Response("fcm error", { status: 500 });
  }

  return new Response("ok", { status: 200 });
});

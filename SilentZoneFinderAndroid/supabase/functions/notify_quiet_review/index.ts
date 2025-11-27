// supabase/functions/notify_quiet_review/index.ts

import { serve } from "https://deno.land/std@0.168.0/http/server.ts";

type QuietReviewPayload = {
  placeId: string;
  placeName: string;
  decibel: number;
  reviewSnippet?: string;
  testToken: string;  // ★ 요청에서 직접 받는 토큰
};

// ★ 여기 FCM 서버키를 직접 넣는다 (Android key 값 그대로)
const FCM_SERVER_KEY = "***REMOVED***CHuQ6Y2vp3Z8YRHDqs-zBntGCTo3LJ6Bg";
const FCM_ENDPOINT = "https://fcm.googleapis.com/fcm/send";

if (!FCM_SERVER_KEY) {
  console.log("FCM_SERVER_KEY is not set");
}

serve(async (req: Request) => {
  if (req.method !== "POST") {
    return new Response("Method Not Allowed", { status: 405 });
  }

  let body: QuietReviewPayload;

  try {
    body = await req.json();
  } catch (_) {
    return new Response("Invalid JSON", { status: 400 });
  }

  const { placeId, placeName, decibel, reviewSnippet, testToken } = body;

  if (!placeId || !placeName || typeof decibel !== "number" || !testToken) {
    return new Response("Missing required fields", { status: 400 });
  }

  const QUIET_THRESHOLD = 50;

  if (decibel > QUIET_THRESHOLD) {
    return new Response(
      JSON.stringify({
        ok: true,
        skipped: true,
        reason: "not quiet review",
      }),
      { headers: { "Content-Type": "application/json" } },
    );
  }

  const title = "새 조용한 리뷰가 등록됐어요";
  const bodyText =
    `${placeName}에 조용한 리뷰가 올라왔어요. (약 ${decibel.toFixed(1)} dB)`;

  const message = {
    to: testToken,   // ★ 여기서 body로 받은 토큰 사용
    notification: {
      title,
      body: bodyText,
    },
    data: {
      placeId,
      placeName,
      decibel: decibel.toString(),
      reviewSnippet: reviewSnippet ?? "",
    },
  };

  const fcmRes = await fetch(FCM_ENDPOINT, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `key=${FCM_SERVER_KEY}`,
    },
    body: JSON.stringify(message),
  });

  const fcmText = await fcmRes.text();

  if (!fcmRes.ok) {
    console.log("FCM error:", fcmRes.status, fcmText);
    return new Response(
      JSON.stringify({
        ok: false,
        status: fcmRes.status,
        fcmResponse: fcmText,
      }),
      {
        status: 500,
        headers: { "Content-Type": "application/json" },
      },
    );
  }

  return new Response(
    JSON.stringify({
      ok: true,
      fcmResponse: fcmText,
    }),
    {
      headers: { "Content-Type": "application/json" },
    },
  );
});

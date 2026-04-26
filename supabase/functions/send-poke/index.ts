// Edge Function: send-poke (no-auth version)
// Reçoit un x-user-id header, trouve l'autre profil, envoie une notif FCM.
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.4";

const COOLDOWN_REGULAR_SECONDS = 5;
const COOLDOWN_REPLY_SECONDS = 2;

const FCM_SERVICE_ACCOUNT = JSON.parse(Deno.env.get("FCM_SERVICE_ACCOUNT")!);
const FCM_PROJECT_ID = FCM_SERVICE_ACCOUNT.project_id;

async function getFcmAccessToken(): Promise<string> {
    const now = Math.floor(Date.now() / 1000);
    const header = { alg: "RS256", typ: "JWT" };
    const claim = {
        iss: FCM_SERVICE_ACCOUNT.client_email,
        scope: "https://www.googleapis.com/auth/firebase.messaging",
        aud: "https://oauth2.googleapis.com/token",
        iat: now,
        exp: now + 3600,
    };

    const enc = (o: unknown) =>
        btoa(JSON.stringify(o)).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
    const unsigned = `${enc(header)}.${enc(claim)}`;

    const pem = FCM_SERVICE_ACCOUNT.private_key
        .replace(/-----(BEGIN|END) PRIVATE KEY-----/g, "")
        .replace(/\s+/g, "");
    const der = Uint8Array.from(atob(pem), (c) => c.charCodeAt(0));
    const key = await crypto.subtle.importKey(
        "pkcs8", der,
        { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
        false, ["sign"],
    );
    const sig = await crypto.subtle.sign(
        "RSASSA-PKCS1-v1_5", key,
        new TextEncoder().encode(unsigned),
    );
    const sigB64 = btoa(String.fromCharCode(...new Uint8Array(sig)))
        .replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
    const jwt = `${unsigned}.${sigB64}`;

    const res = await fetch("https://oauth2.googleapis.com/token", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams({
            grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
            assertion: jwt,
        }),
    });
    if (!res.ok) throw new Error(`oauth failed: ${await res.text()}`);
    return (await res.json()).access_token;
}

Deno.serve(async (req) => {
    if (req.method === "OPTIONS") return new Response("ok", { status: 200 });
    if (req.method !== "POST") return new Response("method not allowed", { status: 405 });

    const senderId = req.headers.get("x-user-id");
    if (!senderId) return new Response("missing x-user-id", { status: 400 });

    const admin = createClient(
        Deno.env.get("SUPABASE_URL")!,
        Deno.env.get("SERVICE_ROLE_KEY")!,
    );

    const body = await req.json().catch(() => ({}));
    const message: string = body.message ?? "J'ai envie de toi";
    const isReply: boolean = body.is_reply === true;
    const cooldownSeconds = isReply ? COOLDOWN_REPLY_SECONDS : COOLDOWN_REGULAR_SECONDS;

    // Cooldown : dernier poke envoyé < cooldownSeconds ?
    const { data: last } = await admin
        .from("pokes").select("sent_at")
        .eq("sender_id", senderId).order("sent_at", { ascending: false }).limit(1).maybeSingle();
    if (last) {
        const elapsed = (Date.now() - new Date(last.sent_at).getTime()) / 1000;
        if (elapsed < cooldownSeconds) {
            return new Response(
                JSON.stringify({ error: "cooldown", retry_in: Math.ceil(cooldownSeconds - elapsed) }),
                { status: 429, headers: { "Content-Type": "application/json" } },
            );
        }
    }

    // L'autre profil = celui dont l'id != senderId
    const { data: partners, error: pErr } = await admin
        .from("profiles").select("id, fcm_token").neq("id", senderId);
    if (pErr || !partners || partners.length === 0) {
        return new Response(JSON.stringify({ error: "no partner found", detail: pErr?.message }), {
            status: 400, headers: { "Content-Type": "application/json" },
        });
    }
    const partner = partners[0];
    if (!partner.fcm_token) {
        return new Response(JSON.stringify({ error: "partner has no device token" }), {
            status: 400, headers: { "Content-Type": "application/json" },
        });
    }

    // Envoi FCM
    const accessToken = await getFcmAccessToken();
    const fcmRes = await fetch(
        `https://fcm.googleapis.com/v1/projects/${FCM_PROJECT_ID}/messages:send`,
        {
            method: "POST",
            headers: {
                Authorization: `Bearer ${accessToken}`,
                "Content-Type": "application/json",
            },
            body: JSON.stringify({
                message: {
                    token: partner.fcm_token,
                    // data-only : onMessageReceived est appelé même app killed,
                    // donc nos boutons de réponse personnalisés s'affichent toujours.
                    data: { title: "Dring 🔔", body: message },
                    android: { priority: "HIGH" },
                },
            }),
        },
    );
    if (!fcmRes.ok) {
        const txt = await fcmRes.text();
        return new Response(JSON.stringify({ error: "fcm failed", detail: txt }), {
            status: 502, headers: { "Content-Type": "application/json" },
        });
    }

    await admin.from("pokes").insert({
        sender_id: senderId, receiver_id: partner.id, message,
    });

    return new Response(JSON.stringify({ ok: true, cooldown_seconds: cooldownSeconds }), {
        headers: { "Content-Type": "application/json" },
    });
});

<div align="center">

<img src="app/src/main/res/drawable/ic_dring_logo.png" alt="Dring logo" width="160" />

# Dring

**Un tap, une notif. Pour les couples qui veulent rester connectés.**

</div>

---

## ✨ Concept

Dring est une mini-app Android conçue pour deux personnes — typiquement un couple. Un widget posé sur l'écran d'accueil permet d'envoyer en un seul tap un poke (« j'ai envie de toi », « câlin », « je rentre »…) à son partenaire, qui reçoit une notification immédiate avec des **réponses prédéfinies** ou la possibilité d'écrire sa propre réponse.

Pas de fil de discussion, pas d'historique infini, pas de réseau social — juste un canal direct entre deux personnes.

## 🔔 Fonctionnalités

- **Widgets configurables** posés sur l'écran d'accueil :
  - **Widget 1×1** — un Dring choisi (ou aléatoire) en un tap
  - **Widget 4×2** — 8 Drings à portée de doigt, chaque slot personnalisable
- **8 Drings préfaits** : ❤️ Je t'aime · 🤗 Câlin · 😘 Bisou · 🔥 Envie · 🥺 Manque · 💭 Pense à toi · 🍝 On mange quoi ? · 🚗 Je rentre
- **Réponses rapides** : 1 tap depuis la notification ou depuis l'app pour répondre (ou réponse libre tapée au clavier)
- **Cooldown anti-spam de poche** : 5 s entre Drings, 2 s pour les réponses
- **Compteur du jour** : nombre de Drings envoyés / reçus
- **Feedback visuel** : widget grisé pendant le cooldown avec compte à rebours animé
- **Auth-less** : 2 utilisateurs hardcodés (pas de signup, pas de mot de passe, pas d'email). On choisit qui on est au premier lancement et c'est tout.

## 🧱 Architecture

```
[ Widget tap ]
       │
       ▼
[ AppWidgetProvider ] ──► [ PokeWorker (WorkManager) ]
                                   │
                                   ▼
                  POST /functions/v1/send-poke (Edge Function)
                                   │
                          ┌────────┴────────┐
                          ▼                 ▼
                   [ Supabase DB ]    [ Firebase FCM ]
                                            │
                                            ▼
                            [ Notification sur l'autre tel ]
```

## 🛠 Stack

- **Android** : Kotlin · Jetpack Compose · AppWidgetProvider · WorkManager · FirebaseMessagingService
- **Backend** : [Supabase](https://supabase.com) (Postgres + Edge Functions Deno)
- **Push** : [Firebase Cloud Messaging](https://firebase.google.com/products/cloud-messaging) (HTTP v1, signé via service account côté Edge Function)
- **Build** : Gradle Kotlin DSL · AGP 9 · min SDK 26

## 📂 Structure du projet

```
dring-widget/
├── app/                                        # Module Android
│   └── src/main/
│       ├── java/fr/dring/app/
│       │   ├── MainActivity.kt                # UI Compose principale
│       │   ├── Config.kt                      # URL Supabase, clés publiques, messages
│       │   ├── Identity.kt                    # Quel user suis-je ? (SharedPrefs)
│       │   ├── ReplyPresets.kt                # Mapping Dring -> réponses préfaites
│       │   ├── Stats.kt                       # Compteur Drings envoyés/reçus du jour
│       │   ├── LastReceivedStore.kt           # Dernier Dring reçu (pour reply card)
│       │   ├── widget/                        # Widgets 1x1 + 4x2 + activités de config
│       │   ├── poke/PokeWorker.kt             # Appel HTTP à send-poke
│       │   └── fcm/                           # Réception push + reply receiver
│       └── res/drawable/ic_dring_logo.png     # Logo
└── supabase/
    ├── migrations/0002_reset_no_auth.sql      # Schéma Postgres (profiles, pokes)
    └── functions/send-poke/index.ts           # Edge Function : reçoit poke + push FCM
```

## ⚙️ Setup minimal

> Projet conçu pour un usage perso à 2 — pas distribué sur le Play Store.

### 1. Backend Supabase

1. Crée un projet [Supabase](https://supabase.com) (free tier suffit)
2. Lance `supabase/migrations/0002_reset_no_auth.sql` dans le SQL Editor
3. Déploie `supabase/functions/send-poke/index.ts` comme Edge Function
4. Désactive *Verify JWT* dans les settings de la fonction
5. Configure les secrets de la fonction :
   - `FCM_SERVICE_ACCOUNT` — JSON entier du service account Firebase Admin
   - `SERVICE_ROLE_KEY` — clé `sb_secret_…` du projet

### 2. Backend Firebase

1. Crée un projet [Firebase](https://console.firebase.google.com)
2. Ajoute une app Android avec le package `fr.dring.app`
3. Télécharge `google-services.json` et place-le dans `app/`
4. Génère une clé privée pour le SDK Admin (Paramètres du projet → Comptes de service) et colle son contenu dans le secret Supabase `FCM_SERVICE_ACCOUNT`

### 3. Côté Android

1. Renseigne `SUPABASE_URL` et `SUPABASE_KEY` (publishable) dans [`Config.kt`](app/src/main/java/fr/dring/app/Config.kt)
2. Adapte les noms et UUIDs des deux utilisateurs dans `Config.kt` + dans la migration SQL
3. Build l'APK : `./gradlew assembleDebug`
4. Installe sur les deux téléphones

## 🔐 Modèle de sécurité

- **Pas d'authentification** : la sécurité repose sur « qui possède l'APK ». Adapté pour un usage perso strict.
- **Edge Function publique** (sans JWT) : protège seulement contre les requêtes invalides via cooldown serveur.
- Les UUIDs des deux profils sont fixés dans `Config.kt` et la table `profiles` est seedée en conséquence.
- Pour un usage non perso, ré-activer Supabase Auth + RLS.

## 🤝 Crédits

Projet personnel développé pour rester connecté avec ma copine 💕

---

<div align="center">
<sub>Made with ❤️ and Kotlin</sub>
</div>

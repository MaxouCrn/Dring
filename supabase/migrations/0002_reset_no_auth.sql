-- Reset complet : on drop tout du 0001 et on repart sans auth.
drop trigger if exists on_auth_user_created on auth.users;
drop function if exists public.handle_new_user();
drop function if exists public.pair_with(text);
drop table if exists public.pokes;
drop table if exists public.profiles;

-- Profil simplifié : juste id (UUID fixe), name, fcm_token
create table public.profiles (
    id uuid primary key,
    name text not null,
    fcm_token text,
    created_at timestamptz not null default now()
);

create table public.pokes (
    id bigserial primary key,
    sender_id uuid not null references public.profiles(id) on delete cascade,
    receiver_id uuid not null references public.profiles(id) on delete cascade,
    message text,
    sent_at timestamptz not null default now()
);

create index pokes_sender_sent_at_idx on public.pokes (sender_id, sent_at desc);

-- Pas de RLS : on assume que l'APK est privé.
alter table public.profiles disable row level security;
alter table public.pokes disable row level security;

-- Seed les 2 utilisateurs (UUIDs fixes connus de l'app)
insert into public.profiles (id, name) values
    ('11111111-1111-1111-1111-111111111111', 'Max'),
    ('22222222-2222-2222-2222-222222222222', 'Ma chérie')
on conflict (id) do nothing;

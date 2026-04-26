-- Profil utilisateur lié à auth.users
create table public.profiles (
    id uuid primary key references auth.users(id) on delete cascade,
    email text not null unique,
    fcm_token text,
    partner_id uuid references public.profiles(id) on delete set null,
    created_at timestamptz not null default now()
);

-- Historique des pokes (pour cooldown + debug)
create table public.pokes (
    id bigserial primary key,
    sender_id uuid not null references public.profiles(id) on delete cascade,
    receiver_id uuid not null references public.profiles(id) on delete cascade,
    message text,
    sent_at timestamptz not null default now()
);

create index pokes_sender_sent_at_idx on public.pokes (sender_id, sent_at desc);

-- RLS
alter table public.profiles enable row level security;
alter table public.pokes enable row level security;

-- Profil : chacun voit/modifie le sien, et peut lire celui de son partenaire
create policy "read own or partner profile" on public.profiles
    for select using (
        auth.uid() = id
        or auth.uid() = partner_id
        or id = (select partner_id from public.profiles where id = auth.uid())
    );

create policy "update own profile" on public.profiles
    for update using (auth.uid() = id);

create policy "insert own profile" on public.profiles
    for insert with check (auth.uid() = id);

-- Pokes : on voit ceux qu'on a envoyés ou reçus
create policy "read own pokes" on public.pokes
    for select using (auth.uid() = sender_id or auth.uid() = receiver_id);

-- Auto-création du profil à l'inscription
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    insert into public.profiles (id, email) values (new.id, new.email);
    return new;
end;
$$;

create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function public.handle_new_user();

-- Fonction de pairing : lie deux profils par email mutuel
create or replace function public.pair_with(partner_email text)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    me uuid := auth.uid();
    partner uuid;
begin
    if me is null then raise exception 'not authenticated'; end if;

    select id into partner from public.profiles where email = partner_email;
    if partner is null then raise exception 'partner not found'; end if;
    if partner = me then raise exception 'cannot pair with yourself'; end if;

    update public.profiles set partner_id = partner where id = me;
    update public.profiles set partner_id = me where id = partner;
end;
$$;

grant execute on function public.pair_with(text) to authenticated;

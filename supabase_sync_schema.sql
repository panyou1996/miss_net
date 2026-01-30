-- 1. Favorites Table (Cloud Sync)
create table if not exists public.favorites (
  user_id uuid references auth.users not null,
  video_id uuid references public.videos(id) not null,
  created_at timestamp with time zone default timezone('utc'::text, now()) not null,
  primary key (user_id, video_id)
);

-- RLS for Favorites
alter table public.favorites enable row level security;

create policy "Users can view their own favorites"
  on public.favorites for select
  using ( auth.uid() = user_id );

create policy "Users can insert their own favorites"
  on public.favorites for insert
  with check ( auth.uid() = user_id );

create policy "Users can delete their own favorites"
  on public.favorites for delete
  using ( auth.uid() = user_id );

-- 2. Watch History Table (Cloud Sync)
create table if not exists public.watch_history (
  user_id uuid references auth.users not null,
  video_id uuid references public.videos(id) not null,
  position_ms int4 default 0,
  updated_at timestamp with time zone default timezone('utc'::text, now()) not null,
  primary key (user_id, video_id)
);

-- RLS for Watch History
alter table public.watch_history enable row level security;

create policy "Users can view their own history"
  on public.watch_history for select
  using ( auth.uid() = user_id );

create policy "Users can insert/update their own history"
  on public.watch_history for insert
  with check ( auth.uid() = user_id );

create policy "Users can update their own history"
  on public.watch_history for update
  using ( auth.uid() = user_id );

-- Optional: Function to update 'updated_at' on history change

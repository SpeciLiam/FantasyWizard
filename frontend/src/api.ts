export type LeagueUser = { userId: string; displayName: string; avatar?: string; isMe?: boolean };
export type Player = { id: string; name: string; pos: string; team?: string; proj?: number; value?: number };
export type DraftPick = { season: number; round: number; originalOwner?: string; originalOwnerName?: string; owner?: string; traded?: boolean };
export type Roster = { starters: Player[]; bench: Player[]; taxi: Player[]; picks?: DraftPick[] };
export type FantasyProvider = "sleeper" | "yahoo";

export type MatchupTeam = {
  userId: string;
  displayName: string;
  avatar: string | null;
  projectedTotal: number;
  starters: Player[];
};

export type MatchupPair = {
  home: MatchupTeam;
  away: MatchupTeam;
};

export type MatchupsResponse = {
  leagueId: string;
  week: number;
  pairs: MatchupPair[];
};

const API_BASE = import.meta.env.VITE_API_BASE ?? "http://localhost:8080";

export type LeaguePick = {
  season: number;
  round: number;
  originalOwnerName: string;
  currentOwnerName: string;
  currentUserId: string;
  currentAvatar?: string;
  traded: boolean;
};

export type LeaguePicksResponse = {
  managers: { userId: string; displayName: string; avatar?: string }[];
  picks: LeaguePick[];
};

export type TeamRoster = {
  displayName: string;
  userId: string;
  starters: { id: string; name: string; pos: string; team: string; proj: number }[];
  bench: { id: string; name: string; pos: string; team: string; proj: number }[];
  taxi: { id: string; name: string; pos: string; team: string; proj: number }[];
};

export async function fetchAllRosters(leagueId: string, week: number, provider: FantasyProvider = "sleeper"): Promise<TeamRoster[]> {
  const res = await fetch(`${API_BASE}/api/league/${leagueId}/all-rosters?week=${week}&provider=${provider}`);
  if (!res.ok) throw new Error(`All rosters fetch failed: ${res.status}`);
  return res.json();
}

export async function fetchLeaguePicks(leagueId: string, provider: FantasyProvider = "sleeper"): Promise<LeaguePicksResponse> {
  const res = await fetch(`${API_BASE}/api/league/${leagueId}/picks?provider=${provider}`);
  if (!res.ok) throw new Error(`Picks fetch failed: ${res.status}`);
  return res.json();
}

export async function sendAdvisorChat(
  message: string,
  history: { role: string; content: string }[],
  context?: string
): Promise<string> {
  const res = await fetch(`${API_BASE}/api/advisor/chat`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ message, history, context }),
  });
  if (!res.ok) {
    let message = `Chat failed: ${res.status}`;
    try {
      const errorBody = await res.json();
      if (errorBody?.message) message = errorBody.message;
    } catch {
      /* keep status fallback */
    }
    throw new Error(message);
  }
  const data = await res.json();
  return data.reply as string;
}

export async function fetchLeagues(username: string, season: number, provider: FantasyProvider = "sleeper") {
  const res = await fetch(`${API_BASE}/api/user/${encodeURIComponent(username || "me")}/leagues?season=${season}&provider=${provider}`);
  if (!res.ok) throw new Error(`Leagues fetch failed: ${res.status}`);
  const data = await res.json();
  return (data.leagues ?? []) as Array<{ leagueId: string; name: string }>;
}

export async function fetchLeagueUsers(leagueId: string, provider: FantasyProvider = "sleeper", username?: string) {
  const usernameParam = username ? `&username=${encodeURIComponent(username)}` : "";
  const res = await fetch(`${API_BASE}/api/league/${leagueId}/members?provider=${provider}${usernameParam}`);
  if (!res.ok) throw new Error(`Members fetch failed: ${res.status}`);
  return (await res.json()) as LeagueUser[];
}

export async function fetchRoster(leagueId: string, userId: string, week: number, provider: FantasyProvider = "sleeper") {
  const res = await fetch(`${API_BASE}/api/league/${leagueId}/roster/${userId}?week=${week}&provider=${provider}`);
  if (!res.ok) throw new Error(`Roster fetch failed: ${res.status}`);
  return (await res.json()) as Roster;
}

export async function fetchMatchups(leagueId: string, week: number, provider: FantasyProvider = "sleeper") {
  const res = await fetch(`${API_BASE}/api/league/${leagueId}/matchups/${week}?provider=${provider}`);
  if (!res.ok) throw new Error(`Matchups fetch failed: ${res.status}`);
  return (await res.json()) as MatchupsResponse;
}

export async function fetchState() {
  const res = await fetch(`${API_BASE}/api/state`);
  if (!res.ok) throw new Error(`State fetch failed: ${res.status}`);
  return (await res.json()) as { season?: number; week?: number };
}

export async function fetchYahooStatus(): Promise<{ configured: boolean; connected: boolean }> {
  const res = await fetch(`${API_BASE}/api/yahoo/status`);
  if (!res.ok) throw new Error(`Yahoo status failed: ${res.status}`);
  return res.json();
}

export async function fetchYahooAuthUrl(): Promise<string> {
  const res = await fetch(`${API_BASE}/api/yahoo/auth-url`);
  if (!res.ok) throw new Error(`Yahoo auth URL failed: ${res.status}`);
  const data = await res.json();
  return data.url as string;
}

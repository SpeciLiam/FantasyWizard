export type LeagueUser = { userId: string; displayName: string; avatar?: string; isMe?: boolean };
export type Player = { id: string; name: string; pos: string; team?: string; proj?: number; value?: number };
export type DraftPick = { season: number; round: number; originalOwner?: string; originalOwnerName?: string; owner?: string; traded?: boolean };
export type Roster = { starters: Player[]; bench: Player[]; taxi: Player[]; picks?: DraftPick[] };

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
  if (!res.ok) throw new Error(`Chat failed: ${res.status}`);
  const data = await res.json();
  return data.reply as string;
}

export async function fetchLeagues(username: string, season: number) {
  const res = await fetch(`${API_BASE}/api/user/${encodeURIComponent(username)}/leagues?season=${season}`);
  if (!res.ok) throw new Error(`Leagues fetch failed: ${res.status}`);
  const data = await res.json();
  return (data.leagues ?? []) as Array<{ leagueId: string; name: string }>;
}

export async function fetchLeagueUsers(leagueId: string) {
  const res = await fetch(`${API_BASE}/api/league/${leagueId}/members`);
  if (!res.ok) throw new Error(`Members fetch failed: ${res.status}`);
  return (await res.json()) as LeagueUser[];
}

export async function fetchRoster(leagueId: string, userId: string, week: number) {
  const res = await fetch(`${API_BASE}/api/league/${leagueId}/roster/${userId}?week=${week}`);
  if (!res.ok) throw new Error(`Roster fetch failed: ${res.status}`);
  return (await res.json()) as Roster;
}

export async function fetchMatchups(leagueId: string, week: number) {
  const res = await fetch(`${API_BASE}/api/league/${leagueId}/matchups/${week}`);
  if (!res.ok) throw new Error(`Matchups fetch failed: ${res.status}`);
  return (await res.json()) as MatchupsResponse;
}

export async function fetchState() {
  const res = await fetch(`${API_BASE}/api/state`);
  if (!res.ok) throw new Error(`State fetch failed: ${res.status}`);
  return (await res.json()) as { season?: number; week?: number };
}

const API = "https://api.sleeper.app";

// sleeper.ts
export type SleeperUser = { user_id: string; display_name: string; avatar?: string | null };
export type SleeperRoster = {
  roster_id: number;
  owner_id: string;
  settings?: {
    wins?: number;
    losses?: number;
    ties?: number;
    fpts?: number;
    fpts_decimal?: number;
  };
};
export type SleeperMatchupRow = {
  roster_id: number;
  matchup_id?: number | null;
  starters?: string[];     // player ids (optional to render)
  points?: number;         // actual points (optional)
  // ...other fields not used here
};

/* Remove duplicate declaration of API. */

export async function getLeagueUsers(leagueId: string): Promise<SleeperUser[]> {
  const r = await fetch(`${API}/v1/league/${leagueId}/users`);
  if (!r.ok) throw new Error(`users ${r.status}`);
  return r.json();
}

export async function getRosters(leagueId: string): Promise<SleeperRoster[]> {
  const r = await fetch(`${API}/v1/league/${leagueId}/rosters`);
  if (!r.ok) throw new Error(`rosters ${r.status}`);
  return r.json();
}

export async function getWeekMatchups(leagueId: string, week: number): Promise<SleeperMatchupRow[]> {
  const r = await fetch(`${API}/v1/league/${leagueId}/matchups/${week}`);
  if (!r.ok) throw new Error(`matchups ${r.status}`);
  return r.json();
}

/** Pair rows by matchup_id, map roster_id -> owner_id (user_id).
 * Sleeper doesn't define "home/away", so we pick deterministically:
 * lower owner_id (string) becomes "home".
 */
export type MatchupSide = { userId: string; projectedTotal?: number };
export type MatchupPair = { id: string; home: MatchupSide; away: MatchupSide };

/** Fetches the full NFL player metadata dictionary from Sleeper. */
export async function getSleeperPlayersDict(): Promise<Record<string, any>> {
  const r = await fetch("https://api.sleeper.app/v1/players/nfl");
  if (!r.ok) throw new Error(`players ${r.status}`);
  return r.json();
}


export function buildMatchupPairs(
  rows: SleeperMatchupRow[],
  rosters: SleeperRoster[],
): MatchupPair[] {
  if (!rows?.length) return [];
  const rMap = new Map<number, string>(); // roster_id -> owner_id (user_id)
  for (const r of rosters) rMap.set(r.roster_id, r.owner_id);

  // group by matchup_id (null/undefined can happen; treat each as its own)
  const byId = new Map<number|string, SleeperMatchupRow[]>();
  for (const row of rows) {
    const key = row.matchup_id ?? `solo-${row.roster_id}`;
    const arr = byId.get(key) ?? [];
    arr.push(row);
    byId.set(key, arr);
  }

  const pairs: MatchupPair[] = [];
  for (const [key, arr] of byId) {
    // sometimes there are >2 rows (multi-team oddities); pick two by roster_id sort
    const two = [...arr].sort((a,b)=>a.roster_id - b.roster_id).slice(0,2);
    const a = two[0];
    const b = two[1];

    const aUser = rMap.get(a.roster_id);
    const bUser = b ? rMap.get(b.roster_id) : undefined;
    if (!aUser) continue;

    // stable "home/away" selection
    const [homeUser, awayUser] =
      bUser && aUser > bUser ? [bUser, aUser] : [aUser, bUser ?? aUser];

    // If only one team in the matchup (rare), duplicate as away so UI still renders two columns
    const id = String(key);
    pairs.push({
      id,
      home: { userId: homeUser!, projectedTotal: a.points }, // points optional; swap for projections if you have them
      away: { userId: awayUser!, projectedTotal: b?.points },
    });
  }
  return pairs;
}

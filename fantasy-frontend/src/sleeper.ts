export type LeagueUserApi = {
  user_id: string;
  display_name: string;
  avatar?: string | null;
  // other fields we don’t use right now…
};

const SLEEPER_API = "https://api.sleeper.app";

export async function getLeagueUsers(leagueId: string): Promise<LeagueUserApi[]> {
  const res = await fetch(`${SLEEPER_API}/v1/league/${leagueId}/users`);
  if (!res.ok) {
    throw new Error(`Sleeper users fetch failed: ${res.status} ${res.statusText}`);
  }
  return res.json();
}

export function avatarUrlFromId(avatar?: string | null): string | undefined {
  if (!avatar) return undefined;
  // Use thumbs for faster load; swap to full-size if desired:
  return `https://sleepercdn.com/avatars/thumbs/${avatar}`;
}

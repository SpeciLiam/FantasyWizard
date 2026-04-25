import React, { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import ReactMarkdown from 'react-markdown';
import { ManualTradeModal, AITradesModal } from './TradeModal';

import {
  Player,
  Roster,
  LeaguePick,
  TeamRoster,
  FantasyProvider,
  fetchLeagues,
  fetchRoster,
  fetchState,
  fetchLeaguePicks,
  fetchLeagueUsers,
  fetchAllRosters,
  fetchYahooAuthUrl,
  fetchYahooStatus,
  sendAdvisorChat,
} from './api';
import { getLeagueUsers, getRosters } from './sleeper';

const API_BASE = import.meta.env.VITE_API_BASE ?? 'http://localhost:8080';

export type LeagueUser = {
  userId: string;
  displayName: string;
  avatarUrl?: string;
  isMe?: boolean;
};

type Standing = {
  userId: string;
  wins: number;
  losses: number;
  ties: number;
  fpts: number;
};

// ── Position color helpers ──
const POS_HEX: Record<string, string> = {
  QB: '#f87171',
  RB: '#4ade80',
  WR: '#60a5fa',
  TE: '#fb923c',
  K: '#c084fc',
  DEF: '#94a3b8',
  FLEX: '#fbbf24',
};
function posHex(pos?: string): string {
  return POS_HEX[(pos ?? '').toUpperCase()] ?? '#5a5a6e';
}

// ── Initials avatar helpers ──
function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .map((w) => w[0])
    .join('')
    .slice(0, 2)
    .toUpperCase();
}
function hueFromName(name: string): number {
  let h = 0;
  for (let i = 0; i < name.length; i++) {
    h = (h * 31 + name.charCodeAt(i)) % 360;
  }
  return h;
}

function normalizeText(value: string): string {
  return value
    .toLowerCase()
    .replace(/[^a-z0-9\s]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

function cleanTeam(team?: string | null): string | undefined {
  if (!team || team.toLowerCase() === 'null') return undefined;
  return team;
}

function playerSearchTerms(player: Player): string[] {
  const normalized = normalizeText(player.name);
  const parts = normalized.split(' ').filter((part) => part.length > 1);
  return [...new Set([normalized, ...parts, cleanTeam(player.team) ?? ''].filter(Boolean))];
}

function resolveMentionedPlayers(
  text: string,
  roster: Roster | null,
  history: { role: 'user' | 'assistant'; text: string }[]
): Player[] {
  if (!roster) return [];
  const players = [...roster.starters, ...roster.bench, ...roster.taxi];
  const query = normalizeText(text);
  const direct = players.filter((player) =>
    playerSearchTerms(player).some((term) => query.includes(term))
  );
  if (direct.length) return direct;

  const asksForSamePerson =
    /\b(he|him|his|that|that guy|same player|recently|nowadays|doing|news|meant|mean)\b/i.test(text);
  if (!asksForSamePerson) return [];

  const recentUserText = history
    .filter((message) => message.role === 'user')
    .slice(-3)
    .map((message) => message.text)
    .join(' ');
  const recent = normalizeText(recentUserText);
  return players.filter((player) =>
    playerSearchTerms(player).some((term) => recent.includes(term))
  );
}

function inferConversationIntent(text: string): string {
  if (/trade|swap|offer|deal|pick up|drop|sell|buy|acquire/i.test(text)) {
    return 'trade / roster construction';
  }
  if (/start|sit|lineup|bench|flex|play|playing/i.test(text)) {
    return 'start-sit / lineup';
  }
  if (/news|injur|report|update|espn|hurt|status|practice|limited|questionable|out|active|inactive|recent|recently|nowadays|doing|latest/i.test(text)) {
    return 'player news / availability';
  }
  if (/matchup|opponent|facing|this week|projec|winning/i.test(text)) {
    return 'weekly matchup';
  }
  if (/pick|draft pick|round|2026|2027|2028|dynasty/i.test(text)) {
    return 'draft picks / dynasty value';
  }
  return 'general fantasy advice';
}

function isNewsQuestion(text: string, mentionedPlayers: Player[]): boolean {
  if (/news|injur|report|update|espn|hurt|status|practice|limited|questionable|out|active|inactive|recent|recently|nowadays|doing|latest/i.test(text)) {
    return true;
  }
  return mentionedPlayers.length > 0 && /\b(how|what|why|should|start|play|playing)\b/i.test(text);
}

const Avatar: React.FC<{ name: string; size?: 'sm' | 'lg' }> = ({
  name,
  size = 'sm',
}) => {
  const hue = hueFromName(name || '?');
  const bg = `oklch(0.35 0.08 ${hue})`;
  const fg = `oklch(0.85 0.1 ${hue})`;
  const border = `oklch(0.45 0.08 ${hue})`;
  return (
    <div
      className={`avatar avatar-${size}`}
      style={{ background: bg, color: fg, borderColor: border }}
    >
      {initials(name || '?')}
    </div>
  );
};

const SectionLabel: React.FC<{ children: React.ReactNode; count?: number }> = ({
  children,
  count,
}) => (
  <div className="section-label">
    <span>{children}</span>
    {typeof count === 'number' && <span className="section-count">{count}</span>}
  </div>
);

const PlayerCard: React.FC<{
  p: Player;
  role?: 'starter' | 'bench' | 'taxi';
  rosProj?: number;
}> = ({ p, role = 'starter', rosProj }) => {
  const color = posHex(p.pos);
  const team = cleanTeam(p.team);
  return (
    <div className={`player-card ${role}`}>
      <div className="pos-accent" style={{ background: color }} />
      <div className="pos-badge" style={{ background: color + '22', color }}>
        {p.pos}
      </div>
      <div className="player-main">
        <div className="player-name">{p.name}</div>
        <div className="player-meta">
          {team && <span>{team}</span>}
          {typeof p.value === 'number' && (
            <span className="value-badge">val {p.value.toFixed(0)}</span>
          )}
        </div>
      </div>
      <div className="proj-cell">
        <div className="row">
          <span className="label">WK</span>
          <span className="wk">
            {typeof p.proj === 'number' ? p.proj.toFixed(1) : '—'}
          </span>
        </div>
        <div className="row">
          <span className="label">ROS</span>
          <span className="ros">
            {typeof rosProj === 'number' ? rosProj.toFixed(1) : '—'}
          </span>
        </div>
      </div>
    </div>
  );
};

const PickChip: React.FC<{ pick: LeaguePick }> = ({ pick }) => (
  <div className={`pick-chip ${pick.traded ? 'traded' : 'own'}`}>
    <span className="pri">
      {pick.season} R{pick.round}
    </span>
    {pick.traded && <span className="sub">via {pick.originalOwnerName}</span>}
  </div>
);

const QUICK_PROMPTS = [
  'Who should I start?',
  'Best trade targets?',
  'Waiver wire adds?',
  'Am I winning this week?',
];

const TypingDots: React.FC = () => (
  <span className="typing-dots">
    <span className="typing-dot" />
    <span className="typing-dot" />
    <span className="typing-dot" />
  </span>
);

export default function App() {
  const [tab, setTab] = useState<'roster' | 'matchups'>('roster');
  const [provider, setProvider] = useState<FantasyProvider>('sleeper');
  const [yahooStatus, setYahooStatus] = useState<{
    configured: boolean;
    connected: boolean;
  } | null>(null);
  const topbarRef = useRef<HTMLDivElement | null>(null);
  const [showManualTrade, setShowManualTrade] = useState(false);
  const [showAITrades, setShowAITrades] = useState(false);
  const [showPicksModal, setShowPicksModal] = useState(false);

  const [username, setUsername] = useState('SpeciLiam');
  const [season, setSeason] = useState<number>(new Date().getFullYear());
  const [week, setWeek] = useState<number>(1);

  useEffect(() => {
    (async () => {
      try {
        const s = await fetchState();
        if (typeof s?.week === 'number' && s.week >= 1 && s.week <= 18) setWeek(s.week);
        if (typeof s?.season === 'number' && !season) setSeason(s.season);
      } catch (_) {
        /* keep defaults */
      }
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (provider !== 'yahoo') return;
    fetchYahooStatus()
      .then(setYahooStatus)
      .catch(() => setYahooStatus({ configured: false, connected: false }));
  }, [provider]);

  const [leagues, setLeagues] = useState<Array<{ leagueId: string; name: string }>>([]);
  const [leagueId, setLeagueId] = useState<string | null>(null);
  const [users, setUsers] = useState<LeagueUser[]>([]);
  const [standings, setStandings] = useState<Record<string, Standing>>({});
  const [loadingUsers, setLoadingUsers] = useState(false);
  const [errorUsers, setErrorUsers] = useState<string | null>(null);
  const [memberSearch, setMemberSearch] = useState('');
  const [selectedUser, setSelectedUser] = useState<LeagueUser | null>(null);
  const [roster, setRoster] = useState<Roster | null>(null);
  const [mainError, setMainError] = useState<string | null>(null);

  const [chatInput, setChatInput] = useState('');
  const [chatLog, setChatLog] = useState<
    { role: 'user' | 'assistant'; text: string }[]
  >([]);
  const [chatLoading, setChatLoading] = useState(false);

  useLayoutEffect(() => {
    const topbar = topbarRef.current;
    if (!topbar) return;

    const updateTopbarHeight = () => {
      document.documentElement.style.setProperty(
        '--topbar-height',
        `${topbar.offsetHeight}px`
      );
    };

    updateTopbarHeight();
    const observer = new ResizeObserver(updateTopbarHeight);
    observer.observe(topbar);
    window.addEventListener('resize', updateTopbarHeight);
    return () => {
      observer.disconnect();
      window.removeEventListener('resize', updateTopbarHeight);
    };
  }, []);

  useEffect(() => {
    setLeagues([]);
    setLeagueId(null);
    setUsers([]);
    setRoster(null);
    setMainError(null);
    fetchLeagues(username, season, provider)
      .then((ls) => {
        setLeagues(ls);
        setLeagueId(ls[0]?.leagueId ?? null);
      })
      .catch((e) => setMainError('Could not load leagues: ' + String(e)));
    // eslint-disable-next-line
  }, [username, season, provider]);

  useEffect(() => {
    if (!leagueId) {
      setUsers([]);
      setSelectedUser(null);
      setRoster(null);
      setStandings({});
      return;
    }
    setUsers([]);
    setSelectedUser(null);
    setRoster(null);
    setErrorUsers(null);
    setLoadingUsers(true);
    const usersPromise =
      provider === 'sleeper'
        ? getLeagueUsers(leagueId).then((apiUsers) =>
            apiUsers.map((u) => ({
              userId: u.user_id,
              displayName: u.display_name,
              avatarUrl: u.avatar
                ? `https://sleepercdn.com/avatars/thumbs/${u.avatar}`
                : undefined,
              isMe: u.display_name?.toLowerCase() === username.toLowerCase(),
            }))
          )
        : fetchLeagueUsers(leagueId, provider, username).then((apiUsers) =>
            apiUsers.map((u) => ({
              userId: u.userId,
              displayName: u.displayName,
              avatarUrl: u.avatar,
              isMe: u.isMe,
            }))
          );
    const rostersPromise =
      provider === 'sleeper' ? getRosters(leagueId) : Promise.resolve([]);

    Promise.all([usersPromise, rostersPromise])
      .then(([mapped, sleeperRosters]) => {
        const s: Record<string, Standing> = {};
        for (const r of sleeperRosters) {
          if (!r.owner_id) continue;
          const fpts =
            (r.settings?.fpts ?? 0) + (r.settings?.fpts_decimal ?? 0) / 100;
          s[r.owner_id] = {
            userId: r.owner_id,
            wins: r.settings?.wins ?? 0,
            losses: r.settings?.losses ?? 0,
            ties: r.settings?.ties ?? 0,
            fpts,
          };
        }
        setUsers(mapped);
        setStandings(s);
        const me = mapped.find((u) => u.isMe) ?? mapped[0];
        if (me) setSelectedUser(me);
      })
      .catch((e) => setErrorUsers(e?.message ?? 'Failed to fetch users'))
      .finally(() => setLoadingUsers(false));
    // eslint-disable-next-line
  }, [leagueId, provider, username]);

  useEffect(() => {
    if (!leagueId || !selectedUser) {
      setRoster(null);
      return;
    }
    setRoster(null);
    setMainError(null);
    fetchRoster(leagueId, selectedUser.userId, week, provider)
      .then(setRoster)
      .catch((e) => setMainError('Could not load roster: ' + String(e)));
  }, [leagueId, selectedUser, week, provider]);

  // Order: pin self to top, others by fpts desc
  const orderedUsers = useMemo(() => {
    if (!users.length) return [];
    const myId = users.find((u) => u.isMe)?.userId;
    const sortedByFpts = [...users].sort((a, b) => {
      const fa = standings[a.userId]?.fpts ?? 0;
      const fb = standings[b.userId]?.fpts ?? 0;
      return fb - fa;
    });
    if (!myId) return sortedByFpts;
    const me = sortedByFpts.find((u) => u.userId === myId);
    if (!me) return sortedByFpts;
    return [me, ...sortedByFpts.filter((u) => u.userId !== myId)];
  }, [users, standings]);

  const rankByUser = useMemo(() => {
    const ranked = [...users].sort((a, b) => {
      const fa = standings[a.userId]?.fpts ?? 0;
      const fb = standings[b.userId]?.fpts ?? 0;
      return fb - fa;
    });
    const map: Record<string, number> = {};
    ranked.forEach((u, i) => (map[u.userId] = i + 1));
    return map;
  }, [users, standings]);

  const filteredUsers = useMemo(() => {
    const q = memberSearch.trim().toLowerCase();
    if (!q) return orderedUsers;
    return orderedUsers.filter((u) => u.displayName.toLowerCase().includes(q));
  }, [orderedUsers, memberSearch]);

  // Matchups
  type MatchupSide = {
    userId: string;
    displayName?: string;
    projectedTotal?: number;
    starters?: Player[];
  };
  type MatchupPair = { id: string; home: MatchupSide; away: MatchupSide };

  const [pairs, setPairs] = useState<MatchupPair[]>([]);
  const [expandedMatchup, setExpandedMatchup] = useState<string | null>(null);
  useEffect(() => {
    let ok = true;
    (async () => {
      if (!leagueId || !week || !season) {
        setPairs([]);
        return;
      }
      try {
        const url = `${API_BASE}/api/projections/${season}/${week}/league/${leagueId}?format=ppr&provider=${provider}`;
        const resp = await fetch(url);
        if (!resp.ok) throw new Error('Projections API error: ' + resp.status);
        const data = await resp.json();
        if (!ok) return;
        setPairs(data.pairs || []);
      } catch (_) {
        if (!ok) return;
      }
    })();
    return () => {
      ok = false;
    };
  }, [leagueId, season, week, provider]);

  const orderedPairs = useMemo(() => {
    if (!selectedUser) return pairs;
    const me = selectedUser.userId;
    const idx = pairs.findIndex(
      (p) => p.home.userId === me || p.away.userId === me
    );
    if (idx <= 0) return pairs;
    const arr = pairs.slice();
    const [hit] = arr.splice(idx, 1);
    arr.unshift(hit);
    return arr;
  }, [pairs, selectedUser]);

  const selectedMatchup = useMemo(() => {
    if (!selectedUser) return null;
    return (
      pairs.find(
        (p) =>
          p.home.userId === selectedUser.userId ||
          p.away.userId === selectedUser.userId
      ) ?? null
    );
  }, [pairs, selectedUser]);

  const selectedOpponent = useMemo(() => {
    if (!selectedUser || !selectedMatchup) return null;
    return selectedMatchup.home.userId === selectedUser.userId
      ? selectedMatchup.away
      : selectedMatchup.home;
  }, [selectedMatchup, selectedUser]);

  const [allRosters, setAllRosters] = useState<TeamRoster[]>([]);
  useEffect(() => {
    if (!leagueId) {
      setAllRosters([]);
      return;
    }
    fetchAllRosters(leagueId, week, provider)
      .then(setAllRosters)
      .catch(() => setAllRosters([]));
  }, [leagueId, week, provider]);

  const [leaguePicks, setLeaguePicks] = useState<LeaguePick[]>([]);
  const [picksLoading, setPicksLoading] = useState(false);
  useEffect(() => {
    if (!leagueId) {
      setLeaguePicks([]);
      return;
    }
    setPicksLoading(true);
    fetchLeaguePicks(leagueId, provider)
      .then((r) => setLeaguePicks(r.picks))
      .catch(() => setLeaguePicks([]))
      .finally(() => setPicksLoading(false));
  }, [leagueId, provider]);

  const [rosProjections, setRosProjections] = useState<Record<string, number>>({});
  useEffect(() => {
    fetch(`${API_BASE}/api/projections/ros?format=ppr`)
      .then((r) => r.json())
      .then((r) => setRosProjections(r.map || {}))
      .catch(() => {});
  }, []);

  const nameOf = (uid?: string) =>
    users.find((u) => u.userId === uid)?.displayName ?? '—';

  const sendChatWith = async (text: string) => {
    if (!text || chatLoading) return;
    const history = chatLog.map((m) => ({ role: m.role, content: m.text }));
    const mentionedPlayers = resolveMentionedPlayers(text, roster, chatLog);
    const intent = inferConversationIntent(text);
    const q = text.toLowerCase();
    const isTrade = /trade|swap|offer|deal|pick up|drop|sell|buy|acquire/i.test(q);
    const isNews = isNewsQuestion(text, mentionedPlayers);
    const isPicks = /pick|draft pick|round|2026|2027|dynasty/i.test(q);
    const isMatchup = /matchup|opponent|facing|this week|projec/i.test(q);

    const fmtPlayer = (p: Player) =>
      `  - ${p.pos} ${p.name}${cleanTeam(p.team) ? ` (${cleanTeam(p.team)})` : ''}${
        typeof p.proj === 'number' ? ` — proj ${p.proj.toFixed(1)}` : ''
      }`;

    let context = '';
    context += `Conversation intent: ${intent}\n`;
    context += `User may ask casually, with typos, nicknames, pronouns, or follow-ups. Infer likely meaning from this context and recent chat history.\n`;
    if (mentionedPlayers.length) {
      context += `Likely referenced player(s): ${mentionedPlayers
        .map((p) => `${p.name}${cleanTeam(p.team) ? ` (${cleanTeam(p.team)})` : ''}`)
        .join(', ')}\n`;
    } else {
      context += `Likely referenced player(s): none resolved from current roster; answer from roster/context if possible and only ask a short clarifying question if truly necessary.\n`;
    }
    if (chatLog.length) {
      context += `Recent conversation:\n${chatLog
        .slice(-6)
        .map((m) => `  ${m.role}: ${m.text.replace(/\s+/g, ' ').slice(0, 220)}`)
        .join('\n')}\n`;
    }
    if (selectedUser)
      context += `Your team: ${selectedUser.displayName} (Week ${week}, Season ${season})\n`;
    if (roster) {
      if (roster.starters?.length)
        context += `Starters:\n${roster.starters.map(fmtPlayer).join('\n')}\n`;
      if (roster.bench?.length)
        context += `Bench:\n${roster.bench.map(fmtPlayer).join('\n')}\n`;
      if (roster.taxi?.length)
        context += `Taxi:\n${roster.taxi.map(fmtPlayer).join('\n')}\n`;
    }
    if (roster?.picks?.length) {
      context += `Your draft picks:\n${roster.picks
        .map((p) => {
          const src = p.traded
            ? ` (via ${p.originalOwnerName ?? p.originalOwner ?? '?'})`
            : ' (own)';
          return `  - ${p.season} Round ${p.round}${src}`;
        })
        .join('\n')}\n`;
    }
    if ((isTrade || isPicks || isMatchup) && allRosters.length > 0) {
      context += `\n--- ALL LEAGUE ROSTERS ---\n`;
      const fmtP = (p: { pos: string; name: string; team: string; proj: number }) =>
        `${p.pos} ${p.name}${cleanTeam(p.team) ? ` (${cleanTeam(p.team)})` : ''} ${p.proj.toFixed(1)}pts`;
      for (const team of allRosters) {
        const isMe = team.userId === selectedUser?.userId;
        context += `\n${team.displayName}${isMe ? ' (YOU)' : ''}:\n`;
        if (team.starters.length)
          context += `  Starters: ${team.starters.map(fmtP).join(' | ')}\n`;
        if (team.bench.length)
          context += `  Bench: ${team.bench.map((p) => `${p.pos} ${p.name}`).join(', ')}\n`;
        if (team.taxi.length)
          context += `  Taxi: ${team.taxi.map((p) => `${p.pos} ${p.name}`).join(', ')}\n`;
      }
    }
    if (isNews) {
      try {
        const [injRes, newsRes] = await Promise.all([
          fetch(`${API_BASE}/api/espn/injuries`),
          fetch(`${API_BASE}/api/espn/news`),
        ]);
        const targetTerms = [
          ...new Set(mentionedPlayers.flatMap(playerSearchTerms).map(normalizeText)),
        ].filter((term) => term.length > 1);

        if (mentionedPlayers.length) {
          context += `\n--- NEWS TARGET PLAYER(S) ---\n${mentionedPlayers
            .map((p) => `  - ${p.name}${cleanTeam(p.team) ? ` (${cleanTeam(p.team)})` : ''}`)
            .join('\n')}\n`;
        }

        if (injRes.ok) {
          const inj: Record<string, string> = await injRes.json();
          const relevant = targetTerms.length
            ? Object.entries(inj).filter(([name]) => {
                const normalizedName = normalizeText(name);
                return targetTerms.some(
                  (term) =>
                    normalizedName === term ||
                    normalizedName.includes(term) ||
                    term.includes(normalizedName)
                );
              })
            : [];
          const all = relevant.length ? relevant : Object.entries(inj).slice(0, 30);
          context += `\n--- ESPN INJURY REPORT ---\n${all
            .map(([n, s]) => `  ${n}: ${s}`)
            .join('\n')}\n`;
          if (targetTerms.length && !relevant.length) {
            context += `No ESPN injury entry matched the target player names above.\n`;
          }
        }
        if (newsRes.ok) {
          const news: { headline: string; description: string; published: string }[] =
            await newsRes.json();
          const relevantNews = targetTerms.length
            ? news.filter((article) => {
                const haystack = normalizeText(
                  `${article.headline} ${article.description ?? ''}`
                );
                return targetTerms.some((term) => haystack.includes(term));
              })
            : [];
          const selectedNews = relevantNews.length ? relevantNews : news.slice(0, 8);
          context += `\n--- ESPN NFL NEWS (${relevantNews.length ? 'matched' : 'latest'}) ---\n${selectedNews
            .slice(0, 8)
            .map(
              (a) =>
                `  - ${a.headline}${a.description ? ` — ${a.description}` : ''}${
                  a.published ? ` (${a.published})` : ''
                }`
            )
            .join('\n')}\n`;
          if (targetTerms.length && !relevantNews.length) {
            context += `No ESPN news headline or description matched the target player names above.\n`;
          }
        }
      } catch {
        /* best-effort */
      }
    }

    setChatLog((prev) => [...prev, { role: 'user', text }]);
    setChatInput('');
    setChatLoading(true);
    try {
      const reply = await sendAdvisorChat(text, history, context || undefined);
      setChatLog((prev) => [...prev, { role: 'assistant', text: reply }]);
    } catch (e: any) {
      setChatLog((prev) => [
        ...prev,
        { role: 'assistant', text: `Error: ${e?.message ?? 'Chat failed'}` },
      ]);
    } finally {
      setChatLoading(false);
    }
  };
  const sendChat = () => sendChatWith(chatInput.trim());

  const connectYahoo = async () => {
    const authWindow = window.open('', '_blank');
    try {
      const url = await fetchYahooAuthUrl();
      if (authWindow) {
        authWindow.location.href = url;
      } else {
        window.location.href = url;
      }
    } catch (e: any) {
      authWindow?.close();
      setMainError(e?.message ?? 'Could not start Yahoo connection.');
    }
  };

  const myStanding = selectedUser ? standings[selectedUser.userId] : undefined;
  const myRank = selectedUser ? rankByUser[selectedUser.userId] : undefined;
  const formatStr = useMemo(() => {
    const sc = roster?.starters?.length ?? 0;
    if (sc >= 10) return 'SF';
    return 'PPR';
  }, [roster]);

  const posOverview = useMemo(() => {
    if (!roster) return [] as { pos: string; name: string; depth: number }[];
    const all = [...roster.starters, ...roster.bench];
    const byPos: Record<string, Player[]> = {};
    for (const p of all) {
      const k = (p.pos || '').toUpperCase();
      if (!k) continue;
      (byPos[k] ??= []).push(p);
    }
    const order = ['QB', 'RB', 'WR', 'TE', 'FLEX', 'K', 'DEF'];
    return order
      .filter((pos) => byPos[pos]?.length)
      .map((pos) => {
        const arr = byPos[pos]
          .slice()
          .sort((a, b) => (b.proj ?? 0) - (a.proj ?? 0));
        const best = arr[0];
        const last = best.name.split(' ').slice(-1)[0];
        return { pos, name: last, depth: arr.length };
      });
  }, [roster]);

  return (
    <>
      <div className="topbar" ref={topbarRef}>
        <div className="topbar-row1">
          <div className="brand">
            <span className="star">✦</span>Fantasy
            <span className="accent">Wizard</span>
          </div>
          <div className="field field-provider">
            <span className="field-label">Provider</span>
            <select
              value={provider}
              onChange={(e) => setProvider(e.target.value as FantasyProvider)}
            >
              <option value="sleeper">Sleeper</option>
              <option value="yahoo">Yahoo</option>
            </select>
          </div>
          <div className="field field-username">
            <span className="field-label">
              {provider === 'yahoo' ? 'Yahoo user' : 'User'}
            </span>
            <input
              value={username}
              disabled={provider === 'yahoo'}
              onChange={(e) => setUsername(e.target.value)}
            />
          </div>
          <div className="field field-season">
            <span className="field-label">Season</span>
            <input
              type="number"
              value={season}
              onChange={(e) => setSeason(parseInt(e.target.value || '2025', 10))}
            />
          </div>
          <div className="week-glass" style={{ ['--week-progress' as string]: `${((week - 1) / 17) * 100}%` }}>
            <div className="week-glass-head">
              <span className="field-label">Week</span>
              <span className="week-value">Week {week}</span>
            </div>
            <div className="week-slider-wrap">
              <input
                className="week-slider"
                type="range"
                min="1"
                max="18"
                value={week}
                aria-label="Select fantasy week"
                onChange={(e) => setWeek(parseInt(e.target.value, 10))}
              />
              <div className="week-ticks" aria-hidden="true">
                {Array.from({ length: 18 }, (_, i) => i + 1).map((w) => (
                  <button
                    key={w}
                    type="button"
                    className={`week-tick ${week === w ? 'active' : ''}`}
                    onClick={() => setWeek(w)}
                  >
                    {w}
                  </button>
                ))}
              </div>
            </div>
          </div>

          <div className="spacer" />

          <select
            className="league-select"
            value={leagueId ?? ''}
            onChange={(e) => setLeagueId(e.target.value)}
          >
            {leagues.map((l) => (
              <option key={l.leagueId} value={l.leagueId}>
                {l.name}
              </option>
            ))}
          </select>
          <button className="tb-btn" onClick={() => setShowManualTrade(true)}>
            ⇄ Trade Builder
          </button>
          {provider === 'yahoo' && (
            <button className="tb-btn secondary" onClick={connectYahoo}>
              {yahooStatus?.connected ? 'Yahoo Connected' : 'Connect Yahoo'}
            </button>
          )}
          <button className="tb-btn secondary" onClick={() => setShowAITrades(true)}>
            🤖 AI Trades
          </button>
          <button className="tb-btn secondary" onClick={() => setShowPicksModal(true)}>
            🏈 Picks
          </button>
        </div>
        <div className="topbar-row2">
          <button
            className={`pill-tab ${tab === 'roster' ? 'active' : ''}`}
            onClick={() => setTab('roster')}
          >
            roster
          </button>
          <button
            className={`pill-tab ${tab === 'matchups' ? 'active' : ''}`}
            onClick={() => setTab('matchups')}
          >
            matchups
          </button>
        </div>
      </div>

      <div className="board">
        {/* LEFT */}
        <div className="col-left">
          <div className="search">
            <span aria-hidden="true">🔎</span>
            <input
              placeholder="Search members…"
              value={memberSearch}
              onChange={(e) => setMemberSearch(e.target.value)}
            />
          </div>
          <div className="league-summary">
            <div className="stat-card teams">
              <div className="num">{users.length || '—'}</div>
              <div className="label">Teams</div>
            </div>
            <div className="stat-card week">
              <div className="num">{week}</div>
              <div className="label">Week</div>
            </div>
            <div className="stat-card format">
              <div className="num">{formatStr}</div>
              <div className="label">Format</div>
            </div>
          </div>
          <SectionLabel count={users.length}>Standings</SectionLabel>
          {loadingUsers && <div className="small">Loading members…</div>}
          {errorUsers && (
            <div className="small" style={{ color: '#ff8a8a' }}>
              {errorUsers}
            </div>
          )}
          <div className="members">
            {filteredUsers.map((u) => {
              const st = standings[u.userId];
              const games = (st?.wins ?? 0) + (st?.losses ?? 0) + (st?.ties ?? 0);
              const winPct = games > 0 ? (st!.wins + st!.ties * 0.5) / games : 0;
              const fillColor =
                winPct > 0.6
                  ? 'var(--green)'
                  : winPct > 0.4
                  ? 'var(--accent)'
                  : 'var(--red)';
              const rank = rankByUser[u.userId];
              const isTop = rank <= 3;
              return (
                <div
                  key={u.userId}
                  className={`member ${
                    selectedUser?.userId === u.userId ? 'active' : ''
                  }`}
                  onClick={() => setSelectedUser(u)}
                >
                  <div className={`rank ${isTop ? 'top' : ''}`}>{rank ?? '—'}</div>
                  <Avatar name={u.displayName} size="sm" />
                  <div className="member-info">
                    <div className="member-name">
                      {u.displayName}
                      {u.isMe && <span className="me-tag">YOU</span>}
                    </div>
                    <div className="winbar">
                      <div
                        className="winbar-fill"
                        style={{
                          width: `${Math.max(2, winPct * 100)}%`,
                          background: fillColor,
                        }}
                      />
                    </div>
                  </div>
                  <div className="member-points">
                    {st?.fpts ? st.fpts.toFixed(0) : '—'}
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* CENTER */}
        <div className="col-center">
          {mainError && (
            <div className="small" style={{ color: '#ff8a8a', marginBottom: 12 }}>
              {mainError}
            </div>
          )}

          {tab === 'roster' && selectedUser && (
            <>
              <div className="team-header">
                <Avatar name={selectedUser.displayName} size="lg" />
                <div>
                  <div className="team-name">{selectedUser.displayName}</div>
                  <div className="team-pills">
                    {myStanding && (
                      <span className="team-pill record">
                        {myStanding.wins}-{myStanding.losses}
                        {myStanding.ties ? `-${myStanding.ties}` : ''}
                      </span>
                    )}
                    {myRank && (
                      <span className="team-pill rank">
                        #{myRank} of {users.length}
                      </span>
                    )}
                    {myStanding && (
                      <span className="team-pill points">
                        {myStanding.fpts.toFixed(1)} PF
                      </span>
                    )}
                  </div>
                </div>
                <div className="team-header-right">
                  <div className="label">Week {week}</div>
                  <div className="value">
                    {selectedOpponent
                      ? `vs ${nameOf(selectedOpponent.userId)}`
                      : 'opponent —'}
                  </div>
                  <div className="subvalue">
                    {roster?.starters?.length
                      ? `${roster.starters
                          .reduce((a, p) => a + (p.proj ?? 0), 0)
                          .toFixed(1)} proj`
                      : '— proj'}
                  </div>
                </div>
              </div>

              {posOverview.length > 0 && (
                <div className="pos-overview">
                  {posOverview.map((c) => {
                    const color = posHex(c.pos);
                    return (
                      <div
                        key={c.pos}
                        className="pos-chip"
                        style={{
                          background: color + '12',
                          borderColor: color + '30',
                        }}
                      >
                        <span className="pos" style={{ color }}>
                          {c.pos}
                        </span>
                        <span className="name">{c.name}</span>
                        <span className="depth">{c.depth}</span>
                      </div>
                    );
                  })}
                </div>
              )}

              <SectionLabel count={roster?.starters.length}>Starters</SectionLabel>
              {roster?.starters.map((p) => (
                <PlayerCard
                  key={p.id}
                  p={p}
                  role="starter"
                  rosProj={rosProjections[p.id]}
                />
              )) || <div className="small">No starters</div>}

              <SectionLabel count={roster?.bench.length}>Bench</SectionLabel>
              {roster?.bench.map((p) => (
                <PlayerCard
                  key={p.id}
                  p={p}
                  role="bench"
                  rosProj={rosProjections[p.id]}
                />
              )) || <div className="small">No bench players</div>}

              {roster?.taxi && roster.taxi.length > 0 && (
                <>
                  <SectionLabel count={roster.taxi.length}>Taxi</SectionLabel>
                  {roster.taxi.map((p) => (
                    <PlayerCard
                      key={p.id}
                      p={p}
                      role="taxi"
                      rosProj={rosProjections[p.id]}
                    />
                  ))}
                </>
              )}

              <SectionLabel>Draft Picks</SectionLabel>
              {(() => {
                const myPicks = leaguePicks.filter(
                  (p) => p.currentUserId === selectedUser?.userId
                );
                if (picksLoading) return <div className="small">Loading picks…</div>;
                if (!myPicks.length)
                  return <div className="small">No future picks held.</div>;
                const seasons = [...new Set(myPicks.map((p) => p.season))].sort();
                return (
                  <>
                    {seasons.map((s) => (
                      <div key={s} style={{ marginBottom: 10 }}>
                        <div
                          className="small"
                          style={{
                            fontWeight: 700,
                            color: 'var(--accent)',
                            marginBottom: 4,
                          }}
                        >
                          {s}
                        </div>
                        <div className="pick-row">
                          {myPicks
                            .filter((p) => p.season === s)
                            .sort((a, b) => a.round - b.round)
                            .map((pick, i) => (
                              <PickChip key={i} pick={pick} />
                            ))}
                        </div>
                      </div>
                    ))}
                  </>
                );
              })()}
            </>
          )}

          {tab === 'matchups' && (
            <>
              <SectionLabel count={orderedPairs.length}>
                Week {week} Matchups
              </SectionLabel>
              {orderedPairs.length === 0 && (
                <div className="small">No matchups for this week.</div>
              )}
              {orderedPairs.map((m, idx) => {
                const selectedId = selectedUser?.userId;
                const isMine =
                  m.home.userId === selectedId || m.away.userId === selectedId;
                const expanded =
                  expandedMatchup === m.id ||
                  (idx === 0 && isMine && expandedMatchup === null);
                const homeTotal = m.home.projectedTotal ?? 0;
                const awayTotal = m.away.projectedTotal ?? 0;
                const total = homeTotal + awayTotal || 1;
                const homePct = (homeTotal / total) * 100;
                const homeWin = homeTotal > awayTotal;
                const awayWin = awayTotal > homeTotal;
                const delta = Math.abs(homeTotal - awayTotal).toFixed(1);
                return (
                  <div
                    key={m.id}
                    className={`matchup-card ${isMine ? 'selected' : ''}`}
                    onClick={() =>
                      setExpandedMatchup(expanded ? null : m.id)
                    }
                  >
                    <div className="matchup-header">
                      {isMine && (
                        <div className="your-matchup-label">Your matchup</div>
                      )}
                      <div className="matchup-grid">
                        <div className="matchup-side">
                          <div className="name">{nameOf(m.home.userId)}</div>
                          <div className={`total ${homeWin ? 'win' : ''}`}>
                            {homeTotal ? homeTotal.toFixed(1) : '—'}
                          </div>
                        </div>
                        <div className="vs-divider">
                          <div className="vs">VS</div>
                          {homeTotal > 0 && awayTotal > 0 && (
                            <div className="delta">Δ {delta}</div>
                          )}
                        </div>
                        <div className="matchup-side right">
                          <div className="name">{nameOf(m.away.userId)}</div>
                          <div className={`total ${awayWin ? 'win' : ''}`}>
                            {awayTotal ? awayTotal.toFixed(1) : '—'}
                          </div>
                        </div>
                      </div>
                      <div className="winprob">
                        <div className="winprob-bar">
                          <div
                            className="winprob-fill"
                            style={{ width: `${homePct}%` }}
                          />
                        </div>
                        <div className="winprob-labels">
                          <span>{homePct.toFixed(0)}%</span>
                          <span>{(100 - homePct).toFixed(0)}%</span>
                        </div>
                      </div>
                    </div>

                    {expanded && (
                      <div className="starters-grid">
                        <div>
                          {(m.home.starters ?? []).map((p, i) => {
                            const opp = (m.away.starters ?? [])[i];
                            const winning =
                              opp &&
                              typeof p.proj === 'number' &&
                              typeof opp.proj === 'number' &&
                              p.proj > opp.proj;
                            return (
                              <div
                                key={`h-${i}`}
                                className={`starter-row ${winning ? 'winning' : ''}`}
                              >
                                <span
                                  className="pos"
                                  style={{ color: posHex(p.pos) }}
                                >
                                  {p.pos}
                                </span>
                                <span className="name">{p.name}</span>
                                <span className="proj">
                                  {typeof p.proj === 'number'
                                    ? p.proj.toFixed(1)
                                    : '—'}
                                </span>
                              </div>
                            );
                          })}
                        </div>
                        <div>
                          {(m.away.starters ?? []).map((p, i) => {
                            const opp = (m.home.starters ?? [])[i];
                            const winning =
                              opp &&
                              typeof p.proj === 'number' &&
                              typeof opp.proj === 'number' &&
                              p.proj > opp.proj;
                            return (
                              <div
                                key={`a-${i}`}
                                className={`starter-row ${winning ? 'winning' : ''}`}
                              >
                                <span
                                  className="pos"
                                  style={{ color: posHex(p.pos) }}
                                >
                                  {p.pos}
                                </span>
                                <span className="name">{p.name}</span>
                                <span className="proj">
                                  {typeof p.proj === 'number'
                                    ? p.proj.toFixed(1)
                                    : '—'}
                                </span>
                              </div>
                            );
                          })}
                        </div>
                      </div>
                    )}
                    <button
                      className="matchup-toggle"
                      onClick={(e) => {
                        e.stopPropagation();
                        setExpandedMatchup(expanded ? null : m.id);
                      }}
                    >
                      {expanded ? '▲ Collapse' : '▼ Show starters'}
                    </button>
                  </div>
                );
              })}
            </>
          )}
        </div>

        {/* RIGHT: Chat */}
        <div className="col-right">
          <div className="chat-header">
            <div className="chat-title">
              <span className="chat-status-dot" />
              AI Advisor
            </div>
            <div className="chat-subtitle">
              Ask about lineups, trades, or this week.
            </div>
          </div>

          {chatLog.length === 0 && !chatLoading && (
            <div className="quick-chips">
              {QUICK_PROMPTS.map((q) => (
                <button
                  key={q}
                  className="quick-chip"
                  onClick={() => sendChatWith(q)}
                  disabled={chatLoading}
                >
                  {q}
                </button>
              ))}
            </div>
          )}

          <div className="chatlog">
            {chatLog.map((m, i) => (
              <div key={i} className={`msg ${m.role}`}>
                <div className="msg-role">
                  {m.role === 'user' ? 'You' : 'Advisor'}
                </div>
                <div className="msg-body">
                  <ReactMarkdown>{m.text}</ReactMarkdown>
                </div>
              </div>
            ))}
            {chatLoading && (
              <div className="msg assistant">
                <div className="msg-role">Advisor</div>
                <div className="msg-body">
                  <TypingDots />
                </div>
              </div>
            )}
          </div>

          <div className="chatin">
            <input
              placeholder="Ask anything…"
              value={chatInput}
              onChange={(e) => setChatInput(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') sendChat();
              }}
            />
            <button
              className="send-btn"
              onClick={sendChat}
              disabled={chatLoading || !chatInput.trim()}
              aria-label="Send"
            >
              ↑
            </button>
          </div>
        </div>
      </div>

      {showManualTrade && selectedUser && roster && (
        <ManualTradeModal
          myTeam={selectedUser.displayName}
          myRoster={roster}
          myPicks={roster.picks ?? []}
          allRosters={allRosters}
          leagueContext={buildLeagueContext(allRosters, week, season)}
          onClose={() => setShowManualTrade(false)}
        />
      )}
      {showAITrades && selectedUser && (
        <AITradesModal
          myTeam={selectedUser.displayName}
          leagueContext={buildLeagueContext(allRosters, week, season)}
          onClose={() => setShowAITrades(false)}
        />
      )}
      {showPicksModal && (
        <div className="modal-overlay" onClick={() => setShowPicksModal(false)}>
          <div
            className="modal"
            onClick={(e) => e.stopPropagation()}
            style={{ maxWidth: 720 }}
          >
            <div className="modal-header">
              <span style={{ fontWeight: 700, fontSize: 16 }}>
                🏈 League Draft Picks
              </span>
              <button
                className="modal-close"
                onClick={() => setShowPicksModal(false)}
              >
                ✕
              </button>
            </div>
            {picksLoading ? (
              <div className="small">Loading picks…</div>
            ) : (
              <PicksGrid picks={leaguePicks} />
            )}
          </div>
        </div>
      )}
    </>
  );
}

const PicksGrid: React.FC<{ picks: LeaguePick[] }> = ({ picks }) => {
  if (!picks.length) return <div className="small">No future picks found.</div>;
  const seasons = [...new Set(picks.map((p) => p.season))].sort();
  return (
    <>
      {seasons.map((season) => {
        const rounds = [
          ...new Set(picks.filter((p) => p.season === season).map((p) => p.round)),
        ].sort((a, b) => a - b);
        return (
          <div key={season} style={{ marginBottom: 18 }}>
            <div
              style={{
                fontSize: 12,
                fontWeight: 700,
                color: 'var(--accent)',
                marginBottom: 8,
                letterSpacing: '.06em',
              }}
            >
              {season} SEASON
            </div>
            {rounds.map((round) => {
              const rp = picks.filter(
                (p) => p.season === season && p.round === round
              );
              return (
                <div key={round} style={{ marginBottom: 8 }}>
                  <div
                    className="small"
                    style={{ marginBottom: 4, fontWeight: 600 }}
                  >
                    Round {round}
                  </div>
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                    {rp.map((pick, i) => (
                      <div
                        key={i}
                        className={`pick-chip ${pick.traded ? 'traded' : 'own'}`}
                        style={{ minWidth: 130 }}
                      >
                        <span className="pri">{pick.currentOwnerName}</span>
                        <span className="sub">
                          {pick.traded
                            ? `via ${pick.originalOwnerName}`
                            : 'own pick'}
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
              );
            })}
          </div>
        );
      })}
    </>
  );
};

function buildLeagueContext(
  allRosters: TeamRoster[],
  week: number,
  season: number
): string {
  if (!allRosters.length) return '';
  let ctx = `PPR Dynasty league, Week ${week}, Season ${season}\n\n--- ALL ROSTERS ---\n`;
  for (const team of allRosters) {
    ctx += `\n${team.displayName}:\n`;
    if (team.starters.length)
      ctx += `  Starters: ${team.starters
        .map(
          (p) =>
            `${p.pos} ${p.name}${cleanTeam(p.team) ? ` (${cleanTeam(p.team)})` : ''} ${p.proj.toFixed(1)}pts`
        )
        .join(' | ')}\n`;
    if (team.bench.length)
      ctx += `  Bench: ${team.bench.map((p) => `${p.pos} ${p.name}`).join(', ')}\n`;
    if (team.taxi.length)
      ctx += `  Taxi: ${team.taxi.map((p) => `${p.pos} ${p.name}`).join(', ')}\n`;
  }
  return ctx;
}

const rootElement = document.getElementById('root');
if (rootElement) {
  createRoot(rootElement).render(
    <React.StrictMode>
      <App />
    </React.StrictMode>
  );
}

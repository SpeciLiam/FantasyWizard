import React, { useEffect, useMemo, useState } from "react";
import {
  Player,
  DraftPick,
  Roster,
  MatchupsResponse,
  fetchLeagues,
  fetchRoster,
  fetchMatchups
} from "./api";
import {
  getLeagueUsers,
  getRosters,
  getWeekMatchups,
  buildMatchupPairs,
  getSleeperPlayersDict
} from "./sleeper";

export type LeagueUser = {
  userId: string;
  displayName: string;
  avatarUrl?: string;
  isMe?: boolean;
};

// --- Matchups ---
type MatchupSide = {
  userId: string;           // Sleeper user_id for that side
  projectedTotal?: number;  // optional, if you calculate this
  starters?: Player[];      // now provided for each side! hydrate as array in rendering
};

type MatchupPair = {
  id: string;
  home: MatchupSide;
  away: MatchupSide;
};



// UI helpers and row renderers
const Title: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <div className="title">{children}</div>
);

const Row: React.FC<{ children: React.ReactNode; alt?: boolean; className?: string }> = ({
  children,
  alt,
  className
}) => <div className={`row${alt ? " alt" : ""}${className ? " " + className : ""}`}>{children}</div>;

const PlayerRow: React.FC<{ p: Player }> = ({ p }) => (
  <Row>
    <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
      <div className="pos">{p.pos}</div>
      <div>
        <strong>{p.name}</strong> {p.team ? <span className="small"> • {p.team}</span> : null}
      </div>
    </div>
    <div className="small">
      <span>{p.proj?.toFixed(1) ?? "—"} pts</span>
      <span style={{ marginLeft: 8 }}>val {p.value?.toFixed(0) ?? "—"}</span>
    </div>
  </Row>
);

const PickRow: React.FC<{ pick: DraftPick }> = ({ pick }) => (
  <Row alt>
    <div>
      <strong>
        {pick.season} — Round {pick.round}
      </strong>
    </div>
    <div className="small">
      {pick.owner === pick.originalOwner ? "Own" : `Via ${pick.originalOwner ?? "?"}`}
      {pick.traded ? " • traded" : ""}
    </div>
  </Row>
);



// ------ Main App ------
export default function App() {
  // UI selection
  const [tab, setTab] = useState<"roster" | "matchups">("roster");

  // Controls
  const [username, setUsername] = useState("SpeciLiam");
  const [season, setSeason] = useState<number>(2025);
  const [week, setWeek] = useState<number>(1);

  // Data
  const [leagues, setLeagues] = useState<Array<{ leagueId: string; name: string }>>([]);
  const [leagueId, setLeagueId] = useState<string | null>(null);

  const [users, setUsers] = useState<LeagueUser[]>([]);
  const [loadingUsers, setLoadingUsers] = useState(false);

  const [errorUsers, setErrorUsers] = useState<string | null>(null);
  const [memberSearch, setMemberSearch] = useState("");
  const [selectedUser, setSelectedUser] = useState<LeagueUser | null>(null);

  const [roster, setRoster] = useState<Roster | null>(null);

  // Loading/error state for main data
  const [mainError, setMainError] = useState<string | null>(null);

  // Chat
  const [chatInput, setChatInput] = useState("");
  const [chatLog, setChatLog] = useState<{ role: "user" | "assistant"; text: string }[]>([]);

  // Load leagues when username or season change
  useEffect(() => {
    setLeagues([]);
    setLeagueId(null);
    setUsers([]);
    setRoster(null);
    setMainError(null);
    fetchLeagues(username, season)
      .then(ls => {
        setLeagues(ls);
        setLeagueId(ls[0]?.leagueId ?? null);
      })
      .catch(e => setMainError("Could not load leagues: " + String(e)));
    // eslint-disable-next-line
  }, [username, season]);

  // Load users when league changes
  useEffect(() => {
    if (!leagueId) {
      setUsers([]);
      setSelectedUser(null);
      setRoster(null);
      return;
    }
    setUsers([]);
    setSelectedUser(null);
    setRoster(null);
    setErrorUsers(null);
    setLoadingUsers(true);
    getLeagueUsers(leagueId)
      .then(apiUsers => {
        const mapped: LeagueUser[] = apiUsers.map(u => ({
          userId: u.user_id,
          displayName: u.display_name,
          avatarUrl: u.avatar ? `https://sleepercdn.com/avatars/thumbs/${u.avatar}` : undefined,
          isMe: false
        }));
        setUsers(mapped);
        if (!selectedUser && mapped.length) setSelectedUser(mapped[0]);
      })
      .catch(e => setErrorUsers(e?.message ?? "Failed to fetch users"))
      .finally(() => setLoadingUsers(false));
  }, [leagueId]);

  // Load roster when user or week changes
  useEffect(() => {
    if (!leagueId || !selectedUser) {
      setRoster(null);
      return;
    }
    setRoster(null);
    setMainError(null);
    fetchRoster(leagueId, selectedUser.userId, week)
      .then(setRoster)
      .catch(e => setMainError("Could not load roster: " + String(e)));
  }, [leagueId, selectedUser, week]);

  const filteredUsers = useMemo(
    () => users.filter((u: LeagueUser) => u.displayName.toLowerCase().includes(memberSearch.toLowerCase())),
    [users, memberSearch]
  );

  // Get display name by userId
  const nameOf = (uid?: string) =>
    users.find((u) => u.userId === uid)?.displayName ?? "—";

    // --- Matchups state with direct Sleeper join ---
  const [pairs, setPairs] = useState<MatchupPair[]>([]);
  const [loading, setLoading] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  // Load matchups+rosters+player info on leagueId/week, then build matchup pairs with player cards & projected scores
  useEffect(() => {
    let ok = true;
    (async () => {
      if (!leagueId || !week) return;
      setLoading(true); setErr(null);
      try {
        const [rows, rosters, playerDict] = await Promise.all([
          getWeekMatchups(leagueId, week),
          getRosters(leagueId),
          getSleeperPlayersDict(),
        ]);

        // Build up a map from roster_id to their starters as Player[]
        const startersMap = new Map<number, Player[]>();
        for (const roster of rosters) {
          let starters: Player[] = [];
          // Find this roster in the matchup rows for this week for a possible starters list
          const row = rows.find((r: any) => r.roster_id === roster.roster_id);
          if (row?.starters && Array.isArray(row.starters)) {
            starters = row.starters.map((pid: string) => {
              const info = playerDict?.[pid];
              return info
                ? {
                    id: pid,
                    name: info.full_name ?? pid,
                    pos: info.position ?? "",
                    team: info.team ?? "",
                    // will default to undefined, since playerDict has no projections
                    proj: undefined,
                    value: undefined
                  }
                : { id: pid, name: pid, pos: "", team: "", proj: undefined, value: undefined };
            });
          }
          startersMap.set(roster.roster_id, starters);
        }

        // Build matchups with full Player[] and correct projected total for each side
        const rawPairs = buildMatchupPairs(rows, rosters);
        const pairsWithPlayers: MatchupPair[] = rawPairs.map(pair => {
          // Find roster id for each user
          const getRosterForUserId = (userId: string) =>
            rosters.find((r: any) => r.owner_id === userId)?.roster_id;
          const homeRoster = getRosterForUserId(pair.home.userId);
          const awayRoster = getRosterForUserId(pair.away.userId);
          const homeStarters = homeRoster ? startersMap.get(homeRoster) ?? [] : [];
          const awayStarters = awayRoster ? startersMap.get(awayRoster) ?? [] : [];

          // Calculate correct projected total for each side
          const sumProj = (players: Player[]) =>
            players.reduce((sum, p) => sum + (typeof p.proj === "number" ? p.proj : 0), 0);

          return {
            ...pair,
            home: {
              ...pair.home,
              starters: homeStarters,
              projectedTotal: sumProj(homeStarters)
            },
            away: {
              ...pair.away,
              starters: awayStarters,
              projectedTotal: sumProj(awayStarters)
            }
          };
        });

        if (!ok) return;
        setPairs(pairsWithPlayers);
      } catch (e: any) {
        if (!ok) return;
        setErr(e?.message ?? "Failed to load matchups");
      } finally {
        if (!ok) return;
        setLoading(false);
      }
    })();
    return () => { ok = false; };
  }, [leagueId, week]);

  // Reorder so the selected member’s matchup is first
  const orderedPairs = useMemo(() => {
    if (!selectedUser) return pairs;
    const me = selectedUser.userId;
    const idx = pairs.findIndex(p => p.home.userId === me || p.away.userId === me);
    if (idx <= 0) return pairs; // already first or not found
    const arr = pairs.slice();
    const [hit] = arr.splice(idx, 1);
    arr.unshift(hit);
    return arr;
  }, [pairs, selectedUser]);

  // Chat send (still placeholder)
  const sendChat = () => {
    const text = chatInput.trim();
    if (!text) return;
    setChatLog((prev) => [...prev, { role: "user", text }]);
    setChatInput("");
    setTimeout(() => {
      setChatLog((prev) => [...prev, { role: "assistant", text: "LLM disabled (wire later)." }]);
    }, 250);
  };

  // UI main
  return (
    <>
      {/* Top bar */}
      <div className="topbar">
        <label>Username</label>
        <input
          className="input"
          value={username}
          onChange={(e: React.ChangeEvent<HTMLInputElement>) => setUsername(e.target.value)}
        />
        <label>Season</label>
        <input
          className="input"
          type="number"
          value={season}
          onChange={(e: React.ChangeEvent<HTMLInputElement>) => setSeason(parseInt(e.target.value || "2025", 10))}
        />
        <label>Week</label>
        <input
          className="input"
          type="number"
          value={week}
          onChange={(e: React.ChangeEvent<HTMLInputElement>) => setWeek(parseInt(e.target.value || "1", 10))}
        />
        <button
          className="button"
          onClick={() =>
            fetchLeagues(username, season)
              .then(ls => {
                setLeagues(ls);
                setLeagueId(ls[0]?.leagueId ?? null);
              })
              .catch(e => setMainError("Could not reload leagues: " + String(e)))
          }
        >
          Load Leagues
        </button>

        <div className="spacer"></div>

        <select
          className="select"
          value={leagueId ?? ""}
          onChange={(e: React.ChangeEvent<HTMLSelectElement>) => setLeagueId(e.target.value)}
        >
          {leagues.map((l: { leagueId: string; name: string }) => (
            <option key={l.leagueId} value={l.leagueId}>
              {l.name}
            </option>
          ))}
        </select>

        <div style={{ display: "flex", gap: 6 }}>
          <button
            className={`toggle${tab === "roster" ? " active" : ""}`}
            onClick={() => setTab("roster")}
            style={{ background: tab === "roster" ? "var(--accent)" : undefined, color: tab === "roster" ? "white" : undefined }}
          >
            Roster
          </button>
          <button
            className={`toggle${tab === "matchups" ? " active" : ""}`}
            onClick={() => setTab("matchups")}
            style={{ background: tab === "matchups" ? "var(--accent)" : undefined, color: tab === "matchups" ? "white" : undefined }}
          >
            Matchups
          </button>
        </div>
      </div>

      {/* 3-column board */}
      <div className="board-wrap">
        <div className="board">
          {/* LEFT: League members */}
          <div className="panel">
            <div className="card">
              <div className="search">
                <span role="img" aria-label="search">🔎</span>
                <input
                  className="input"
                  style={{ flex: 1, background: "transparent", border: "none", padding: 0 }}
                  placeholder="Search members…"
                  value={memberSearch}
                  onChange={(e: React.ChangeEvent<HTMLInputElement>) => setMemberSearch(e.target.value)}
                />
              </div>
              {loadingUsers && <div className="small">Loading members…</div>}
              {errorUsers && <div className="small" style={{color:"#ff8a8a"}}>{errorUsers}</div>}
              <div className="members">
                {(filteredUsers.length ? filteredUsers : users).map((u: LeagueUser) => (
                  <div
                    key={u.userId}
                    className={`member ${selectedUser?.userId === u.userId ? "active" : ""}`}
                    onClick={() => setSelectedUser(u)}
                  >
                    {u.avatarUrl ? (
                      <img
                        className="avatar"
                        src={u.avatarUrl}
                        alt=""
                        onError={(e) => { (e.currentTarget as HTMLImageElement).style.display = "none"; }}
                      />
                    ) : (
                      <div style={{ width: 28, height: 28, borderRadius: 999, background: "#2b2d31" }} />
                    )}
                    <div style={{ display: "flex", flexDirection: "column" }}>
                      <strong>{u.displayName}</strong>
                      {u.isMe && <span className="badge">You</span>}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>

          {/* CENTER PANEL: Roster or Matchups */}
          <div className="panel center">
            {tab === "roster" && (
              <>
                <div className="card">
                  <Title>Starters</Title>
                  {roster?.starters.map((p: Player) => <PlayerRow key={p.id} p={p} />) || <div className="small">No starters</div>}
                </div>
                <div className="card">
                  <Title>Bench</Title>
                  {roster?.bench.map((p: Player) => <PlayerRow key={p.id} p={p} />) || <div className="small">No bench players</div>}
                </div>
                <div className="card">
                  <Title>Taxi (if enabled)</Title>
                  {roster?.taxi.length
                    ? roster.taxi.map((p: Player) => <PlayerRow key={p.id} p={p} />)
                    : <div className="small">No taxi players.</div>
                  }
                </div>
                <div className="card">
                  <Title>Draft Picks</Title>
                  {(roster?.picks?.length && roster.picks.map((pick: DraftPick, i: number) => <PickRow key={i} pick={pick} />)) ||
                    <div className="small">No picks.</div>}
                </div>
              </>
            )}
            {tab === "matchups" && (
              <div className="card">
                <div className="title">Week {week} Matchups</div>
{orderedPairs.length === 0 && (
  <div className="small">No matchups for this week.</div>
)}
{orderedPairs.map((m: MatchupPair, idx: number) => {
  const selectedId = selectedUser?.userId;
  const isSelectedMatchup = m.home.userId === selectedId || m.away.userId === selectedId;

  return (
    <div
      key={m.id}
      className="card"
      style={{
        border: isSelectedMatchup && idx === 0
          ? "2.5px solid var(--accent)"
          : "1px solid var(--border)",
        borderRadius: 12,
        padding: 8,
        background: "transparent",
        marginBottom: "12px",
        boxShadow: isSelectedMatchup && idx === 0
          ? "0 0 0 2px var(--accent-light)" : undefined
      }}
    >
      {idx === 0 && isSelectedMatchup && (
        <div className="small" style={{ color: "var(--accent)", fontWeight: 600, marginBottom: 2 }}>
          Selected member matchup
        </div>
      )}
      <div className="row"
           style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8 }}>
        {/* Home side */}
        <div>
          <div style={{ fontWeight: 600, color: "#1454a3", fontSize: 12 }}>
            Home ({nameOf(m.home.userId)})
          </div>
          <div className="small">
            Projected:{" "}
            <span style={{ fontWeight: 700, color: "#008328" }}>
              {m.home.projectedTotal?.toFixed(1) ?? "—"}
            </span>
          </div>
        </div>
        {/* Away side */}
        <div style={{ textAlign: "right" }}>
          <div style={{ fontWeight: 600, color: "#cf2a27", fontSize: 12 }}>
            Away ({nameOf(m.away.userId)})
          </div>
          <div className="small">
            Projected:{" "}
            <span style={{ fontWeight: 700, color: "#008328" }}>
              {m.away.projectedTotal?.toFixed(1) ?? "—"}
            </span>
          </div>
        </div>
      </div>
      {(() => {
        const homeStarters = m.home.starters ?? [];
        const awayStarters = m.away.starters ?? [];
        const maxCount = Math.max(homeStarters.length, awayStarters.length);

        // helper: pad array to max with nulls (for ghost slots)
        const padArr = (arr: any[], n: number) =>
          [...arr, ...Array(n - arr.length).fill(null)];

        // compute additive subtotal for each card
        const addSums = (players: Player[]) => {
          let sum = 0;
          return players.map((p: Player | null) => {
            if (p && typeof p.proj === "number") sum += p.proj;
            return { player: p, subtotal: p ? sum : null };
          });
        };

        const homeAdds = addSums(padArr(homeStarters, maxCount));
        const awayAdds = addSums(padArr(awayStarters, maxCount));

        return (
          <div
            style={{
              display: "grid",
              gridTemplateColumns: "1fr 1fr",
              gap: 8,
              marginTop: 12,
              alignItems: "stretch",
            }}
          >
            {/* Home starters */}
            <div className="player-col" style={{ display: "flex", flexDirection: "column", gap: 6 }}>
              {homeAdds.map(({ player, subtotal }, i: number) =>
                player ? (
                  <div className="row" key={player.id ?? i} style={{ minHeight: 54, background: "#191a21" }}>
                    <div>
                      <span className="pos" style={{ fontWeight: 600 }}>{player.pos}</span>{" "}
                      <strong>{player.name}</strong> {player.team && <span className="small">• {player.team}</span>}
                      {typeof player.value === "number" && (
                        <span
                          style={{
                            background: "#222b38",
                            color: "#74d7ff",
                            marginLeft: 10,
                            fontSize: 11,
                            padding: "2px 8px",
                            borderRadius: 9,
                            fontWeight: 700,
                          }}
                        >
                          value: {player.value.toFixed(0)}
                        </span>
                      )}
                    </div>
                    <div style={{ textAlign: "right" }}>
                      <span style={{ fontWeight: 700, color: "#22c55e", marginRight: 12 }}>
                        {typeof player.proj === "number" ? player.proj.toFixed(1) : "—"}
                      </span>
                      {subtotal !== null && (
                        <span className="small" style={{ color: "#888", fontSize: 11 }}>
                          Σ {subtotal.toFixed(1)}
                        </span>
                      )}
                    </div>
                  </div>
                ) : (
                  <div className="row" key={`ghost${i}`} style={{
                    minHeight: 54,
                    opacity: 0.25,
                    background: "var(--row2)",
                    borderStyle: "dashed",
                  }}></div>
                )
              )}
            </div>
            {/* Away starters */}
            <div className="player-col" style={{ display: "flex", flexDirection: "column", gap: 6 }}>
              {awayAdds.map(({ player, subtotal }, i: number) =>
                player ? (
                  <div className="row" key={player.id ?? i} style={{ minHeight: 54, background: "#191a21" }}>
                    <div>
                      <span className="pos" style={{ fontWeight: 600 }}>{player.pos}</span>{" "}
                      <strong>{player.name}</strong> {player.team && <span className="small">• {player.team}</span>}
                      {typeof player.value === "number" && (
                        <span
                          style={{
                            background: "#222b38",
                            color: "#74d7ff",
                            marginLeft: 10,
                            fontSize: 11,
                            padding: "2px 8px",
                            borderRadius: 9,
                            fontWeight: 700,
                          }}
                        >
                          value: {player.value.toFixed(0)}
                        </span>
                      )}
                    </div>
                    <div style={{ textAlign: "right" }}>
                      <span style={{ fontWeight: 700, color: "#22c55e", marginRight: 12 }}>
                        {typeof player.proj === "number" ? player.proj.toFixed(1) : "—"}
                      </span>
                      {subtotal !== null && (
                        <span className="small" style={{ color: "#888", fontSize: 11 }}>
                          Σ {subtotal.toFixed(1)}
                        </span>
                      )}
                    </div>
                  </div>
                ) : (
                  <div className="row" key={`ghost${i}`} style={{
                    minHeight: 54,
                    opacity: 0.25,
                    background: "var(--row2)",
                    borderStyle: "dashed",
                  }}></div>
                )
              )}
            </div>
          </div>
        );
      })()}
    </div>
  );
})}
              </div>
            )}
          </div>

          {/* RIGHT: Advisor Chat */}
          <div className="panel chat">
            <div className="card" style={{ paddingBottom: 8 }}>
              <Title>Advisor Chat</Title>
              <div className="small">💬 LLM disabled (for now). Ask things like “Who should I start?”</div>
            </div>

            <div className="chatlog">
              {chatLog.length === 0 && <div className="small">No messages yet.</div>}
              {chatLog.map((m: { role: "user" | "assistant"; text: string }, i: number) => (
                <div key={i} className={`msg ${m.role}`}>
                  <div className="small" style={{ marginBottom: 4, textTransform: "uppercase" }}>{m.role}</div>
                  {m.text}
                </div>
              ))}
            </div>

            <div className="chatin">
              <input
                className="input"
                placeholder="Type your question…"
                value={chatInput}
                onChange={(e: React.ChangeEvent<HTMLInputElement>) => setChatInput(e.target.value)}
                onKeyDown={(e: React.KeyboardEvent<HTMLInputElement>) => e.key === "Enter" ? sendChat() : null}
              />
              <button className="button" onClick={sendChat}>Send</button>
            </div>
          </div>
        </div>
      </div>
    </>
  );
}

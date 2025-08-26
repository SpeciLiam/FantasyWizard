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
import { getLeagueUsers, avatarUrlFromId } from "./sleeper";

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
  starters?: Player[];      // optional: use if you render player rows
};

type Matchup = {
  id: string;
  week: number;
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
          avatarUrl: avatarUrlFromId(u.avatar),
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

  // --- Matchups state: always from the backend/API, never mock data ---
  const [matchups, setMatchups] = useState<Matchup[]>([]);

  // Fetch matchups for current league & week
  useEffect(() => {
    if (!leagueId || !week) {
      setMatchups([]);
      return;
    }
    fetchMatchups(leagueId, week)
      .then((resp: MatchupsResponse) => {
        // Convert to legacy Matchup[] shape if needed (for now, pass through pair format).
        if (Array.isArray(resp.pairs)) {
          setMatchups(
            resp.pairs.map((pair, i) => ({
              id: `${pair.home.userId}_${pair.away.userId}_w${week}`,
              week: week,
              home: pair.home,
              away: pair.away,
            }))
          );
        } else {
          setMatchups([]);
        }
      })
      .catch(() => setMatchups([]));
  }, [leagueId, week]);

  // Reorder: selected user's game first
  const orderedMatchups = React.useMemo(() => {
    if (!selectedUser) return matchups;
    const me = selectedUser.userId;
    const meFirst = [...matchups].sort((a, b) => {
      const aHas = a.home.userId === me || a.away.userId === me;
      const bHas = b.home.userId === me || b.away.userId === me;
      if (aHas && !bHas) return -1;
      if (!aHas && bHas) return 1;
      return 0;
    });
    return meFirst;
  }, [matchups, selectedUser]);

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
                {orderedMatchups.length === 0 && (
                  <div className="small">No matchups for this week.</div>
                )}
                {orderedMatchups.map((m) => {
                  const me = selectedUser?.userId;
                  const isMine = m.home.userId === me || m.away.userId === me;
                  return (
                    <div
                      key={m.id}
                      className="card"
                      style={{
                        border: isMine ? "2px solid var(--accent)" : "1px solid var(--border)",
                        borderRadius: 12,
                        padding: 8,
                        background: "transparent",
                        marginBottom: "12px"
                      }}
                    >
                      <div
                        className="row"
                        style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8 }}
                      >
                        <div>
                          <div className="small" style={{ marginBottom: 4 }}>
                            Home — <strong>{nameOf(m.home.userId)}</strong>
                          </div>
                          <div className="small">
                            Projected Total: {m.home.projectedTotal?.toFixed(1) ?? "—"}
                          </div>
                        </div>
                        <div style={{ textAlign: "right" }}>
                          <div className="small" style={{ marginBottom: 4 }}>
                            Away — <strong>{nameOf(m.away.userId)}</strong>
                          </div>
                          <div className="small">
                            Projected Total: {m.away.projectedTotal?.toFixed(1) ?? "—"}
                          </div>
                        </div>
                      </div>
                      <div
                        style={{
                          display: "grid",
                          gridTemplateColumns: "1fr 1fr",
                          gap: 8,
                          marginTop: 8,
                        }}
                      >
                        <div>
                          {(m.home.starters ?? []).map((p, i) => (
                            <PlayerRow key={p.id ?? i} p={p} />
                          ))}
                        </div>
                        <div>
                          {(m.away.starters ?? []).map((p, i) => (
                            <PlayerRow key={p.id ?? i} p={p} />
                          ))}
                        </div>
                      </div>
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

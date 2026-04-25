import React, { useState } from 'react';
import ReactMarkdown from 'react-markdown';
import { Player, DraftPick, TeamRoster, LeaguePick } from './api';

const API_BASE = import.meta.env.VITE_API_BASE ?? 'http://localhost:8080';

// ─── Types ───────────────────────────────────────────────────────────────────

type Asset = { name: string; pos: string; team?: string; season?: number; round?: number; isPick?: boolean };

type TradeEvaluation = {
  verdict?: 'WIN' | 'LOSS' | 'FAIR';
  score?: number;
  summary?: string;
  prosForMe?: string[];
  consForMe?: string[];
  recommendation?: 'ACCEPT' | 'DECLINE' | 'COUNTER';
  counterSuggestion?: string;
  error?: string;
};

type TradeSuggestion = {
  targetManager: string;
  myAssets: Asset[];
  theirAssets: Asset[];
  rationale: string;
  winProbability: string;
};

// ─── Helpers ─────────────────────────────────────────────────────────────────

function AssetPill({ a, selected, onClick }: { a: Asset; selected: boolean; onClick?: () => void }) {
  return (
    <div
      onClick={onClick}
      style={{
        display: 'flex', alignItems: 'center', gap: 8,
        padding: '7px 12px', borderRadius: 10, marginBottom: 5, cursor: onClick ? 'pointer' : 'default',
        background: selected ? '#1a3a1a' : '#191a21',
        border: `1.5px solid ${selected ? '#22c55e' : '#2a2c31'}`,
        transition: 'all .12s',
      }}
    >
      <span style={{ fontSize: 11, fontWeight: 700, color: '#888', width: 28 }}>
        {a.isPick ? '🏈' : a.pos}
      </span>
      <span style={{ flex: 1, fontSize: 13 }}>
        {a.isPick ? `${a.season} R${a.round}` : a.name}
        {a.team ? <span style={{ color: '#888', fontSize: 11, marginLeft: 5 }}>• {a.team}</span> : null}
      </span>
      {onClick && (
        <span style={{ fontSize: 16, color: selected ? '#22c55e' : '#555' }}>
          {selected ? '✓' : '+'}
        </span>
      )}
    </div>
  );
}

function VerdictBanner({ ev }: { ev: TradeEvaluation }) {
  if (ev.error) return <div style={{ color: '#f87171', padding: 12 }}>Error: {ev.error}</div>;
  const color = ev.verdict === 'WIN' ? '#22c55e' : ev.verdict === 'LOSS' ? '#ef4444' : '#f59e0b';
  const bg    = ev.verdict === 'WIN' ? '#0f2a0f' : ev.verdict === 'LOSS' ? '#2a0f0f' : '#2a200a';
  return (
    <div style={{ background: bg, border: `1.5px solid ${color}`, borderRadius: 12, padding: '12px 16px', marginTop: 12 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 6 }}>
        <span style={{ fontWeight: 800, fontSize: 18, color }}>{ev.verdict}</span>
        {typeof ev.score === 'number' && (
          <span style={{ fontSize: 12, color: '#888' }}>score: {ev.score > 0 ? '+' : ''}{ev.score}/10</span>
        )}
        <span style={{ marginLeft: 'auto', fontWeight: 700, color, fontSize: 13 }}>{ev.recommendation}</span>
      </div>
      {ev.summary && <p style={{ margin: '0 0 8px', fontSize: 13, color: '#e0e0e0' }}>{ev.summary}</p>}
      {ev.prosForMe?.length ? (
        <div style={{ marginBottom: 4 }}>
          <div style={{ fontSize: 11, color: '#22c55e', fontWeight: 700, marginBottom: 2 }}>PROS FOR YOU</div>
          {ev.prosForMe.map((p, i) => <div key={i} style={{ fontSize: 12, color: '#ccc' }}>✓ {p}</div>)}
        </div>
      ) : null}
      {ev.consForMe?.length ? (
        <div style={{ marginBottom: 4 }}>
          <div style={{ fontSize: 11, color: '#ef4444', fontWeight: 700, marginBottom: 2 }}>CONS</div>
          {ev.consForMe.map((c, i) => <div key={i} style={{ fontSize: 12, color: '#ccc' }}>✗ {c}</div>)}
        </div>
      ) : null}
      {ev.counterSuggestion && (
        <div style={{ marginTop: 6, fontSize: 12, color: '#f59e0b' }}>💡 Counter: {ev.counterSuggestion}</div>
      )}
    </div>
  );
}

// ─── Trade View Modal (shared) ────────────────────────────────────────────────

function TradeViewModal({
  myTeam, theirTeam, myAssets, theirAssets, leagueContext, onClose,
}: {
  myTeam: string; theirTeam: string;
  myAssets: Asset[]; theirAssets: Asset[];
  leagueContext?: string; onClose: () => void;
}) {
  const [evaluation, setEvaluation] = useState<TradeEvaluation | null>(null);
  const [loading, setLoading] = useState(false);

  const evaluate = async () => {
    setLoading(true);
    try {
      const res = await fetch(`${API_BASE}/api/trade/evaluate`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ myTeam, theirTeam, myAssets, theirAssets, leagueContext }),
      });
      setEvaluation(await res.json());
    } catch (e: any) {
      setEvaluation({ error: e.message });
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal" onClick={e => e.stopPropagation()} style={{ maxWidth: 640 }}>
        <div className="modal-header">
          <span style={{ fontWeight: 700, fontSize: 16 }}>Trade Proposal</span>
          <button className="modal-close" onClick={onClose}>✕</button>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16, marginBottom: 12 }}>
          {/* Left — you give */}
          <div>
            <div className="trade-side-label give">YOU GIVE · {myTeam}</div>
            {myAssets.length === 0
              ? <div className="small" style={{ padding: 8 }}>Nothing</div>
              : myAssets.map((a, i) => <AssetPill key={i} a={a} selected />)}
          </div>
          {/* Right — you receive */}
          <div>
            <div className="trade-side-label receive">YOU GET · {theirTeam}</div>
            {theirAssets.length === 0
              ? <div className="small" style={{ padding: 8 }}>Nothing</div>
              : theirAssets.map((a, i) => <AssetPill key={i} a={a} selected />)}
          </div>
        </div>

        {!evaluation && (
          <button className="button" style={{ width: '100%' }} onClick={evaluate} disabled={loading}>
            {loading ? 'Evaluating…' : '🤖 Evaluate with AI'}
          </button>
        )}
        {evaluation && <VerdictBanner ev={evaluation} />}
        {evaluation && (
          <button
            className="button" style={{ width: '100%', marginTop: 8, background: '#333', border: '1px solid #555' }}
            onClick={() => setEvaluation(null)}
          >
            Re-evaluate
          </button>
        )}
      </div>
    </div>
  );
}

// ─── Manual Trade Builder ─────────────────────────────────────────────────────

export function ManualTradeModal({
  myTeam, myRoster, myPicks, allRosters, leagueContext, onClose,
}: {
  myTeam: string;
  myRoster: { starters: Player[]; bench: Player[]; taxi: Player[] };
  myPicks: DraftPick[];
  allRosters: TeamRoster[];
  leagueContext?: string;
  onClose: () => void;
}) {
  const [opponent, setOpponent] = useState<TeamRoster | null>(null);
  const [mySelected, setMySelected] = useState<Set<string>>(new Set());
  const [theirSelected, setTheirSelected] = useState<Set<string>>(new Set());
  const [viewTrade, setViewTrade] = useState(false);

  const myPlayers: Asset[] = [
    ...myRoster.starters, ...myRoster.bench, ...myRoster.taxi,
  ].map(p => ({ name: p.name ?? '', pos: p.pos ?? '', team: p.team ?? '' }));

  const myPickAssets: Asset[] = (myPicks ?? []).map(p => ({
    name: `${p.season} R${p.round}`, pos: 'PICK', isPick: true,
    season: p.season, round: p.round,
  }));

  const theirPlayers: Asset[] = opponent
    ? [...opponent.starters, ...opponent.bench, ...opponent.taxi].map(p => ({
        name: p.name ?? '', pos: p.pos ?? '', team: p.team ?? '',
      }))
    : [];

  const toggle = (set: Set<string>, key: string, setter: (s: Set<string>) => void) => {
    const next = new Set(set);
    next.has(key) ? next.delete(key) : next.add(key);
    setter(next);
  };

  const buildAssets = (all: Asset[], selected: Set<string>): Asset[] =>
    all.filter((_, i) => selected.has(String(i)));

  const myGive    = buildAssets([...myPlayers, ...myPickAssets], mySelected);
  const theirGive = buildAssets(theirPlayers, theirSelected);

  return (
    <>
      <div className="modal-overlay" onClick={onClose}>
        <div className="modal" onClick={e => e.stopPropagation()} style={{ maxWidth: 680 }}>
          <div className="modal-header">
            <span style={{ fontWeight: 700, fontSize: 16 }}>Build a Trade</span>
            <button className="modal-close" onClick={onClose}>✕</button>
          </div>

          {/* Opponent selector */}
          <div style={{ marginBottom: 14 }}>
            <div className="small" style={{ marginBottom: 6, fontWeight: 600 }}>Select opponent</div>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
              {allRosters.filter(r => r.displayName !== myTeam).map(r => (
                <button
                  key={r.userId}
                  onClick={() => { setOpponent(r); setTheirSelected(new Set()); }}
                  style={{
                    padding: '5px 12px', borderRadius: 999, fontSize: 12, cursor: 'pointer',
                    background: opponent?.userId === r.userId ? 'var(--accent)' : 'var(--row)',
                    color: opponent?.userId === r.userId ? '#fff' : 'var(--fg)',
                    border: `1.5px solid ${opponent?.userId === r.userId ? 'var(--accent)' : 'var(--border)'}`,
                  }}
                >
                  {r.displayName}
                </button>
              ))}
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16, maxHeight: 380, overflow: 'auto' }}>
            {/* My assets */}
            <div>
              <div className="trade-side-label give">YOU GIVE · {myTeam}</div>
              <div className="small" style={{ marginBottom: 6 }}>Click to select</div>
              {[...myPlayers, ...myPickAssets].map((a, i) => (
                <AssetPill key={i} a={a} selected={mySelected.has(String(i))}
                  onClick={() => toggle(mySelected, String(i), setMySelected)} />
              ))}
            </div>
            {/* Their assets */}
            <div>
              <div className="trade-side-label receive">YOU GET · {opponent?.displayName ?? '—'}</div>
              {!opponent
                ? <div className="small">Select an opponent first</div>
                : <>
                    <div className="small" style={{ marginBottom: 6 }}>Click to select</div>
                    {theirPlayers.map((a, i) => (
                      <AssetPill key={i} a={a} selected={theirSelected.has(String(i))}
                        onClick={() => toggle(theirSelected, String(i), setTheirSelected)} />
                    ))}
                  </>
              }
            </div>
          </div>

          <div style={{ display: 'flex', gap: 8, marginTop: 14 }}>
            <button
              className="button"
              style={{ flex: 1 }}
              disabled={!opponent || (myGive.length === 0 && theirGive.length === 0)}
              onClick={() => setViewTrade(true)}
            >
              View Trade
            </button>
          </div>
        </div>
      </div>

      {viewTrade && opponent && (
        <TradeViewModal
          myTeam={myTeam} theirTeam={opponent.displayName}
          myAssets={myGive} theirAssets={theirGive}
          leagueContext={leagueContext}
          onClose={() => setViewTrade(false)}
        />
      )}
    </>
  );
}

// ─── AI Trade Suggestions ─────────────────────────────────────────────────────

export function AITradesModal({
  myTeam, leagueContext, onClose,
}: {
  myTeam: string; leagueContext?: string; onClose: () => void;
}) {
  const [suggestions, setSuggestions] = useState<TradeSuggestion[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [viewIdx, setViewIdx] = useState<number | null>(null);

  const load = async () => {
    setLoading(true); setError(null);
    try {
      const res = await fetch(`${API_BASE}/api/trade/suggest`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ myTeam, leagueContext }),
      });
      const data = await res.json();
      if (data.error) throw new Error(data.error);
      setSuggestions(data.suggestions ?? []);
    } catch (e: any) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  };

  const probColor = (p: string) =>
    p === 'HIGH' ? '#22c55e' : p === 'MEDIUM' ? '#f59e0b' : '#ef4444';

  return (
    <>
      <div className="modal-overlay" onClick={onClose}>
        <div className="modal" onClick={e => e.stopPropagation()} style={{ maxWidth: 620 }}>
          <div className="modal-header">
            <span style={{ fontWeight: 700, fontSize: 16 }}>🤖 AI Trade Suggestions</span>
            <button className="modal-close" onClick={onClose}>✕</button>
          </div>

          {suggestions.length === 0 && !loading && (
            <button className="button" style={{ width: '100%', marginBottom: 12 }} onClick={load}>
              Generate Trade Ideas for {myTeam}
            </button>
          )}
          {loading && (
            <div style={{ textAlign: 'center', padding: 24, color: '#888' }}>
              Analysing league rosters…
            </div>
          )}
          {error && <div style={{ color: '#f87171', marginBottom: 12 }}>Error: {error}</div>}

          {suggestions.map((s, i) => (
            <div key={i} className="trade-suggestion-card">
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 }}>
                <span style={{ fontWeight: 700, fontSize: 14 }}>Trade with {s.targetManager}</span>
                <span style={{ fontSize: 11, fontWeight: 700, color: probColor(s.winProbability) }}>
                  {s.winProbability} acceptance
                </span>
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10, marginBottom: 8 }}>
                <div>
                  <div className="trade-side-label give" style={{ marginBottom: 4 }}>YOU GIVE</div>
                  {s.myAssets.map((a, j) => <AssetPill key={j} a={a} selected />)}
                </div>
                <div>
                  <div className="trade-side-label receive" style={{ marginBottom: 4 }}>YOU GET</div>
                  {s.theirAssets.map((a, j) => <AssetPill key={j} a={a} selected />)}
                </div>
              </div>
              <div className="small" style={{ marginBottom: 8, lineHeight: 1.5 }}>{s.rationale}</div>
              <button className="button" style={{ width: '100%', fontSize: 12, padding: '6px 0' }}
                onClick={() => setViewIdx(i)}>
                View Trade & Evaluate
              </button>
            </div>
          ))}

          {suggestions.length > 0 && (
            <button
              className="button"
              style={{ width: '100%', marginTop: 8, background: '#333', border: '1px solid #555' }}
              onClick={load}
            >
              Regenerate
            </button>
          )}
        </div>
      </div>

      {viewIdx !== null && suggestions[viewIdx] && (
        <TradeViewModal
          myTeam={myTeam}
          theirTeam={suggestions[viewIdx].targetManager}
          myAssets={suggestions[viewIdx].myAssets}
          theirAssets={suggestions[viewIdx].theirAssets}
          leagueContext={leagueContext}
          onClose={() => setViewIdx(null)}
        />
      )}
    </>
  );
}

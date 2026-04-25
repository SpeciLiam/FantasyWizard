import React, { useState } from 'react';
import ReactMarkdown from 'react-markdown';
import { Player, DraftPick, TeamRoster } from './api';

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

const POS_COLOR: Record<string, string> = {
  QB: 'var(--qb)', RB: 'var(--rb)', WR: 'var(--wr)',
  TE: 'var(--te)', K: 'var(--k)', DEF: 'var(--def)', FLEX: 'var(--flex)',
};
function posColor(pos?: string): string {
  return POS_COLOR[pos?.toUpperCase() ?? ''] ?? 'var(--fg3)';
}

function PosBadge({ pos }: { pos: string }) {
  const color = posColor(pos);
  return (
    <span style={{
      display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
      width: 28, height: 18, borderRadius: 5,
      background: color + '22', color,
      fontSize: 9, fontWeight: 700, letterSpacing: '.04em', flexShrink: 0,
    }}>{pos}</span>
  );
}

function AssetRow({ a, selected, onClick }: { a: Asset; selected: boolean; onClick?: () => void }) {
  return (
    <div
      onClick={onClick}
      style={{
        display: 'flex', alignItems: 'center', gap: 8,
        padding: '7px 10px', borderRadius: 9, marginBottom: 4,
        cursor: onClick ? 'pointer' : 'default',
        background: selected ? (onClick ? 'rgba(91,127,255,.1)' : 'var(--surface3)') : 'var(--surface3)',
        border: `1px solid ${selected ? 'var(--accent)' : 'var(--border)'}`,
        transition: 'all .12s',
      }}
    >
      {a.isPick
        ? <span style={{ fontSize: 11, fontWeight: 700, color: 'var(--yellow)' }}>🏈</span>
        : <PosBadge pos={a.pos} />}
      <div style={{ flex: 1, minWidth: 0 }}>
        <span style={{ fontSize: 12, fontWeight: 500, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', display: 'block' }}>
          {a.isPick ? `${a.season} Round ${a.round}` : a.name}
        </span>
        {a.team && <span style={{ color: 'var(--fg3)', fontSize: 10 }}>{a.team}</span>}
      </div>
      {onClick && (
        <span style={{ fontSize: 14, color: selected ? 'var(--accent)' : 'var(--fg3)', flexShrink: 0 }}>
          {selected ? '✓' : '+'}
        </span>
      )}
    </div>
  );
}

function VerdictBanner({ ev }: { ev: TradeEvaluation }) {
  if (ev.error) return <div style={{ color: 'var(--red)', padding: 12 }}>Error: {ev.error}</div>;
  const color = ev.verdict === 'WIN' ? 'var(--green)' : ev.verdict === 'LOSS' ? 'var(--red)' : 'var(--yellow)';
  const colorHex = ev.verdict === 'WIN' ? '#3fcf8e' : ev.verdict === 'LOSS' ? '#f87171' : '#fbbf24';
  return (
    <div style={{ background: colorHex + '14', border: `1px solid ${colorHex}40`, borderRadius: 14, padding: '16px 20px', marginTop: 12 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 8 }}>
        <span style={{ fontSize: 24, fontWeight: 800, color }}>{ev.verdict}</span>
        {typeof ev.score === 'number' && (
          <span style={{ fontSize: 11, color: 'var(--fg3)' }}>score: {ev.score > 0 ? '+' : ''}{ev.score}/10</span>
        )}
        <span style={{ marginLeft: 'auto', fontSize: 11, fontWeight: 700, color, background: colorHex + '20', padding: '3px 10px', borderRadius: 99 }}>
          {ev.recommendation}
        </span>
      </div>
      {ev.summary && <p style={{ margin: '0 0 10px', fontSize: 13, color: 'var(--fg2)', lineHeight: 1.6 }}>{ev.summary}</p>}
      {ev.prosForMe?.length ? (
        <div style={{ marginBottom: 6 }}>
          <div style={{ fontSize: 10, color: 'var(--green)', fontWeight: 700, marginBottom: 3, letterSpacing: '.06em' }}>PROS FOR YOU</div>
          {ev.prosForMe.map((p, i) => <div key={i} style={{ fontSize: 12, color: 'var(--fg2)' }}>✓ {p}</div>)}
        </div>
      ) : null}
      {ev.consForMe?.length ? (
        <div style={{ marginBottom: 6 }}>
          <div style={{ fontSize: 10, color: 'var(--red)', fontWeight: 700, marginBottom: 3, letterSpacing: '.06em' }}>CONS</div>
          {ev.consForMe.map((c, i) => <div key={i} style={{ fontSize: 12, color: 'var(--fg2)' }}>✗ {c}</div>)}
        </div>
      ) : null}
      {ev.counterSuggestion && (
        <div style={{ marginTop: 8, fontSize: 12, color: 'var(--yellow)' }}>💡 Counter: {ev.counterSuggestion}</div>
      )}
    </div>
  );
}

// ─── Trade View Modal ─────────────────────────────────────────────────────────

function TradeViewModal({ myTeam, theirTeam, myAssets, theirAssets, leagueContext, onClose }: {
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
      <div className="modal" onClick={(e: React.MouseEvent) => e.stopPropagation()} style={{ maxWidth: 640 }}>
        <div className="modal-header">
          <span style={{ fontWeight: 800, fontSize: 16 }}>Trade Proposal</span>
          <button className="modal-close" onClick={onClose}>✕</button>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16, marginBottom: 16 }}>
          <div>
            <div className="trade-side-label give">YOU GIVE · {myTeam}</div>
            {myAssets.length === 0
              ? <div className="small" style={{ padding: 8 }}>Nothing selected</div>
              : myAssets.map((a, i) => <AssetRow key={i} a={a} selected />)}
          </div>
          <div>
            <div className="trade-side-label receive">YOU GET · {theirTeam}</div>
            {theirAssets.length === 0
              ? <div className="small" style={{ padding: 8 }}>Nothing selected</div>
              : theirAssets.map((a, i) => <AssetRow key={i} a={a} selected />)}
          </div>
        </div>

        {!evaluation && (
          <button className="button" style={{ width: '100%' }} onClick={evaluate} disabled={loading}>
            {loading ? 'Evaluating…' : '✦ Evaluate with AI'}
          </button>
        )}
        {evaluation && <VerdictBanner ev={evaluation} />}
        {evaluation && (
          <button
            style={{ width: '100%', marginTop: 8, padding: '9px 16px', borderRadius: 10, border: '1px solid var(--border2)', background: 'var(--surface3)', color: 'var(--fg2)', fontSize: 13, cursor: 'pointer', fontFamily: 'inherit', fontWeight: 600 }}
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

export function ManualTradeModal({ myTeam, myRoster, myPicks, allRosters, leagueContext, onClose }: {
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

  const myPlayers: Asset[] = [...myRoster.starters, ...myRoster.bench, ...myRoster.taxi]
    .map(p => ({ name: p.name ?? '', pos: p.pos ?? '', team: p.team ?? '' }));
  const myPickAssets: Asset[] = (myPicks ?? []).map(p => ({
    name: `${p.season} R${p.round}`, pos: 'PICK', isPick: true, season: p.season, round: p.round,
  }));
  const allMyAssets = [...myPlayers, ...myPickAssets];

  const theirPlayers: Asset[] = opponent
    ? [...opponent.starters, ...opponent.bench, ...opponent.taxi].map(p => ({ name: p.name ?? '', pos: p.pos ?? '', team: p.team ?? '' }))
    : [];

  const toggle = (set: Set<string>, key: string, setter: (s: Set<string>) => void) => {
    const next = new Set(set);
    next.has(key) ? next.delete(key) : next.add(key);
    setter(next);
  };

  const myGive    = allMyAssets.filter((_, i) => mySelected.has(String(i)));
  const theirGive = theirPlayers.filter((_, i) => theirSelected.has(String(i)));

  return (
    <>
      <div className="modal-overlay" onClick={onClose}>
        <div className="modal" onClick={(e: React.MouseEvent) => e.stopPropagation()} style={{ maxWidth: 680 }}>
          <div className="modal-header">
            <div>
              <div style={{ fontWeight: 800, fontSize: 16 }}>Build a Trade</div>
              <div style={{ color: 'var(--fg3)', fontSize: 11, marginTop: 2 }}>Select assets from each side to propose</div>
            </div>
            <button className="modal-close" onClick={onClose}>✕</button>
          </div>

          {/* Opponent selector */}
          <div style={{ marginBottom: 16 }}>
            <div style={{ fontSize: 10, fontWeight: 700, color: 'var(--fg3)', letterSpacing: '.08em', marginBottom: 8, textTransform: 'uppercase' }}>Select Opponent</div>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
              {allRosters.filter(r => r.displayName !== myTeam).map(r => (
                <button
                  key={r.userId}
                  onClick={() => { setOpponent(r); setTheirSelected(new Set()); }}
                  style={{
                    padding: '5px 12px', borderRadius: 99, fontSize: 12, cursor: 'pointer', fontFamily: 'inherit',
                    background: opponent?.userId === r.userId ? 'var(--accent)' : 'var(--surface3)',
                    color: opponent?.userId === r.userId ? '#fff' : 'var(--fg2)',
                    border: `1px solid ${opponent?.userId === r.userId ? 'var(--accent)' : 'var(--border)'}`,
                    fontWeight: 600,
                  }}
                >
                  {r.displayName}
                </button>
              ))}
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16, maxHeight: 380, overflowY: 'auto' }}>
            {/* My assets */}
            <div>
              <div className="trade-side-label give">You Give · {myTeam}</div>
              {allMyAssets.map((a, i) => (
                <AssetRow key={i} a={a} selected={mySelected.has(String(i))}
                  onClick={() => toggle(mySelected, String(i), setMySelected)} />
              ))}
            </div>
            {/* Their assets */}
            <div>
              <div className="trade-side-label receive">You Get · {opponent?.displayName ?? '—'}</div>
              {!opponent
                ? <div className="small">Select an opponent first</div>
                : theirPlayers.map((a, i) => (
                    <AssetRow key={i} a={a} selected={theirSelected.has(String(i))}
                      onClick={() => toggle(theirSelected, String(i), setTheirSelected)} />
                  ))}
            </div>
          </div>

          <button
            className="button"
            style={{ width: '100%', marginTop: 16 }}
            disabled={!opponent || (myGive.length === 0 && theirGive.length === 0)}
            onClick={() => setViewTrade(true)}
          >
            View Trade
          </button>
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

export function AITradesModal({ myTeam, leagueContext, onClose }: {
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
    p === 'HIGH' ? 'var(--green)' : p === 'MEDIUM' ? 'var(--yellow)' : 'var(--red)';

  return (
    <>
      <div className="modal-overlay" onClick={onClose}>
        <div className="modal" onClick={(e: React.MouseEvent) => e.stopPropagation()} style={{ maxWidth: 620 }}>
          <div className="modal-header">
            <div>
              <div style={{ fontWeight: 800, fontSize: 16 }}>🤖 AI Trade Suggestions</div>
              <div style={{ color: 'var(--fg3)', fontSize: 11, marginTop: 2 }}>AI-generated trade ideas for {myTeam}</div>
            </div>
            <button className="modal-close" onClick={onClose}>✕</button>
          </div>

          {suggestions.length === 0 && !loading && (
            <button className="button" style={{ width: '100%', marginBottom: 12 }} onClick={load}>
              Generate Trade Ideas
            </button>
          )}
          {loading && (
            <div style={{ textAlign: 'center', padding: 32, color: 'var(--fg3)' }}>
              <div style={{ marginBottom: 8 }}>Analysing league rosters…</div>
              <span style={{ display: 'inline-flex', gap: 4 }}>
                {[0, 1, 2].map(i => (
                  <span key={i} style={{ display: 'inline-block', width: 5, height: 5, borderRadius: '50%', background: 'var(--fg3)', animation: `bounce 1s ${i * .15}s ease-in-out infinite` }} />
                ))}
              </span>
            </div>
          )}
          {error && <div style={{ color: 'var(--red)', marginBottom: 12, fontSize: 12 }}>Error: {error}</div>}

          {suggestions.map((s, i) => (
            <div key={i} className="trade-suggestion-card">
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 10 }}>
                <span style={{ fontWeight: 700, fontSize: 14 }}>Trade with {s.targetManager}</span>
                <span style={{ fontSize: 11, fontWeight: 700, color: probColor(s.winProbability) }}>
                  {s.winProbability} acceptance
                </span>
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10, marginBottom: 10 }}>
                <div>
                  <div className="trade-side-label give" style={{ marginBottom: 6 }}>YOU GIVE</div>
                  {s.myAssets.map((a, j) => <AssetRow key={j} a={a} selected />)}
                </div>
                <div>
                  <div className="trade-side-label receive" style={{ marginBottom: 6 }}>YOU GET</div>
                  {s.theirAssets.map((a, j) => <AssetRow key={j} a={a} selected />)}
                </div>
              </div>
              <div className="small" style={{ marginBottom: 10, lineHeight: 1.55 }}>{s.rationale}</div>
              <button className="button" style={{ width: '100%', fontSize: 12, padding: '7px 0' }}
                onClick={() => setViewIdx(i)}>
                View & Evaluate Trade
              </button>
            </div>
          ))}

          {suggestions.length > 0 && (
            <button
              style={{ width: '100%', marginTop: 8, padding: '9px 16px', borderRadius: 10, border: '1px solid var(--border2)', background: 'var(--surface3)', color: 'var(--fg2)', fontSize: 13, cursor: 'pointer', fontFamily: 'inherit', fontWeight: 600 }}
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

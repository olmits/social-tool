import type { RadarStat } from "./radarDisplay";

/** The stat tiles. No deltas — see `radarStats` for why. */
export function RadarStats({ stats }: { stats: RadarStat[] }) {
  return (
    <div className="mb-5.5 grid grid-cols-2 gap-3.5 md:grid-cols-3">
      {stats.map((stat) => (
        <div
          key={stat.label}
          className="rounded-xl border border-border bg-background p-4"
        >
          <div className="mb-2 text-xs text-muted-foreground">{stat.label}</div>
          <div className="text-2xl font-semibold tracking-tight tabular-nums">
            {stat.value}
          </div>
        </div>
      ))}
    </div>
  );
}

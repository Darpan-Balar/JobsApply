import { useEffect, useState } from "react";
import { Link } from "react-router";
import { api, unwrap, type Schemas } from "../api/client";
import { Badge, Card, Empty, ErrorBanner, Field, Loading, Notice } from "../components/ui";
import { useAction, useResource } from "../lib/useResource";

type Eligibility = "ELIGIBLE" | "NOT_ELIGIBLE" | "UNKNOWN";

export function MatchesPage() {
  const profiles = useResource(() => unwrap(api.GET("/api/v1/me/candidate-profiles")), []);
  const roles = useResource(() => unwrap(api.GET("/api/v1/role-profiles")), []);
  const [candidate, setCandidate] = useState("");
  const [role, setRole] = useState("");
  const [eligibility, setEligibility] = useState<Eligibility | "">("ELIGIBLE");
  const [report, setReport] = useState<Schemas["MatchRunReport"]>();
  const run = useAction();

  useEffect(() => {
    if (!candidate && profiles.data?.[0]?.key) setCandidate(profiles.data[0].key);
  }, [profiles.data, candidate]);
  useEffect(() => {
    if (!role && roles.data?.[0]?.key) setRole(roles.data[0].key);
  }, [roles.data, role]);

  const matches = useResource(
    () =>
      candidate && role
        ? unwrap(
            api.GET("/api/v1/me/matches", {
              params: { query: { candidateProfile: candidate, roleProfile: role, eligibility: eligibility || undefined, size: 50 } },
            }),
          )
        : Promise.resolve(undefined),
    [candidate, role, eligibility],
  );

  return (
    <>
      <h1>Matches</h1>
      <Card>
        <div className="toolbar">
          <Field label="Candidate profile">
            <select value={candidate} onChange={(e) => setCandidate(e.target.value)}>
              {profiles.data?.map((p) => (
                <option key={p.key} value={p.key}>
                  {p.displayName ?? p.key}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Role profile">
            <select value={role} onChange={(e) => setRole(e.target.value)}>
              {roles.data?.map((r) => (
                <option key={r.key} value={r.key}>
                  {r.key}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Eligibility">
            <select value={eligibility} onChange={(e) => setEligibility(e.target.value as Eligibility | "")}>
              <option value="ELIGIBLE">Eligible</option>
              <option value="UNKNOWN">Unknown</option>
              <option value="NOT_ELIGIBLE">Not eligible</option>
              <option value="">All</option>
            </select>
          </Field>
          <button
            disabled={run.busy || !candidate || !role}
            onClick={() =>
              void run.run(async () => {
                setReport(await unwrap(api.POST("/api/v1/me/matches/run", { body: { candidateProfile: candidate, roleProfile: role } })));
                matches.reload();
              })
            }
          >
            {run.busy ? "Matching…" : "Match all open jobs"}
          </button>
        </div>
        <ErrorBanner error={run.error ?? matches.error} />
        {report && (
          <Notice tone="success">
            Evaluated {report.evaluated}: {report.eligible} eligible, {report.unknown} unknown, {report.notEligible} not eligible
            {report.excludedByPreference ? `, ${report.excludedByPreference} skipped (employers you ignore)` : ""}.
          </Notice>
        )}
      </Card>
      <Card title="Results">
        {matches.loading ? (
          <Loading />
        ) : !matches.data?.items?.length ? (
          <Empty>No matches yet. Run matching above.</Empty>
        ) : (
          <table>
            <thead>
              <tr>
                <th>Score</th>
                <th>Job</th>
                <th>Employer</th>
                <th>Eligibility</th>
                <th>Why</th>
              </tr>
            </thead>
            <tbody>
              {matches.data.items.map((m) => (
                <tr key={m.id}>
                  <td className="num">{m.score == null ? "—" : Math.round(m.score)}</td>
                  <td>
                    <Link to={`/jobs/${m.postingId}`}>{m.postingTitle}</Link>
                  </td>
                  <td>{m.employerName}</td>
                  <td>
                    <Badge value={m.eligibility} />
                  </td>
                  <td className="small-text">{m.result?.reasons?.slice(0, 2).join("; ")}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>
    </>
  );
}

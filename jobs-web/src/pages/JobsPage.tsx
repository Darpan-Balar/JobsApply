import { useState, type FormEvent } from "react";
import { Link, useSearchParams } from "react-router";
import { api, unwrap } from "../api/client";
import { Badge, Card, Empty, ErrorBanner, Loading } from "../components/ui";
import { formatDateTime, humanize } from "../lib/format";
import { useResource } from "../lib/useResource";

type Availability = "OPEN" | "CLOSED" | "UNKNOWN";
type Workplace = "REMOTE" | "HYBRID" | "ONSITE" | "UNKNOWN";

export function JobsPage() {
  const [params, setParams] = useSearchParams();
  const q = params.get("q") ?? "";
  const employer = params.get("employer") ?? "";
  const availabilityChoice = params.get("availability") ?? "OPEN";
  const availability = availabilityChoice === "any" ? undefined : (availabilityChoice as Availability);
  const workplace = (params.get("workplace") ?? "") as Workplace | "";
  const page = Number(params.get("page") ?? "0");
  const [draft, setDraft] = useState({ q, employer });

  const postings = useResource(
    () =>
      unwrap(
        api.GET("/api/v1/postings", {
          params: {
            query: {
              q: q || undefined,
              employer: employer || undefined,
              availability,
              workplace: workplace || undefined,
              page,
              size: 25,
            },
          },
        }),
      ),
    [q, employer, availability, workplace, page],
  );

  const update = (changes: Record<string, string>) => {
    const next = new URLSearchParams(params);
    Object.entries(changes).forEach(([k, v]) => (v ? next.set(k, v) : next.delete(k)));
    if (!("page" in changes)) next.delete("page");
    setParams(next);
  };

  const search = (e: FormEvent) => {
    e.preventDefault();
    update({ q: draft.q.trim(), employer: draft.employer.trim() });
  };

  const result = postings.data;
  return (
    <>
      <h1>Jobs</h1>
      <Card>
        <form className="toolbar" onSubmit={search}>
          <input aria-label="Search" placeholder="Title or keyword" value={draft.q} onChange={(e) => setDraft({ ...draft, q: e.target.value })} />
          <input aria-label="Employer" placeholder="Employer key" value={draft.employer} onChange={(e) => setDraft({ ...draft, employer: e.target.value })} />
          <select aria-label="Availability" value={availabilityChoice} onChange={(e) => update({ availability: e.target.value })}>
            <option value="OPEN">Open</option>
            <option value="UNKNOWN">Unknown</option>
            <option value="CLOSED">Closed</option>
            <option value="any">Any availability</option>
          </select>
          <select aria-label="Workplace" value={workplace} onChange={(e) => update({ workplace: e.target.value })}>
            <option value="">Any workplace</option>
            <option value="REMOTE">Remote</option>
            <option value="HYBRID">Hybrid</option>
            <option value="ONSITE">On-site</option>
          </select>
          <button type="submit">Search</button>
        </form>
      </Card>
      <ErrorBanner error={postings.error} />
      <Card title={result ? `${result.totalItems ?? 0} jobs` : undefined}>
        {postings.loading ? (
          <Loading />
        ) : !result?.items?.length ? (
          <Empty>No jobs match. Synchronize employers from Admin, or widen the search.</Empty>
        ) : (
          <>
            <table>
              <thead>
                <tr>
                  <th>Title</th>
                  <th>Employer</th>
                  <th>Location</th>
                  <th>Workplace</th>
                  <th>Availability</th>
                  <th>First seen</th>
                </tr>
              </thead>
              <tbody>
                {result.items.map((p) => (
                  <tr key={p.id}>
                    <td>
                      <Link to={`/jobs/${p.id}`}>{p.title}</Link>
                    </td>
                    <td>{p.employerName}</td>
                    <td>{p.locations?.map((l) => l.rawText ?? [l.city, l.countryCode].filter(Boolean).join(", ")).join(" · ")}</td>
                    <td>{humanize(p.workplaceType)}</td>
                    <td>
                      <Badge value={p.availability} />
                    </td>
                    <td>{formatDateTime(p.firstSeenAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <div className="pager">
              <button className="secondary small" disabled={page <= 0} onClick={() => update({ page: String(page - 1) })}>
                Previous
              </button>
              <span>
                Page {page + 1} of {Math.max(1, result.totalPages ?? 1)}
              </span>
              <button className="secondary small" disabled={page + 1 >= (result.totalPages ?? 1)} onClick={() => update({ page: String(page + 1) })}>
                Next
              </button>
            </div>
          </>
        )}
      </Card>
    </>
  );
}

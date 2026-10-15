import { Link } from "react-router";
import { api, unwrap, type Schemas } from "../api/client";
import { Badge, Card, Empty, ErrorBanner, Loading } from "../components/ui";
import { formatDate, formatDateTime, humanize } from "../lib/format";
import { useAction, useResource } from "../lib/useResource";

type Status = NonNullable<Schemas["PreparationViewsApplicationSummary"]["status"]>;

/** What needs the candidate's attention, grouped by what they would do next. */
const GROUPS: { title: string; statuses: Status[] }[] = [
  { title: "Ready for your review", statuses: ["READY_FOR_REVIEW"] },
  { title: "Approved, not yet submitted", statuses: ["APPROVED"] },
  { title: "Needs your verification", statuses: ["SUBMITTING", "SUBMISSION_UNCONFIRMED"] },
  { title: "Submitted", statuses: ["SUBMITTED"] },
];

export function DashboardPage() {
  const applications = useResource(() => unwrap(api.GET("/api/v1/me/applications")), []);
  const summary = useResource(() => unwrap(api.GET("/api/v1/me/tracking/summary")), []);
  const action = useAction();

  const complete = (id?: string) =>
    id && void action.run(async () => {
      await unwrap(api.POST("/api/v1/me/follow-ups/{id}/done", { params: { path: { id } } }));
      summary.reload();
    });

  const all = applications.data ?? [];
  return (
    <>
      <h1>Overview</h1>
      <ErrorBanner error={applications.error ?? summary.error ?? action.error} />
      <div className="stats">
        {GROUPS.map((g) => (
          <div className="stat" key={g.title}>
            <span className="stat-value">{all.filter((a) => a.status && g.statuses.includes(a.status)).length}</span>
            <span className="stat-label">{g.title}</span>
          </div>
        ))}
        <div className="stat">
          <span className="stat-value">{summary.data?.overdueFollowUps ?? 0}</span>
          <span className="stat-label">Overdue follow-ups</span>
        </div>
      </div>

      <div className="columns">
        <Card title="Needs you">
          {applications.loading ? (
            <Loading />
          ) : (
            <ApplicationList
              items={all.filter((a) => a.status && ["READY_FOR_REVIEW", "APPROVED", "SUBMITTING", "SUBMISSION_UNCONFIRMED", "FAILED"].includes(a.status))}
              empty="Nothing waiting for you."
            />
          )}
        </Card>
        <div>
          <Card title="Upcoming interviews">
            {summary.data?.upcomingInterviews?.length ? (
              <ul className="list">
                {summary.data.upcomingInterviews.map((i) => (
                  <li key={i.id}>
                    <strong>{formatDateTime(i.scheduledAt)}</strong> · {humanize(i.kind)}
                    {i.location && <span className="muted"> · {i.location}</span>}
                    <div>
                      <Link to={`/applications/${i.applicationId}`}>Open application</Link>
                    </div>
                  </li>
                ))}
              </ul>
            ) : (
              <Empty>No interviews in the next two weeks.</Empty>
            )}
          </Card>
          <Card title="Follow-ups due" actions={<Link to="/tracking">All follow-ups</Link>}>
            {summary.data?.openFollowUps?.length ? (
              <ul className="list">
                {summary.data.openFollowUps.map((f) => (
                  <li key={f.id} className="row-between">
                    <span>
                      <span className={f.overdue ? "danger-text" : undefined}>{formatDate(f.dueOn)}</span> · {f.note}
                    </span>
                    <button className="secondary small" disabled={action.busy} onClick={() => complete(f.id)}>
                      Done
                    </button>
                  </li>
                ))}
              </ul>
            ) : (
              <Empty>No follow-ups due this week.</Empty>
            )}
          </Card>
        </div>
      </div>
    </>
  );
}

export function ApplicationList({ items, empty }: { items: Schemas["PreparationViewsApplicationSummary"][]; empty: string }) {
  if (items.length === 0) return <Empty>{empty}</Empty>;
  return (
    <table>
      <thead>
        <tr>
          <th>Job</th>
          <th>Employer</th>
          <th>Status</th>
          <th>Since</th>
        </tr>
      </thead>
      <tbody>
        {items.map((a) => (
          <tr key={a.id}>
            <td>
              <Link to={`/applications/${a.id}`}>{a.postingTitle ?? "Application"}</Link>
            </td>
            <td>{a.employerName}</td>
            <td>
              <Badge value={a.status} />
            </td>
            <td>{formatDateTime(a.statusChangedAt)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

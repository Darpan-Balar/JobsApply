import { useParams } from "react-router";
import { api, unwrap } from "../api/client";
import { ApplicationTracking } from "../components/ApplicationTracking";
import { PackageReview } from "../components/PackageReview";
import { SubmissionPanel } from "../components/SubmissionPanel";
import { Card, ErrorBanner, Loading, Notice } from "../components/ui";
import { formatDateTime, humanize } from "../lib/format";
import { useResource } from "../lib/useResource";

const EDITABLE = new Set(["MATCHED", "PREPARED", "READY_FOR_REVIEW", "APPROVED", "FAILED"]);

export function ApplicationDetailPage() {
  const { id = "" } = useParams();
  const detail = useResource(() => unwrap(api.GET("/api/v1/me/applications/{id}", { params: { path: { id } } })), [id]);

  if (detail.loading && !detail.data) return <Loading />;
  if (detail.error) return <ErrorBanner error={detail.error} />;
  const data = detail.data!;
  const application = data.application!;
  const status = application.status ?? "";
  return (
    <>
      <h1>{data.posting?.title ?? "Application"}</h1>
      <p className="subtitle">{data.posting?.employerName}</p>
      <div className="columns">
        <div>
          {data.currentPackage ? (
            <PackageReview applicationId={id} pkg={data.currentPackage} editable={EDITABLE.has(status)} onChanged={detail.reload} />
          ) : (
            <Notice>No package has been prepared for this application.</Notice>
          )}
        </div>
        <div>
          <SubmissionPanel detail={data} onChanged={detail.reload} />
          {(status === "SUBMITTED" || status === "WITHDRAWN") && <ApplicationTracking applicationId={id} submitted={status === "SUBMITTED"} />}
          <Card title="History">
            <ol className="timeline">
              {[...(application.history ?? [])].reverse().map((h, n) => (
                <li key={n}>
                  <strong>{humanize(h.to)}</strong> <span className="muted small-text">{formatDateTime(h.at)} · {h.actor === "USER" ? "you" : "system"}</span>
                  {h.reason && <div className="small-text">{h.reason}</div>}
                </li>
              ))}
            </ol>
          </Card>
        </div>
      </div>
    </>
  );
}

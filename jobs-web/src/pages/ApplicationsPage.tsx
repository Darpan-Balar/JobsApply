import { useState } from "react";
import { api, unwrap } from "../api/client";
import { Card, ErrorBanner, Loading } from "../components/ui";
import { humanize } from "../lib/format";
import { useResource } from "../lib/useResource";
import { ApplicationList } from "./DashboardPage";

export function ApplicationsPage() {
  const applications = useResource(() => unwrap(api.GET("/api/v1/me/applications")), []);
  const [status, setStatus] = useState("");
  const all = applications.data ?? [];
  const statuses = [...new Set(all.map((a) => a.status).filter(Boolean))] as string[];
  return (
    <>
      <h1>Applications</h1>
      <ErrorBanner error={applications.error} />
      <Card
        actions={
          <select aria-label="Status" value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="">All statuses</option>
            {statuses.map((s) => (
              <option key={s} value={s}>
                {humanize(s)}
              </option>
            ))}
          </select>
        }
      >
        {applications.loading ? (
          <Loading />
        ) : (
          <ApplicationList items={status ? all.filter((a) => a.status === status) : all} empty="No applications yet. Prepare one from a job." />
        )}
      </Card>
    </>
  );
}

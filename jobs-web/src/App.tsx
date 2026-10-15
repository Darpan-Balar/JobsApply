import { Navigate, Route, Routes } from "react-router";
import { useSession } from "./auth/session";
import { Layout } from "./components/Layout";
import { Loading } from "./components/ui";
import { AccountPage } from "./pages/AccountPage";
import { AdminPage } from "./pages/AdminPage";
import { ApplicationDetailPage } from "./pages/ApplicationDetailPage";
import { ApplicationsPage } from "./pages/ApplicationsPage";
import { DashboardPage } from "./pages/DashboardPage";
import { JobDetailPage } from "./pages/JobDetailPage";
import { JobsPage } from "./pages/JobsPage";
import { LoginPage } from "./pages/LoginPage";
import { MatchesPage } from "./pages/MatchesPage";
import { ProfilesPage } from "./pages/ProfilesPage";
import { SetupPage } from "./pages/SetupPage";
import { TrackingPage } from "./pages/TrackingPage";

/** Everything except sign-in and setup requires a signed-in user; the server enforces the same. */
export function App() {
  const session = useSession();
  if (session.loading) {
    return (
      <main className="centered">
        <Loading />
      </main>
    );
  }
  if (!session.user) {
    return session.setupRequired ? <SetupPage /> : <LoginPage />;
  }
  return (
    <Layout>
      <Routes>
        <Route path="/" element={<DashboardPage />} />
        <Route path="/jobs" element={<JobsPage />} />
        <Route path="/jobs/:id" element={<JobDetailPage />} />
        <Route path="/applications" element={<ApplicationsPage />} />
        <Route path="/applications/:id" element={<ApplicationDetailPage />} />
        <Route path="/profiles" element={<ProfilesPage />} />
        <Route path="/matches" element={<MatchesPage />} />
        <Route path="/tracking" element={<TrackingPage />} />
        <Route path="/account" element={<AccountPage />} />
        {session.user.role === "ADMIN" && <Route path="/admin" element={<AdminPage />} />}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Layout>
  );
}

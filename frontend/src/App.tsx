import { Route, Routes } from "react-router-dom";
import { Layout } from "./components/Layout";
import { RequireAuth } from "./components/RequireAuth";
import { ShipmentsListPage } from "./pages/ShipmentsListPage";
import { ShipmentDetailPage } from "./pages/ShipmentDetailPage";
import { CreateShipmentPage } from "./pages/CreateShipmentPage";
import { HubsPage } from "./pages/HubsPage";
import { LoginPage } from "./pages/LoginPage";
import { PublicTrackPage } from "./pages/PublicTrackPage";

export function App() {
  return (
    <Routes>
      {/* Public: anyone can look up a shipment, no login - same as any courier's tracking page. */}
      <Route path="/" element={<PublicTrackPage />} />
      <Route path="/track/:trackingId" element={<PublicTrackPage />} />
      <Route path="/login" element={<LoginPage />} />

      {/* Everything else is the internal ops dashboard, behind a login. */}
      <Route path="/app" element={<RequireAuth />}>
        <Route element={<Layout />}>
          <Route index element={<ShipmentsListPage />} />
          <Route path="shipments/new" element={<CreateShipmentPage />} />
          <Route path="shipments/:id" element={<ShipmentDetailPage />} />
          <Route path="hubs" element={<HubsPage />} />
        </Route>
      </Route>
    </Routes>
  );
}

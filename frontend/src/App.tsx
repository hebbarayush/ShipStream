import { Route, Routes } from "react-router-dom";
import { Layout } from "./components/Layout";
import { ShipmentsListPage } from "./pages/ShipmentsListPage";
import { ShipmentDetailPage } from "./pages/ShipmentDetailPage";
import { CreateShipmentPage } from "./pages/CreateShipmentPage";
import { HubsPage } from "./pages/HubsPage";

export function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<ShipmentsListPage />} />
        <Route path="shipments/new" element={<CreateShipmentPage />} />
        <Route path="shipments/:id" element={<ShipmentDetailPage />} />
        <Route path="hubs" element={<HubsPage />} />
      </Route>
    </Routes>
  );
}

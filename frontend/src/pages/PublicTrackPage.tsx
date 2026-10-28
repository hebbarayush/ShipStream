import { useQuery } from "@tanstack/react-query";
import { useState, type FormEvent } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";

// Seeded by the backend on startup (DemoDataSeeder) - always IN_TRANSIT, so there's a live ETA and a
// moving position to look at without needing to create a shipment first.
const DEMO_TRACKING_ID = "FT-DEMO00001";
import { api, ApiError } from "../api/client";
import { StatusBadge } from "../components/StatusBadge";
import { Loading } from "../components/RequestState";
import { useShipmentSocket } from "../hooks/useShipmentSocket";

/**
 * The only page a stranger can reach without an OPS login - same idea as entering a tracking number
 * on any courier's public site. No internal routes, no event history, no admin data: just enough to
 * answer "where is my package".
 */
export function PublicTrackPage() {
  const { trackingId } = useParams<{ trackingId: string }>();
  const navigate = useNavigate();
  const [query, setQuery] = useState("");

  function handleSearch(e: FormEvent) {
    e.preventDefault();
    if (query.trim()) navigate(`/track/${query.trim()}`);
  }

  return (
    <div className="auth-page">
      <div className="card auth-card" style={{ maxWidth: 480 }}>
        <div className="brand" style={{ marginBottom: "var(--sp-6)" }}>
          Ship<span className="brand-mark">Stream</span>
        </div>

        <form onSubmit={handleSearch} style={{ display: "flex", gap: "var(--sp-2)", marginBottom: "var(--sp-6)" }}>
          <input
            placeholder="Tracking ID, e.g. FT-7K2M9QX4TB"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            className="mono"
            style={{ flex: 1, padding: "var(--sp-2) var(--sp-3)", border: "var(--border)", borderRadius: "var(--radius)" }}
            autoFocus
          />
          <button type="submit" className="btn btn-primary">
            Track
          </button>
        </form>

        {!trackingId && (
          <div className="hint-box" style={{ marginBottom: "var(--sp-6)" }}>
            No tracking ID handy? Try the demo shipment:{" "}
            <button type="button" className="link" onClick={() => navigate(`/track/${DEMO_TRACKING_ID}`)}>
              {DEMO_TRACKING_ID}
            </button>
          </div>
        )}

        {trackingId && <TrackingResult trackingId={trackingId} />}

        <p className="subtitle" style={{ marginTop: "var(--sp-6)" }}>
          Logistics staff: <Link to="/login">ops login</Link>.
        </p>
      </div>
    </div>
  );
}

function TrackingResult({ trackingId }: { trackingId: string }) {
  const shipmentQuery = useQuery({
    queryKey: ["public-shipment", trackingId],
    queryFn: () => api.getShipmentByTrackingId(trackingId),
    retry: false,
  });
  const trackingQuery = useQuery({
    queryKey: ["public-tracking", shipmentQuery.data?.id],
    queryFn: () => api.getTracking(shipmentQuery.data!.id),
    enabled: !!shipmentQuery.data,
    retry: false,
    refetchInterval: 8000,
  });
  useShipmentSocket(shipmentQuery.data?.trackingId); // live push just speeds up the next poll tick

  if (shipmentQuery.isLoading) return <Loading />;
  if (shipmentQuery.isError) {
    const notFound = shipmentQuery.error instanceof ApiError && shipmentQuery.error.status === 404;
    return (
      <p className="muted">{notFound ? "No shipment with that tracking ID." : "Couldn't look that up right now."}</p>
    );
  }
  const shipment = shipmentQuery.data!;

  return (
    <div>
      <div className="page-header" style={{ marginBottom: "var(--sp-4)" }}>
        <h2 className="mono">{shipment.trackingId}</h2>
        <StatusBadge status={shipment.status} />
      </div>
      <dl className="kv">
        <dt>From</dt>
        <dd>{shipment.senderAddress}</dd>
        <dt>To</dt>
        <dd>{shipment.receiverAddress}</dd>
        <dt>Estimated delivery</dt>
        <dd>{shipment.estimatedDeliveryAt ? new Date(shipment.estimatedDeliveryAt).toLocaleString() : "—"}</dd>
        <dt>Live position</dt>
        <dd className="mono">
          {trackingQuery.data
            ? `${trackingQuery.data.latitude.toFixed(4)}, ${trackingQuery.data.longitude.toFixed(4)}`
            : "—"}
        </dd>
      </dl>
    </div>
  );
}

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { api } from "../api/client";
import type { ShipmentStatus } from "../api/types";
import { StatusBadge } from "../components/StatusBadge";
import { ErrorBanner, Loading } from "../components/RequestState";
import { useShipmentSocket } from "../hooks/useShipmentSocket";

// Mirrors ShipmentStatus#canTransitionTo on the backend, purely to decide which buttons to show.
// The backend is still the source of truth - an out-of-date map here just means a stale button, never
// an invalid write, since every transition is re-validated server-side regardless.
const NEXT_STEPS: Record<ShipmentStatus, ShipmentStatus[]> = {
  CREATED: ["PICKED_UP", "CANCELLED"],
  PICKED_UP: ["IN_TRANSIT", "CANCELLED"],
  IN_TRANSIT: ["ARRIVED_AT_HUB", "DELAYED", "LOST"],
  ARRIVED_AT_HUB: ["IN_TRANSIT", "OUT_FOR_DELIVERY", "DELAYED", "LOST"],
  OUT_FOR_DELIVERY: ["DELIVERED", "DELAYED", "LOST"],
  DELAYED: ["IN_TRANSIT", "ARRIVED_AT_HUB", "OUT_FOR_DELIVERY", "LOST"],
  DELIVERED: [],
  LOST: [],
  CANCELLED: [],
};

export function ShipmentDetailPage() {
  const { id } = useParams<{ id: string }>();
  const shipmentId = Number(id);
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  const shipmentQuery = useQuery({
    queryKey: ["shipment", shipmentId],
    queryFn: () => api.getShipment(shipmentId),
  });
  const eventsQuery = useQuery({
    queryKey: ["shipment-events", shipmentId],
    queryFn: () => api.getEvents(shipmentId),
  });
  const trackingQuery = useQuery({
    queryKey: ["tracking", shipmentId],
    queryFn: () => api.getTracking(shipmentId),
    retry: false,
    refetchInterval: 8000,
  });

  const { connected, lastEvent } = useShipmentSocket(shipmentQuery.data?.trackingId);

  // A WS push is faster than the next poll tick - refetch immediately when one arrives.
  useEffect(() => {
    if (!lastEvent) return;
    queryClient.invalidateQueries({ queryKey: ["shipment", shipmentId] });
    queryClient.invalidateQueries({ queryKey: ["shipment-events", shipmentId] });
    queryClient.invalidateQueries({ queryKey: ["tracking", shipmentId] });
  }, [lastEvent, queryClient, shipmentId]);

  const transition = useMutation({
    mutationFn: (status: ShipmentStatus) => api.updateStatus(shipmentId, status),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["shipment", shipmentId] });
      queryClient.invalidateQueries({ queryKey: ["shipment-events", shipmentId] });
      queryClient.invalidateQueries({ queryKey: ["shipments"] });
    },
  });

  const deleteShipment = useMutation({
    mutationFn: () => api.deleteShipment(shipmentId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["shipments"] });
      navigate("/app");
    },
  });

  if (shipmentQuery.isLoading) return <Loading />;
  if (shipmentQuery.isError) return <ErrorBanner error={shipmentQuery.error} />;
  const shipment = shipmentQuery.data!;

  return (
    <>
      <div className="page-header">
        <div>
          <h1 className="mono">{shipment.trackingId}</h1>
          <p className="subtitle">
            <span className={"live-dot" + (connected ? "" : " offline")} /> {connected ? "Live" : "Not connected"}
          </p>
        </div>
        <StatusBadge status={shipment.status} />
      </div>

      {transition.isError && <ErrorBanner error={transition.error} />}

      <div className="form-row" style={{ alignItems: "start" }}>
        <section>
          <div className="card" style={{ marginBottom: "var(--sp-6)" }}>
            <h2 style={{ marginBottom: "var(--sp-4)" }}>Details</h2>
            <dl className="kv">
              <dt>Sender</dt>
              <dd>{shipment.senderAddress}</dd>
              <dt>Receiver</dt>
              <dd>{shipment.receiverAddress}</dd>
              <dt>Current hub</dt>
              <dd>{shipment.currentHubId ?? "—"}</dd>
              <dt>Destination hub</dt>
              <dd>{shipment.destinationHubId ?? "—"}</dd>
              <dt>ETA</dt>
              <dd>{shipment.estimatedDeliveryAt ? new Date(shipment.estimatedDeliveryAt).toLocaleString() : "—"}</dd>
              <dt>Live position</dt>
              <dd className="mono">
                {trackingQuery.data
                  ? `${trackingQuery.data.latitude.toFixed(5)}, ${trackingQuery.data.longitude.toFixed(5)}`
                  : "—"}
              </dd>
              <dt>Created</dt>
              <dd>{new Date(shipment.createdAt).toLocaleString()}</dd>
            </dl>
          </div>

          {NEXT_STEPS[shipment.status].length > 0 && (
            <div className="card" style={{ marginBottom: "var(--sp-6)" }}>
              <h2 style={{ marginBottom: "var(--sp-4)" }}>Advance status</h2>
              <div style={{ display: "flex", gap: "var(--sp-3)", flexWrap: "wrap" }}>
                {NEXT_STEPS[shipment.status].map((next) => (
                  <button
                    key={next}
                    className={"btn " + (next === "LOST" || next === "CANCELLED" ? "btn-danger" : "btn-primary")}
                    disabled={transition.isPending}
                    onClick={() => transition.mutate(next)}
                  >
                    {next.replaceAll("_", " ").toLowerCase()}
                  </button>
                ))}
              </div>
            </div>
          )}

          <div className="card">
            <h2 style={{ marginBottom: "var(--sp-2)" }}>Admin cleanup</h2>
            <p className="subtitle" style={{ marginBottom: "var(--sp-4)" }}>
              Deletes the record outright - a real shipment should be <strong>cancelled</strong>, not
              deleted. This is for clearing out test data.
            </p>
            {deleteShipment.isError && <ErrorBanner error={deleteShipment.error} />}
            <button
              className="btn btn-danger"
              disabled={deleteShipment.isPending}
              onClick={() => {
                if (confirm(`Permanently delete shipment ${shipment.trackingId}? This can't be undone.`)) {
                  deleteShipment.mutate();
                }
              }}
            >
              {deleteShipment.isPending ? "Deleting…" : "Delete shipment"}
            </button>
          </div>
        </section>

        <section>
          <h2 style={{ marginBottom: "var(--sp-3)" }}>History</h2>
          {eventsQuery.isLoading && <Loading />}
          {eventsQuery.isError && <ErrorBanner error={eventsQuery.error} />}
          {eventsQuery.data && (
            <div className="card event-list">
              {eventsQuery.data.map((e, i) => (
                <div className="event-row" key={i}>
                  <time>{new Date(e.createdAt).toLocaleString()}</time>
                  <div>
                    <strong>{e.eventType.replaceAll("_", " ").toLowerCase()}</strong>
                    {e.eventType === "STATUS_CHANGED" && (
                      <div className="muted">
                        {String(e.payload.from)} → {String(e.payload.to)}
                      </div>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </section>
      </div>
    </>
  );
}

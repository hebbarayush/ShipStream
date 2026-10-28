import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../api/client";
import type { ShipmentStatus } from "../api/types";
import { StatusBadge } from "../components/StatusBadge";
import { EmptyState, ErrorBanner, Loading } from "../components/RequestState";

const STATUSES: ShipmentStatus[] = [
  "CREATED",
  "PICKED_UP",
  "IN_TRANSIT",
  "ARRIVED_AT_HUB",
  "OUT_FOR_DELIVERY",
  "DELIVERED",
  "DELAYED",
  "LOST",
  "CANCELLED",
];

const PAGE_SIZE = 20;

export function ShipmentsListPage() {
  const [status, setStatus] = useState<ShipmentStatus | "">("");
  const [page, setPage] = useState(0);

  const query = useQuery({
    queryKey: ["shipments", status, page],
    queryFn: () => api.listShipments({ status: status || undefined, page, size: PAGE_SIZE }),
    refetchInterval: 10_000,
  });

  return (
    <>
      <div className="page-header">
        <div>
          <h1>Shipments</h1>
          <p className="subtitle">Everything moving through the network right now.</p>
        </div>
        <Link to="/app/shipments/new" className="btn btn-primary">
          New shipment
        </Link>
      </div>

      <div className="toolbar">
        <div className="field" style={{ marginBottom: 0, minWidth: 200 }}>
          <label htmlFor="status-filter">Status</label>
          <select
            id="status-filter"
            value={status}
            onChange={(e) => {
              setStatus(e.target.value as ShipmentStatus | "");
              setPage(0);
            }}
          >
            <option value="">All statuses</option>
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </div>
      </div>

      {query.isError && <ErrorBanner error={query.error} />}
      {query.isLoading && <Loading />}

      {query.data && query.data.items.length === 0 && (
        <EmptyState>No shipments match this filter yet.</EmptyState>
      )}

      {query.data && query.data.items.length > 0 && (
        <div className="table-card">
          <table>
            <thead>
              <tr>
                <th>Tracking ID</th>
                <th>Status</th>
                <th>Sender</th>
                <th>Receiver</th>
                <th>Updated</th>
              </tr>
            </thead>
            <tbody>
              {query.data.items.map((s) => (
                <tr key={s.id}>
                  <td>
                    <Link to={`/app/shipments/${s.id}`} className="mono">
                      {s.trackingId}
                    </Link>
                  </td>
                  <td>
                    <StatusBadge status={s.status} />
                  </td>
                  <td>{s.senderAddress}</td>
                  <td>{s.receiverAddress}</td>
                  <td className="muted">{new Date(s.updatedAt).toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {query.data && query.data.totalPages > 1 && (
        <div className="pagination">
          <button
            className="btn btn-sm"
            disabled={page === 0}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
          >
            Previous
          </button>
          <span>
            Page {page + 1} of {query.data.totalPages}
          </span>
          <button
            className="btn btn-sm"
            disabled={page + 1 >= query.data.totalPages}
            onClick={() => setPage((p) => p + 1)}
          >
            Next
          </button>
        </div>
      )}
    </>
  );
}

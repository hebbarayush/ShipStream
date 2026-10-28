import type {
  ApiProblem,
  Hub,
  Page,
  Route,
  Shipment,
  ShipmentEvent,
  ShipmentStatus,
  Tracking,
} from "./types";

const BASE_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080";

export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`, {
    ...init,
    headers: { "Content-Type": "application/json", ...init?.headers },
  });
  if (!res.ok) {
    let detail = res.statusText;
    try {
      const problem = (await res.json()) as ApiProblem;
      detail = problem.detail ?? problem.title ?? detail;
    } catch {
      // body wasn't JSON (or was empty) - fall back to the status text
    }
    throw new ApiError(res.status, detail);
  }
  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}

export const api = {
  listShipments: (params: { status?: ShipmentStatus; page?: number; size?: number }) => {
    const qs = new URLSearchParams();
    if (params.status) qs.set("status", params.status);
    qs.set("page", String(params.page ?? 0));
    qs.set("size", String(params.size ?? 20));
    return request<Page<Shipment>>(`/api/v1/shipments?${qs}`);
  },
  getShipment: (id: number) => request<Shipment>(`/api/v1/shipments/${id}`),
  getShipmentByTrackingId: (trackingId: string) =>
    request<Shipment>(`/api/v1/shipments/track/${trackingId}`),
  createShipment: (body: {
    senderAddress: string;
    receiverAddress: string;
    originHubId?: number;
    destinationHubId?: number;
  }) => request<Shipment>("/api/v1/shipments", { method: "POST", body: JSON.stringify(body) }),
  updateStatus: (id: number, status: ShipmentStatus, hubId?: number) =>
    request<Shipment>(`/api/v1/shipments/${id}/status`, {
      method: "POST",
      body: JSON.stringify({ status, hubId }),
    }),
  getEvents: (id: number) => request<ShipmentEvent[]>(`/api/v1/shipments/${id}/events`),

  listHubs: () => request<Hub[]>("/api/v1/hubs"),
  createHub: (body: { name: string; location: string; capacity: number; latitude: number; longitude: number }) =>
    request<Hub>("/api/v1/hubs", { method: "POST", body: JSON.stringify(body) }),
  listRoutes: () => request<Route[]>("/api/v1/routes"),
  createRoute: (body: { sourceHubId: number; destinationHubId: number; distanceKm: number }) =>
    request<Route>("/api/v1/routes", { method: "POST", body: JSON.stringify(body) }),

  getTracking: (shipmentId: number) => request<Tracking>(`/api/v1/tracking/${shipmentId}`),
};

export const WS_URL = `${BASE_URL}/ws`;

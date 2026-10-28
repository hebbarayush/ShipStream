export type ShipmentStatus =
  | "CREATED"
  | "PICKED_UP"
  | "IN_TRANSIT"
  | "ARRIVED_AT_HUB"
  | "OUT_FOR_DELIVERY"
  | "DELIVERED"
  | "DELAYED"
  | "LOST"
  | "CANCELLED";

export interface Shipment {
  id: number;
  trackingId: string;
  senderAddress: string;
  receiverAddress: string;
  status: ShipmentStatus;
  currentHubId: number | null;
  destinationHubId: number | null;
  estimatedDeliveryAt: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface Hub {
  id: number;
  name: string;
  location: string;
  capacity: number;
  latitude: number;
  longitude: number;
}

export interface Route {
  id: number;
  sourceHubId: number;
  destinationHubId: number;
  distanceKm: number;
}

export interface Tracking {
  shipmentId: number;
  latitude: number;
  longitude: number;
  lastUpdated: string;
}

export interface ShipmentEvent {
  eventType: string;
  payload: Record<string, unknown>;
  createdAt: string;
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ApiProblem {
  title?: string;
  detail?: string;
  status?: number;
}

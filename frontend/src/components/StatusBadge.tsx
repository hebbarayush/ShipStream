import type { ShipmentStatus } from "../api/types";

const COLOR_VAR: Record<ShipmentStatus, string> = {
  CREATED: "var(--status-created)",
  PICKED_UP: "var(--status-picked-up)",
  IN_TRANSIT: "var(--status-in-transit)",
  ARRIVED_AT_HUB: "var(--status-arrived)",
  OUT_FOR_DELIVERY: "var(--status-out-for-delivery)",
  DELIVERED: "var(--status-delivered)",
  DELAYED: "var(--status-delayed)",
  LOST: "var(--status-lost)",
  CANCELLED: "var(--status-cancelled)",
};

const LABEL: Record<ShipmentStatus, string> = {
  CREATED: "Created",
  PICKED_UP: "Picked up",
  IN_TRANSIT: "In transit",
  ARRIVED_AT_HUB: "Arrived at hub",
  OUT_FOR_DELIVERY: "Out for delivery",
  DELIVERED: "Delivered",
  DELAYED: "Delayed",
  LOST: "Lost",
  CANCELLED: "Cancelled",
};

export function StatusBadge({ status }: { status: ShipmentStatus }) {
  return (
    <span className="badge" style={{ color: COLOR_VAR[status] }}>
      {LABEL[status]}
    </span>
  );
}

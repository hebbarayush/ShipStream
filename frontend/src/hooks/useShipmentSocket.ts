import { Client, type IMessage } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { useEffect, useRef, useState } from "react";
import { WS_URL } from "../api/client";

export interface ShipmentWsEvent {
  eventType: string;
  shipmentId: number;
  trackingId: string;
  data: Record<string, unknown>;
}

/**
 * Subscribes to /topic/shipments/{trackingId} for as long as the component is mounted. Returns the
 * latest event and a connection flag, so the UI can show a "live" indicator honestly.
 */
export function useShipmentSocket(trackingId: string | undefined) {
  const [connected, setConnected] = useState(false);
  const [lastEvent, setLastEvent] = useState<ShipmentWsEvent | null>(null);
  const clientRef = useRef<Client | null>(null);

  useEffect(() => {
    if (!trackingId) return;

    const client = new Client({
      webSocketFactory: () => new SockJS(WS_URL) as WebSocket,
      reconnectDelay: 3000,
      onConnect: () => {
        setConnected(true);
        client.subscribe(`/topic/shipments/${trackingId}`, (message: IMessage) => {
          try {
            setLastEvent(JSON.parse(message.body) as ShipmentWsEvent);
          } catch {
            // ignore malformed frames
          }
        });
      },
      onWebSocketClose: () => setConnected(false),
      onStompError: () => setConnected(false),
    });

    clientRef.current = client;
    client.activate();

    return () => {
      setConnected(false);
      client.deactivate();
    };
  }, [trackingId]);

  return { connected, lastEvent };
}

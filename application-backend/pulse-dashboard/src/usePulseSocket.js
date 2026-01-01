import { useEffect, useState, useCallback, useRef } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const WS_URL = 'http://localhost:8080/pulse-websocket';

/**
 * Custom hook for managing WebSocket connection to Pulse backend
 * Uses STOMP over SockJS for real-time match updates
 */
export function usePulseSocket() {
    const [isConnected, setIsConnected] = useState(false);
    const [matches, setMatches] = useState([]);
    const [stats, setStats] = useState({
        totalMatches: 0,
        matchesPerSecond: 0,
        peakRate: 0,
    });

    const clientRef = useRef(null);
    const matchCountRef = useRef(0);
    const lastSecondMatchesRef = useRef(0);

    // Calculate matches per second
    useEffect(() => {
        const interval = setInterval(() => {
            const currentCount = matchCountRef.current;
            const rate = currentCount - lastSecondMatchesRef.current;
            lastSecondMatchesRef.current = currentCount;

            setStats(prev => ({
                ...prev,
                matchesPerSecond: rate,
                peakRate: Math.max(prev.peakRate, rate),
            }));
        }, 1000);

        return () => clearInterval(interval);
    }, []);

    const connect = useCallback(() => {
        if (clientRef.current?.connected) return;

        const client = new Client({
            webSocketFactory: () => new SockJS(WS_URL),
            reconnectDelay: 5000,
            heartbeatIncoming: 4000,
            heartbeatOutgoing: 4000,
            debug: (str) => {
                if (import.meta.env.DEV) {
                    console.log('[STOMP]', str);
                }
            },
            onConnect: () => {
                console.log('✅ Connected to Pulse WebSocket');
                setIsConnected(true);

                // Subscribe to match events
                client.subscribe('/topic/matches', (message) => {
                    try {
                        const match = JSON.parse(message.body);
                        matchCountRef.current += 1;

                        setMatches(prev => {
                            const newMatches = [
                                {
                                    id: match.matchId,
                                    userA: match.userA,
                                    userB: match.userB,
                                    timestamp: match.timestamp,
                                    receivedAt: Date.now(),
                                },
                                ...prev,
                            ].slice(0, 50); // Keep last 50 matches
                            return newMatches;
                        });

                        setStats(prev => ({
                            ...prev,
                            totalMatches: matchCountRef.current,
                        }));
                    } catch (err) {
                        console.error('Failed to parse match:', err);
                    }
                });
            },
            onDisconnect: () => {
                console.log('❌ Disconnected from Pulse WebSocket');
                setIsConnected(false);
            },
            onStompError: (frame) => {
                console.error('STOMP error:', frame.headers['message']);
                setIsConnected(false);
            },
        });

        clientRef.current = client;
        client.activate();
    }, []);

    const disconnect = useCallback(() => {
        if (clientRef.current) {
            clientRef.current.deactivate();
            clientRef.current = null;
            setIsConnected(false);
        }
    }, []);

    // Auto-connect on mount
    useEffect(() => {
        connect();
        return () => disconnect();
    }, [connect, disconnect]);

    return {
        isConnected,
        matches,
        stats,
        connect,
        disconnect,
    };
}

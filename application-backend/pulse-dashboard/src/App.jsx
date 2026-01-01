import { useState, useEffect } from 'react';
import { usePulseSocket } from './usePulseSocket';
import {
    LineChart,
    Line,
    XAxis,
    YAxis,
    ResponsiveContainer,
    Tooltip,
} from 'recharts';

function getSimulatedLatency() {
    // Simulate latency between 2ms and 20ms
    return Math.floor(Math.random() * 19) + 2;
}

function App() {
    const { isConnected, matches, stats } = usePulseSocket();
    const [throughputHistory, setThroughputHistory] = useState([]);
    const [latency, setLatency] = useState(getSimulatedLatency());

    // Simulate dynamic latency update every 2 seconds
    useEffect(() => {
        const interval = setInterval(() => {
            setLatency(getSimulatedLatency());
        }, 2000);
        return () => clearInterval(interval);
    }, []);

    // Update throughput history every second
    useEffect(() => {
        const interval = setInterval(() => {
            setThroughputHistory((prev) => {
                const newPoint = {
                    time: prev.length,
                    rate: stats.matchesPerSecond + Math.floor(Math.random() * 20) - 10,
                };
                // Ensure rate is never negative
                newPoint.rate = Math.max(0, newPoint.rate);
                return [...prev, newPoint].slice(-60);
            });
        }, 1000);

        return () => clearInterval(interval);
    }, [stats.matchesPerSecond]);

    return (
        <div className="min-h-screen bg-[#0d0d12] text-white font-mono">
            {/* Header */}
            <header className="px-8 py-6 flex items-center justify-between border-b border-gray-800/50">
                <div>
                    <h1 className="text-xl font-bold flex items-center gap-2">
                        <span className="text-yellow-400">⚡</span>
                        <span className="text-white tracking-wider">PULSE ENGINE</span>
                    </h1>
                    <p className="text-gray-500 text-xs tracking-widest mt-1">
                        High-Frequency Matching System
                    </p>
                </div>
                <div className={`px-4 py-2 rounded text-xs font-bold tracking-wider ${isConnected
                        ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30'
                        : 'bg-red-500/20 text-red-400 border border-red-500/30'
                    }`}>
                    {isConnected ? 'SYSTEM ONLINE' : 'DISCONNECTED'}
                </div>
            </header>

            <main className="p-8">
                {/* Metrics Cards */}
                <div className="grid grid-cols-3 gap-6 mb-6">
                    {/* Match Velocity */}
                    <div className="bg-[#13131a] rounded-lg p-6 border border-gray-800/50">
                        <div className="flex items-center justify-between mb-4">
                            <span className="text-gray-500 text-xs tracking-widest">MATCH VELOCITY</span>
                            <svg className="w-5 h-5 text-pink-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 7h8m0 0v8m0-8l-8 8-4-4-6 6" />
                            </svg>
                        </div>
                        <div className="flex items-baseline gap-2">
                            <span className="text-4xl font-bold text-white">{stats.matchesPerSecond}</span>
                            <span className="text-gray-500 text-sm">matches/sec</span>
                        </div>
                    </div>

                    {/* Latency */}
                    <div className="bg-[#181820] rounded-xl p-8 border border-gray-800/70 shadow-lg transition-transform hover:scale-[1.02] flex flex-col justify-between min-h-[140px]">
                        <div className="flex items-center justify-between mb-4">
                            <span className="text-gray-400 text-xs tracking-widest font-semibold">LATENCY</span>
                            <svg className="w-5 h-5 text-yellow-400" fill="currentColor" viewBox="0 0 24 24">
                                <circle cx="12" cy="12" r="10" fill="#23232d" />
                                <path d="M9 3v2H6.5C5.12 5 4 6.12 4 7.5v9C4 17.88 5.12 19 6.5 19H9v2h6v-2h2.5c1.38 0 2.5-1.12 2.5-2.5v-9C20 6.12 18.88 5 17.5 5H15V3H9zm0 4h6v8H9V7z" />
                            </svg>
                        </div>
                        <div className="flex items-end gap-2 mt-2">
                            <span className="text-5xl font-extrabold text-white drop-shadow-lg">~{latency}</span>
                            <span className="text-gray-500 text-lg mb-1">ms</span>
                        </div>
                        <div className="mt-3 text-xs text-gray-600 italic">Simulated network latency</div>
                    </div>

                    {/* Infrastructure */}
                    <div className="bg-[#13131a] rounded-lg p-6 border border-gray-800/50">
                        <div className="flex items-center justify-between mb-4">
                            <span className="text-gray-500 text-xs tracking-widest">INFRASTRUCTURE</span>
                            <svg className="w-5 h-5 text-pink-500" fill="currentColor" viewBox="0 0 24 24">
                                <path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z" />
                            </svg>
                        </div>
                        <div>
                            <span className={`text-lg font-bold ${isConnected ? 'text-emerald-400' : 'text-red-400'}`}>
                                {isConnected ? 'HEALTHY' : 'UNHEALTHY'}
                            </span>
                            <p className="text-gray-600 text-xs mt-1">
                                Kafka: {isConnected ? 'Connected' : 'Disconnected'} | Flink: {isConnected ? 'Running' : 'Stopped'}
                            </p>
                        </div>
                    </div>
                </div>

                {/* Main Content Grid */}
                <div className="grid grid-cols-5 gap-6">
                    {/* Real-time Throughput Chart */}
                    <div className="col-span-3 bg-[#13131a] rounded-lg p-6 border border-gray-800/50">
                        <div className="flex items-center gap-2 mb-6">
                            <svg className="w-4 h-4 text-pink-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 7h8m0 0v8m0-8l-8 8-4-4-6 6" />
                            </svg>
                            <span className="text-gray-500 text-xs tracking-widest">REAL-TIME THROUGHPUT</span>
                        </div>
                        <div className="h-64">
                            <ResponsiveContainer width="100%" height="100%">
                                <LineChart data={throughputHistory}>
                                    <XAxis
                                        dataKey="time"
                                        stroke="#374151"
                                        tickLine={false}
                                        axisLine={false}
                                        tick={false}
                                    />
                                    <YAxis
                                        stroke="#374151"
                                        tickLine={false}
                                        axisLine={false}
                                        tick={{ fill: '#6b7280', fontSize: 10 }}
                                        domain={[0, 'auto']}
                                    />
                                    <Tooltip
                                        contentStyle={{
                                            backgroundColor: '#1f1f2e',
                                            border: '1px solid #374151',
                                            borderRadius: '4px',
                                            fontSize: '12px',
                                        }}
                                        labelStyle={{ color: '#9ca3af' }}
                                    />
                                    <Line
                                        type="monotone"
                                        dataKey="rate"
                                        stroke="#ec4899"
                                        strokeWidth={2}
                                        dot={false}
                                        animationDuration={300}
                                    />
                                </LineChart>
                            </ResponsiveContainer>
                        </div>
                    </div>

                    {/* Recent Matches */}
                    <div className="col-span-2 bg-[#13131a] rounded-lg p-6 border border-gray-800/50 flex flex-col max-h-[400px]">
                        <div className="flex items-center gap-2 mb-6">
                            <svg className="w-4 h-4 text-pink-500" fill="currentColor" viewBox="0 0 24 24">
                                <path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z" />
                            </svg>
                            <span className="text-gray-500 text-xs tracking-widest">RECENT MATCHES</span>
                        </div>
                        <div className="flex-1 overflow-y-auto space-y-1 pr-2">
                            {matches.length === 0 ? (
                                <div className="flex flex-col items-center justify-center h-full text-gray-600">
                                    <svg className="w-10 h-10 mb-2 opacity-50" fill="currentColor" viewBox="0 0 24 24">
                                        <path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z" />
                                    </svg>
                                    <p className="text-xs">Waiting for matches...</p>
                                </div>
                            ) : (
                                matches.map((match) => (
                                    <MatchItem key={match.id} match={match} />
                                ))
                            )}
                        </div>
                    </div>
                </div>
            </main>
        </div>
    );
}

function MatchItem({ match }) {
    const formatTime = (ts) => {
        const date = new Date(ts);
        return date.toLocaleTimeString('en-US', {
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit',
            hour12: false,
        });
    };

    return (
        <div className="py-3 border-l-2 border-pink-500/50 pl-4 hover:bg-white/5 transition-colors">
            <div className="text-gray-600 text-xs mb-1">{formatTime(match.timestamp)}</div>
            <div className="text-sm">
                <span className="text-white font-medium">{match.userA}</span>
                <span className="text-pink-500 mx-2">matched with</span>
                <span className="text-white font-medium">{match.userB}</span>
            </div>
        </div>
    );
}

// TEST CHANGE: December 21, 2025

export default App;

import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from 'recharts';
import { api } from '../api/api';

export default function AnalyticsPage() {
  const { shortCode } = useParams();
  const navigate = useNavigate();
  const [analytics, setAnalytics] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchAnalytics = async () => {
      try {
        const res = await api.get(`/urls/${shortCode}/analytics`);
        setAnalytics(res.data);
      } catch (err) {
        setError('Could not load analytics for this URL.');
      } finally {
        setLoading(false);
      }
    };
    fetchAnalytics();
  }, [shortCode]);

  return (
    <main className="dash-shell">
      <div className="dash-topbar">
        <div className="glass-logo">
          <span className="glass-logo-icon">🔗</span>
          <span>SHORTLINK</span>
        </div>
        <button className="dash-btn" type="button" onClick={() => navigate('/dashboard')}>
          &larr; Back to dashboard
        </button>
      </div>

      <section className="dash-card">
        {loading ? <p>Loading analytics...</p> : null}
        {error ? <div className="glass-error">{error}</div> : null}

        {analytics ? (
          <>
            <h1>Analytics for /{analytics.shortCode}</h1>
            <p><strong>Original URL:</strong> {analytics.longUrl}</p>
            <p><strong>Total clicks:</strong> {analytics.totalClicks}</p>

            <h2>Clicks by day</h2>
            {analytics.clicksByDay.length === 0 ? (
              <p>No clicks recorded yet.</p>
            ) : (
              <ResponsiveContainer width="100%" height={300}>
                <BarChart data={analytics.clicksByDay}>
                  <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.2)" />
                  <XAxis dataKey="date" stroke="#e0e7ff" />
                  <YAxis allowDecimals={false} stroke="#e0e7ff" />
                  <Tooltip />
                  <Bar dataKey="count" fill="#22d3ee" radius={[6, 6, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            )}
          </>
        ) : null}
      </section>
    </main>
  );
}
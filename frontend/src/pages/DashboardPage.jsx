import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/api';
import { useAuth } from '../context/AuthContext';

const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080';

function getShortUrl(item) {
  return item.shortUrl || item.shortLink || `${API_BASE}/${item.shortCode}`;
}

export default function DashboardPage() {
  const { user, logout } = useAuth();
  const [urls, setUrls] = useState([]);
  const [longUrl, setLongUrl] = useState('');
  const [alias, setAlias] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(true);
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState('');

  const loadUrls = async () => {
    try {
      const res = await api.get('/urls');
      setUrls(res.data);
    } catch (err) {
      setError('Could not load your URLs.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadUrls();
  }, []);

  const handleCreate = async (event) => {
    event.preventDefault();
    setCreating(true);
    setError('');

    try {
      await api.post('/urls', { longUrl, customAlias: alias || undefined, password: password || undefined });
      setLongUrl('');
      setAlias('');
      setPassword('');
      await loadUrls();
    } catch (requestError) {
      setError(requestError?.response?.data?.detail || requestError?.response?.data?.message || 'Could not shorten URL');
    } finally {
      setCreating(false);
    }
  };

  const handleDelete = async (shortCode) => {
    try {
      await api.delete(`/urls/${shortCode}`);
      setUrls((current) => current.filter((item) => item.shortCode !== shortCode));
    } catch (err) {
      setError('Could not delete this URL.');
    }
  };

  const handleDownloadQr = async (shortCode) => {
    try {
      const res = await api.get(`/urls/${shortCode}/qrcode`, { responseType: 'blob' });
      const url = window.URL.createObjectURL(new Blob([res.data]));
      const link = document.createElement('a');
      link.href = url;
      link.download = `${shortCode}-qr.png`;
      link.click();
      window.URL.revokeObjectURL(url);
    } catch (err) {
      setError('Could not download QR code.');
    }
  };

  return (
    <main className="dash-shell">
      <div className="dash-topbar">
        <div className="glass-logo">
          <span className="glass-logo-icon">🔗</span>
          <span>SHORTLINK</span>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <span>Signed in as <strong>{user?.email}</strong></span>
          <button className="dash-btn" type="button" onClick={logout}>
            Log out
          </button>
        </div>
      </div>

      <section className="dash-card">
        <h1>Create a short URL</h1>

        <form className="dash-form-row" onSubmit={handleCreate}>
          <label>
            Long URL
            <input
              type="url"
              placeholder="https://example.com/very/long/link"
              value={longUrl}
              onChange={(event) => setLongUrl(event.target.value)}
              required
            />
          </label>

          <label>
            Custom alias (optional)
            <input
              type="text"
              placeholder="my-link"
              value={alias}
              onChange={(event) => setAlias(event.target.value)}
            />
          </label>

         <label>
            Password (optional)
            <input
              type="password"
              placeholder="Leave blank for no protection"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
          </label>

          <button type="submit" disabled={creating}>
            {creating ? 'Shortening...' : 'Shorten'}
          </button>
        </form>

        {error ? <div className="glass-error" style={{ marginTop: 16 }}>{error}</div> : null}
      </section>

      <section className="dash-card">
        <h2>Your URLs</h2>

        {loading ? (
          <p>Loading...</p>
        ) : urls.length === 0 ? (
          <p>No URLs yet. Create one above.</p>
        ) : (
          <div className="dash-table-wrap">
            <table className="dash-table">
              <thead>
                <tr>
                  <th>Short URL</th>
                  <th>Original URL</th>
                  <th>Clicks</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {urls.map((item) => (
                  <tr key={item.shortCode}>
                    <td>
                      <a href={getShortUrl(item)} target="_blank" rel="noreferrer">
                        {getShortUrl(item)}
                      </a>
                      {item.passwordProtected ? <span title="Password protected"> 🔒</span> : null}
                    </td>
                    <td>{item.longUrl}</td>
                    <td>{item.clicks}</td>
                    <td>
                      <div className="dash-actions">
                        <Link to={`/analytics/${item.shortCode}`}>
                          <button type="button">Analytics</button>
                        </Link>
                        <button type="button" onClick={() => handleDownloadQr(item.shortCode)}>
                          Download QR
                        </button>
                        <button type="button" onClick={() => handleDelete(item.shortCode)}>
                          Delete
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </main>
  );
}
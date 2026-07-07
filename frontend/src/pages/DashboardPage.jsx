import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/api';
import { useAuth } from '../context/AuthContext';

export default function DashboardPage() {
  const navigate = useNavigate();
  const { user, logout } = useAuth();
  const [urls, setUrls] = useState([]);
  const [longUrl, setLongUrl] = useState('');
  const [customAlias, setCustomAlias] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const fetchUrls = async () => {
    try {
      const response = await api.get('/urls');
      setUrls(response.data);
    } catch (err) {
      setError('Could not load your URLs.');
    }
  };

  useEffect(() => {
    fetchUrls();
  }, []);

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  const handleCreate = async (event) => {
    event.preventDefault();
    setError('');
    setLoading(true);
    try {
      await api.post('/urls', {
        longUrl,
        customAlias: customAlias || null,
      });
      setLongUrl('');
      setCustomAlias('');
      fetchUrls();
    } catch (err) {
      setError(err?.response?.data?.detail || err?.response?.data?.message || 'Could not create short URL.');
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async (shortCode) => {
    try {
      await api.delete(`/urls/${shortCode}`);
      fetchUrls();
    } catch (err) {
      setError('Could not delete that URL.');
    }
  };

  const downloadQrCode = async (shortCode) => {
    try {
      const res = await api.get(`/urls/${shortCode}/qrcode`, { responseType: 'blob' });
      const url = window.URL.createObjectURL(new Blob([res.data]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', `${shortCode}-qr.png`);
      document.body.appendChild(link);
      link.click();
      link.remove();
    } catch (err) {
      setError('QR download failed.');
    }
  };

  return (
    <main className="dashboard-shell">
      <section className="dashboard-card">
        <h1>Dashboard</h1>
        <p>
          Signed in as <strong>{user?.email || 'Authenticated user'}</strong>.
        </p>
        <button type="button" onClick={handleLogout}>
          Log out
        </button>

        <h2>Create a short URL</h2>
        <form onSubmit={handleCreate}>
          <label>
            Long URL
            <input
              type="url"
              value={longUrl}
              onChange={(e) => setLongUrl(e.target.value)}
              required
              placeholder="https://example.com/very/long/path"
            />
          </label>
          <label>
            Custom alias (optional)
            <input
              type="text"
              value={customAlias}
              onChange={(e) => setCustomAlias(e.target.value)}
              placeholder="my-link"
            />
          </label>
          {error ? <div className="form-error">{error}</div> : null}
          <button type="submit" disabled={loading}>
            {loading ? 'Creating...' : 'Shorten'}
          </button>
        </form>

        <h2>Your URLs</h2>
        {urls.length === 0 ? (
          <p>No short URLs yet.</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>Short URL</th>
                <th>Original URL</th>
                <th>Clicks</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {urls.map((u) => (
                <tr key={u.shortCode}>
                  <td>
                    <a href={u.fullShortUrl} target="_blank" rel="noreferrer">
                      {u.fullShortUrl}
                    </a>
                  </td>
                  <td>{u.longUrl}</td>
                  <td>{u.clickCount}</td>
                  <td>
                    <button type="button" onClick={() => navigate(`/analytics/${u.shortCode}`)}>
                      Analytics
                    </button>
                    <button type="button" onClick={() => downloadQrCode(u.shortCode)}>
                      Download QR
                    </button>
                    <button type="button" onClick={() => handleDelete(u.shortCode)}>
                      Delete
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </main>
  );
}
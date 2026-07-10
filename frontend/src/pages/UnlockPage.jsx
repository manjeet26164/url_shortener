import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { api } from '../api/api';

export default function UnlockPage() {
  const { shortCode } = useParams();
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (event) => {
    event.preventDefault();
    setSubmitting(true);
    setError('');

    try {
      const res = await api.post(`/urls/${shortCode}/verify-password`, { password });
      window.location.href = res.data.longUrl;
    } catch (requestError) {
      setError(requestError?.response?.data?.detail || 'Incorrect password');
      setSubmitting(false);
    }
  };

  return (
    <main className="dash-shell">
      <div className="dash-topbar">
        <div className="glass-logo">
          <span className="glass-logo-icon">🔒</span>
          <span>SHORTLINK</span>
        </div>
      </div>

      <section className="dash-card">
        <h1>This link is password protected</h1>
        <p>Enter the password to continue to the destination.</p>

        <form className="dash-form-row" onSubmit={handleSubmit}>
          <label>
            Password
            <input
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              required
              autoFocus
            />
          </label>

          <button type="submit" disabled={submitting}>
            {submitting ? 'Checking...' : 'Continue'}
          </button>
        </form>

        {error ? <div className="glass-error" style={{ marginTop: 16 }}>{error}</div> : null}
      </section>
    </main>
  );
}
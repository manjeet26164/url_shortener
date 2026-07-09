import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../api/api';
import { useAuth } from '../context/AuthContext';

export default function LoginPage() {
  const navigate = useNavigate();
  const { login } = useAuth();
  const [form, setForm] = useState({ email: '', password: '' });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleChange = (event) => {
    const { name, value } = event.target;
    setForm((current) => ({ ...current, [name]: value }));
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    setLoading(true);
    setError('');

    try {
      const response = await api.post('/auth/login', form);
      login({ token: response.data.token, user: { email: form.email } });
      navigate('/dashboard', { replace: true });
    } catch (requestError) {
      setError(requestError?.response?.data?.detail || requestError?.response?.data?.message || 'Login failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <main className="glass-page">
      <div className="glass-blob glass-blob--1" />
      <div className="glass-blob glass-blob--2" />

      <div className="glass-column">
        <div className="glass-logo">
          <span className="glass-logo-icon">🔗</span>
          <span>SHORTLINK</span>
        </div>

        <div className="glass-card">
          <h1>Welcome back</h1>
          <p>Sign in to manage your links</p>

          <form className="glass-form" onSubmit={handleSubmit}>
            <label>
              Email
              <input name="email" type="email" placeholder="name@company.com" value={form.email} onChange={handleChange} required />
            </label>

            <label>
              Password
              <input name="password" type="password" placeholder="••••••••" value={form.password} onChange={handleChange} required />
            </label>

            {error ? <div className="glass-error">{error}</div> : null}

            <button type="submit" disabled={loading}>
              {loading ? 'Signing in...' : 'Sign in'}
            </button>
          </form>

          <p className="glass-footer">
            No account? <Link to="/register">Register</Link>
          </p>
        </div>
      </div>
    </main>
  );
}
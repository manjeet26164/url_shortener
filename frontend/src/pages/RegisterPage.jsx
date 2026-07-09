import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../api/api';

export default function RegisterPage() {
  const navigate = useNavigate();
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
      await api.post('/auth/register', form);
      navigate('/login', { replace: true, state: { message: 'Registration successful. Please sign in.' } });
    } catch (requestError) {
      setError(requestError?.response?.data?.detail || requestError?.response?.data?.message || 'Registration failed');
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
          <h1>Create account</h1>
          <p>Register to start shortening URLs</p>

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
              {loading ? 'Creating account...' : 'Register'}
            </button>
          </form>

          <p className="glass-footer">
            Already have an account? <Link to="/login">Sign in</Link>
          </p>
        </div>
      </div>
    </main>
  );
}
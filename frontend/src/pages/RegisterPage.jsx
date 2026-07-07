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
    <main className="auth-shell">
      <section className="auth-card">
        <h1>Create account</h1>
        <p>Register to start shortening URLs.</p>

        <form className="auth-form" onSubmit={handleSubmit}>
          <label>
            Email
            <input name="email" type="email" value={form.email} onChange={handleChange} required />
          </label>

          <label>
            Password
            <input name="password" type="password" value={form.password} onChange={handleChange} required />
          </label>

          {error ? <div className="form-error">{error}</div> : null}

          <button type="submit" disabled={loading}>
            {loading ? 'Creating account...' : 'Register'}
          </button>
        </form>

        <p className="auth-footer">
          Already have an account? <Link to="/login">Sign in</Link>
        </p>
      </section>
    </main>
  );
}
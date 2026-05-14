import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import AuthCard from '../components/auth/AuthCard';

const inputClassName =
  'w-full px-4 py-3 rounded-xl border border-[#e5e5e5] focus:outline-none focus:border-black transition-colors text-[16px]';

export default function Login() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const navigate = useNavigate();

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    setError('');

    try {
      const response = await axios.post('/user/login', { username, password });
      localStorage.setItem('token', response.data.accessToken);
      localStorage.setItem('refreshToken', response.data.refreshToken);
      localStorage.setItem('user', JSON.stringify(response.data.user));
      navigate('/');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Login failed. Please check your credentials.');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <AuthCard
      title="Welcome back"
      error={error}
      isLoading={isLoading}
      submitLabel="Continue"
      loadingLabel="Logging in..."
      footerText="Don't have an account?"
      footerLinkLabel="Sign up"
      footerLinkTo="/register"
      onSubmit={handleLogin}
    >
      <input
        type="text"
        placeholder="Username"
        value={username}
        onChange={(e) => setUsername(e.target.value)}
        required
        className={inputClassName}
      />
      <input
        type="password"
        placeholder="Password"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        required
        className={inputClassName}
      />
    </AuthCard>
  );
}

import { useEffect, useState } from 'react';
import api from './services/api';

function App() {
  const [message, setMessage] = useState('');

  useEffect(() => {
    // 백엔드 API 호출 테스트
    api.get('/test')
      .then(response => {
        setMessage(response.data);
      })
      .catch(error => {
        console.error('Error fetching data:', error);
        setMessage('Failed to connect to backend.');
      });
  }, []);

  return (
    <div className="min-h-screen bg-gray-100 flex flex-col items-center justify-center p-4">
      <div className="bg-white rounded-lg shadow-xl p-8 max-w-md w-full text-center">
        <h1 className="text-3xl font-bold text-blue-600 mb-4">LogSystem Frontend</h1>
        <p className="text-gray-700 mb-6">React + Vite + Tailwind CSS</p>
        
        <div className="bg-blue-50 border border-blue-200 rounded p-4">
          <h2 className="text-sm font-semibold text-blue-800 mb-2">Backend Connection Status:</h2>
          <p className="text-lg text-gray-800 font-medium">
            {message ? message : 'Connecting...'}
          </p>
        </div>
      </div>
    </div>
  );
}

export default App;

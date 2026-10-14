import { createRoot } from 'react-dom/client';
import { App } from './App.tsx';
import { SessionProvider } from './session.tsx';
import './styles.css';

createRoot(document.getElementById('root')!).render(<SessionProvider><App /></SessionProvider>);

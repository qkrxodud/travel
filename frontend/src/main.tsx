import { QueryClientProvider } from '@tanstack/react-query';
import { createRoot } from 'react-dom/client';
import { App } from './app/App';
import { createQueryClient } from './app/queryClient';
import { sprites } from './shared/lib/pixel';
import './styles/global.css';

sprites.preload();
const queryClient = createQueryClient();
const root = document.getElementById('root');
if (root) {
  createRoot(root).render(
    <QueryClientProvider client={queryClient}>
      <App />
    </QueryClientProvider>,
  );
}

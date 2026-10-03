import { useToastStore } from '../../store/toastStore';

/** 하단 토스트 묶음 */
export function ToastHost() {
  const toasts = useToastStore(state => state.toasts);
  return (
    <div className="toasts" id="toasts">
      {toasts.map(item => (
        <div className="toast" key={item.id}>
          <div className="ico">{item.icon}</div>
          <div><b>{item.title}</b><small>{item.sub}</small></div>
        </div>
      ))}
    </div>
  );
}

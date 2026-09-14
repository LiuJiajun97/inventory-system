// 数字滚动 hook(TASK-v22b B2):目标值变化时从 0(或上次值)滚动到新值
// 不引第三方依赖:基于 requestAnimationFrame,easeOut(1-(1-t)^3),默认 600ms

import { useEffect, useRef, useState } from "react";

/**
 * 数字滚动计数:返回当前应展示的数字(浮点,展示端自行格式化)
 * target 变化时从上次目标值滚动到新值(首次从 0 开始)
 */
export function useCountUp(target: number, duration = 600): number {
  const [value, setValue] = useState(0);
  // 上次到达的目标值(下次动画起点)
  const fromRef = useRef(0);

  useEffect(() => {
    const from = fromRef.current;
    const diff = target - from;
    if (diff === 0) {
      setValue(target);
      return;
    }
    let raf = 0;
    const start = performance.now();
    const tick = (now: number) => {
      const t = Math.min((now - start) / duration, 1);
      const eased = 1 - Math.pow(1 - t, 3);
      setValue(from + diff * eased);
      if (t < 1) {
        raf = requestAnimationFrame(tick);
      } else {
        fromRef.current = target;
      }
    };
    raf = requestAnimationFrame(tick);
    return () => {
      cancelAnimationFrame(raf);
      fromRef.current = target;
    };
  }, [target, duration]);

  return value;
}
